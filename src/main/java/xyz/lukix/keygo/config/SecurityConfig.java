package xyz.lukix.keygo.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.LogoutConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import xyz.lukix.keygo.service.DbUserDetailsService;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final DbUserDetailsService dbUserDetailsService;
    private final CustomAuthFailureHandler authFailureHandler;

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
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**").permitAll()
                        .requestMatchers("/login", "/register").permitAll()
                        // 激活码申领接口 — 所有认证用户可用
                        .requestMatchers("/admin/code/fetch", "/admin/code/claim-json").authenticated()
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
                        .tokenValiditySeconds(7 * 24 * 60 * 60)
                        .key("KeyGoRememberMeKey")
                )
                .csrf(AbstractHttpConfigurer::disable);

        return http.build();
    }
}
