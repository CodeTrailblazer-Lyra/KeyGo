package xyz.lukix.keygo.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import xyz.lukix.keygo.entity.SysUser;
import xyz.lukix.keygo.repository.SysUserRepository;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CustomAuthFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final SysUserRepository sysUserRepository;

    @Override
    public void onAuthenticationFailure(@Nullable HttpServletRequest request, @Nullable HttpServletResponse response,
                                        @Nullable AuthenticationException exception) throws IOException {
        String username = request != null ? request.getParameter("username") : null;
        String errorMsg;

        if (exception instanceof DisabledException) {
            // 用户存在但被禁用，进一步判断原因
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

        getRedirectStrategy().sendRedirect(request, response,
                "/login?error=" + URLEncoder.encode(errorMsg, StandardCharsets.UTF_8));
    }
}
