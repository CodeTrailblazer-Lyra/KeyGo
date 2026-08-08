package xyz.lukix.keygo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.rememberme.PersistentRememberMeToken;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import xyz.lukix.keygo.common.ApiResponse;
import xyz.lukix.keygo.service.DbUserDetailsService;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final DbUserDetailsService dbUserDetailsService;
    private final CustomAuthFailureHandler authFailureHandler;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(dbUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public PersistentTokenRepository persistentTokenRepository() {
        return new PersistentTokenRepository() {
            @Override
            public void createNewToken(@NonNull PersistentRememberMeToken token) {
                jdbcTemplate.update(
                        "INSERT INTO persistent_logins (username, series, token, last_used) VALUES (?, ?, ?, ?)",
                        token.getUsername(), token.getSeries(), token.getTokenValue(), token.getDate()
                );
            }

            @Override
            public void updateToken(@NonNull String series, @NonNull String tokenValue, @NonNull Date lastUsed) {
                jdbcTemplate.update(
                        "UPDATE persistent_logins SET token = ?, last_used = ? WHERE series = ?",
                        tokenValue, lastUsed, series
                );
            }

            @Override
            public PersistentRememberMeToken getTokenForSeries(@NonNull String seriesId) {
                try {
                    return jdbcTemplate.queryForObject(
                            "SELECT username, series, token, last_used FROM persistent_logins WHERE series = ?",
                            (rs, unused) -> new PersistentRememberMeToken(
                                    rs.getString("username"),
                                    rs.getString("series"),
                                    rs.getString("token"),
                                    rs.getTimestamp("last_used")
                            ),
                            seriesId
                    );
                } catch (EmptyResultDataAccessException e) {
                    return null;
                }
            }

            @Override
            public void removeUserTokens(@NonNull String username) {
                jdbcTemplate.update("DELETE FROM persistent_logins WHERE username = ?", username);
            }
        };
    }

    @Bean
    public CustomRememberMeServices rememberMeServices() {
        CustomRememberMeServices services = new CustomRememberMeServices(
                "KeyGoRememberMeKey", dbUserDetailsService, persistentTokenRepository());
        services.setTokenValiditySeconds(14 * 24 * 60 * 60);
        return services;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173", "https://118.178.104.187"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public AuthenticationSuccessHandler jsonAuthSuccessHandler() {
        return (request, response, authentication) -> writeJson(response, ApiResponse.ok("登录成功", Map.of(
                "username", authentication.getName(),
                "role", authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .filter(a -> a.startsWith("ROLE_"))
                        .findFirst()
                        .orElse("ROLE_USER"))));
    }

    private void writeJson(HttpServletResponse response, Object data) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(data));
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**", "/favicon.ico").permitAll()
                        .requestMatchers("/api/v1/auth/login", "/api/v1/auth/register").permitAll()
                        // 认证用户可用
                        .requestMatchers("/api/v1/auth/logout").authenticated()
                        .requestMatchers("/api/v1/codes/claim", "/api/v1/codes/batch-claim", "/api/v1/codes/my-codes").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/codes/stats").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/announcements").authenticated()
                        .requestMatchers("/api/v1/admin/users/me", "/api/v1/admin/users/password").authenticated()
                        // 仅管理员可用
                        .requestMatchers("/api/v1/admin/users/**").hasAnyRole("SUPER_ADMIN", "ADMIN")
                        .requestMatchers("/api/v1/admin/codes/**").hasAnyRole("SUPER_ADMIN", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/announcements").hasAnyRole("SUPER_ADMIN", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/announcements/*").hasAnyRole("SUPER_ADMIN", "ADMIN")
                        // SPA 静态资源与其他
                        .anyRequest().permitAll()
                )
                .formLogin(form -> form
                        .loginProcessingUrl("/api/v1/auth/login")
                        .successHandler(jsonAuthSuccessHandler())
                        .failureHandler(authFailureHandler)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/api/v1/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) ->
                                writeJson(response, ApiResponse.ok("已退出登录", null)))
                        .permitAll()
                )
                .rememberMe(remember -> remember
                        .rememberMeServices(rememberMeServices())
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            writeJson(response, ApiResponse.error(401, "未登录或登录已过期"));
                        })
                );

        return http.build();
    }
}
