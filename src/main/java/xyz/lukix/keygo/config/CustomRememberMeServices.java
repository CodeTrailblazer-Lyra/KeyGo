package xyz.lukix.keygo.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.rememberme.CookieTheftException;
import org.springframework.security.web.authentication.rememberme.InvalidCookieException;
import org.springframework.security.web.authentication.rememberme.PersistentRememberMeToken;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationException;

import java.util.Arrays;
import java.util.Date;

/**
 * 自定义 Remember-Me 服务。
 * <p>
 * 默认的 PersistentTokenBasedRememberMeServices 每次自动登录都会轮换 token：Session 过期后
 * SPA 页面并发请求同时携带同一 remember-me cookie，第一个请求轮换成功，其余请求携带旧 token
 * 校验失败，触发 CookieTheftException 并删除该用户全部记住我记录，同时清除浏览器 cookie，
 * 表现为“过几小时就要重新登录”。
 * <p>
 * 因此这里重写 processAutoLoginCookie：校验通过后不轮换 token（并发请求全部校验同一数据库值，
 * 天然并发安全），仅刷新 last_used 保持 14 天滚动有效期并重发 cookie 刷新 Max-Age；
 * token 不匹配时也不删除数据库记录（避免并发误杀），由外层 catch 统一降级为“未认证 + 清除 cookie”。
 * 安全性权衡：cookie 被盗后在 14 天有效期内可被复用，真正的盗用由重新登录更换 series 兜底。
 */
public class CustomRememberMeServices extends PersistentTokenBasedRememberMeServices {

    private final PersistentTokenRepository tokenRepository;

    public CustomRememberMeServices(String key, UserDetailsService userDetailsService,
                                    PersistentTokenRepository tokenRepository) {
        super(key, userDetailsService, tokenRepository);
        this.tokenRepository = tokenRepository;
    }

    @Override
    protected UserDetails processAutoLoginCookie(String[] cookieTokens, HttpServletRequest request,
                                                 HttpServletResponse response)
            throws RememberMeAuthenticationException, UsernameNotFoundException {
        try {
            if (cookieTokens.length != 2) {
                throw new InvalidCookieException("Cookie token did not contain 2 tokens but contained '"
                        + Arrays.asList(cookieTokens) + "'");
            }
            final String presentedSeries = cookieTokens[0];
            final String presentedToken = cookieTokens[1];
            PersistentRememberMeToken token = tokenRepository.getTokenForSeries(presentedSeries);
            if (token == null) {
                throw new RememberMeAuthenticationException(
                        "No persistent token found for series id: " + presentedSeries);
            }
            if (!presentedToken.equals(token.getTokenValue())) {
                // 不调用 removeUserTokens：并发请求携带旧 token 时会误删该用户全部记住我记录，
                // 导致 14 天免登录失效。真正的 Cookie 盗用由重新登录更换 series 兜底。
                throw new CookieTheftException("Invalid remember-me token (Series/token) mismatch");
            }
            // 校验通过：不轮换 token（并发安全），仅刷新 last_used 保持 14 天滚动有效期，
            // 并重发 cookie 刷新 Max-Age。
            tokenRepository.updateToken(token.getSeries(), token.getTokenValue(), new Date());
            setCookie(new String[]{token.getSeries(), token.getTokenValue()},
                    getTokenValiditySeconds(), request, response);
            return getUserDetailsService().loadUserByUsername(token.getUsername());
        } catch (RememberMeAuthenticationException e) {
            // 包含 CookieTheftException、series 不存在等场景：按“已过期”温和处理：
            // 清除 cookie，请求以未认证身份继续（返回 null），避免 500。
            logger.warn("记住我 token 校验失败，按已过期处理: " + e.getMessage());
            cancelCookie(request, response);
            throw new UsernameNotFoundException("remember-me token invalid, treated as expired");
        }
    }
}
