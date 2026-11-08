package xyz.lukix.keygo.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import xyz.lukix.keygo.common.ApiResponse;
import xyz.lukix.keygo.common.AppConstants;
import xyz.lukix.keygo.service.RateLimitService;
import xyz.lukix.keygo.service.UserService;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final RateLimitService rateLimitService;

    /**
     * 处理注册请求（IP 维度限流，防止批量注册）
     */
    @PostMapping("/register")
    public ApiResponse<Void> doRegister(@RequestBody Map<String, String> body,
                                        HttpServletRequest request) {
        String clientIp = rateLimitService.resolveClientIp(request);
        if (!rateLimitService.tryAcquire("register:" + clientIp, AppConstants.REGISTER_LIMIT, AppConstants.REGISTER_WINDOW)) {
            return ApiResponse.error(429, "注册过于频繁，请1小时后再试");
        }

        String username = body.getOrDefault("username", "").trim();
        String password = body.getOrDefault("password", "");
        if (username.length() < 2) {
            return ApiResponse.error(400, "用户名至少2个字符");
        }
        if (password.length() < 4) {
            return ApiResponse.error(400, "密码至少4个字符");
        }
        try {
            userService.register(username, password);
            return ApiResponse.ok("注册成功，请等待管理员审核", null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
}
