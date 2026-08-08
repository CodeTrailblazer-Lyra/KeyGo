package xyz.lukix.keygo.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationException;

/**
 * 自定义 Remember-Me 服务。
 * <p>
 * 将 remember-me cookie 校验失败（CookieTheftException、series 不存在等）降级为
 * “未认证 + 清除 cookie”，而不是把异常抛到 DispatcherServlet 造成 500。
 * <p>
 * 背景：页面并发请求时，多个请求同时携带同一 remember-me cookie，第一个请求自动登录成功后
 * 会轮换 token（updateToken），后续并发请求携带旧 token 校验失败，会被误判为 “Cookie 被盗”
 * （CookieTheftException）。该异常在 autoLogin 阶段抛出，RememberMeAuthenticationFilter
 * 无法捕获，会直接导致请求 500，并误删用户所有记住我记录。
 */
public class CustomRememberMeServices extends PersistentTokenBasedRememberMeServices {

    public CustomRememberMeServices(String key, UserDetailsService userDetailsService,
                                    PersistentTokenRepository tokenRepository) {
        super(key, userDetailsService, tokenRepository);
    }

    @Override
    protected UserDetails processAutoLoginCookie(String[] cookieTokens, HttpServletRequest request,
                                                 HttpServletResponse response)
            throws RememberMeAuthenticationException, UsernameNotFoundException {
        try {
            return super.processAutoLoginCookie(cookieTokens, request, response);
        } catch (RememberMeAuthenticationException e) {
            // 包含 CookieTheftException：并发请求导致 token 轮换、cookie 残留等场景。
            // 按“已过期”温和处理：清除 cookie，请求以未认证身份继续（返回 null），避免 500。
            logger.warn("记住我 token 校验失败，按已过期处理: " + e.getMessage());
            cancelCookie(request, response);
            throw new UsernameNotFoundException("remember-me token invalid, treated as expired");
        }
    }
}
