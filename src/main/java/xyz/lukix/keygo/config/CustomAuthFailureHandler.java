package xyz.lukix.keygo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import xyz.lukix.keygo.common.ApiResponse;
import xyz.lukix.keygo.entity.SysUser;
import xyz.lukix.keygo.repository.SysUserRepository;
import xyz.lukix.keygo.service.RateLimitService;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CustomAuthFailureHandler implements AuthenticationFailureHandler {

    /** HTTP 429（Servlet API 未提供对应常量） */
    private static final int SC_TOO_MANY_REQUESTS = 429;

    /** 登录失败限流：每 IP 15 分钟内最多 30 次失败（撞库防护，成功登录不计入） */
    private static final int LOGIN_FAIL_LIMIT = 30;
    private static final Duration LOGIN_FAIL_WINDOW = Duration.ofMinutes(15);

    private final SysUserRepository sysUserRepository;
    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    @Override
    public void onAuthenticationFailure(@Nullable HttpServletRequest request,
                                        @Nullable HttpServletResponse response,
                                        @Nullable AuthenticationException exception) throws IOException {
        if (response == null) {
            return;
        }
        // 仅失败请求计数：同一 IP 连续失败超限后直接提示稍后再试
        if (request != null
                && !rateLimitService.tryAcquire("login:" + rateLimitService.resolveClientIp(request),
                        LOGIN_FAIL_LIMIT, LOGIN_FAIL_WINDOW)) {
            response.setStatus(SC_TOO_MANY_REQUESTS);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(
                    ApiResponse.error(429, "失败次数过多，请15分钟后再试")));
            return;
        }
        String username = request != null ? request.getParameter("username") : null;
        String errorMsg;

        if (exception instanceof DisabledException) {
            Optional<SysUser> userOpt = sysUserRepository.findByUsername(username);
            if (userOpt.isPresent()) {
                SysUser user = userOpt.get();
                if (!user.getApproved()) {
                    if (Boolean.TRUE.equals(user.getEnabled())) {
                        errorMsg = "账号还在审核中，请等待管理员审核通过";
                    } else {
                        errorMsg = "审核未通过，请联系管理员";
                    }
                } else {
                    errorMsg = "账号已被禁用";
                }
            } else {
                errorMsg = "用户名或密码错误";
            }
        } else if (exception instanceof BadCredentialsException) {
            errorMsg = "用户名或密码错误";
        } else {
            errorMsg = "登录失败，请重试";
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                objectMapper.writeValueAsString(ApiResponse.error(401, errorMsg))
        );
    }
}
