package xyz.lukix.keygo.config;

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
import org.springframework.security.config.annotation.web.configurers.LogoutConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.rememberme.PersistentRememberMeToken;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import xyz.lukix.keygo.service.DbUserDetailsService;

import java.util.Date;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final DbUserDetailsService dbUserDetailsService;
    private final CustomAuthFailureHandler authFailureHandler;
    private final JdbcTemplate jdbcTemplate;

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
                            (rs, _) -> new PersistentRememberMeToken( // 使用下划线前缀忽略未使用的形参
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
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**").permitAll()
                        .requestMatchers("/login", "/register").permitAll()
                        // 激活码申领接口 — 所有认证用户可用
                        .requestMatchers("/admin/code/fetch", "/admin/code/claim-json").authenticated()
                        // 我的获取记录、获取公告 — 所有认证用户可用
                        .requestMatchers("/admin/code/api/my-codes").authenticated()
                        .requestMatchers(HttpMethod.GET, "/admin/code/api/announcements").authenticated()
                        // 激活码管理接口 — 仅管理员可用
                        .requestMatchers("/admin/code/api/**").hasAnyRole("SUPER_ADMIN", "ADMIN")
                        // 用户管理接口 — 仅管理员可用
                        .requestMatchers("/admin/api/users/**").hasAnyRole("SUPER_ADMIN", "ADMIN")
                        .requestMatchers("/admin/api/me").authenticated()
                        // 其他 admin 路径
                        .requestMatchers("/admin/**").authenticated()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .failureHandler(authFailureHandler)
                        .defaultSuccessUrl("/admin/code/fetch", true)
                        .permitAll()
                )
                .logout(LogoutConfigurer::permitAll)
                .rememberMe(remember -> remember
                        .tokenRepository(persistentTokenRepository())
                        .userDetailsService(dbUserDetailsService)
                        .tokenValiditySeconds(14 * 24 * 60 * 60)
                        .key("KeyGoRememberMeKey")
                )
                .csrf(AbstractHttpConfigurer::disable);

        return http.build();
    }
}