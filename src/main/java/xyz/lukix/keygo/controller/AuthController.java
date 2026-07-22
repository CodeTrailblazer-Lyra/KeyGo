package xyz.lukix.keygo.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import xyz.lukix.keygo.service.UserService;

import java.util.Map;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    /** 注册页面 */
    @GetMapping("/register")
    public String registerPage() {
        return "login"; // 与登录页共用模板，由前端参数控制显示
    }

    /** 处理注册请求 */
    @PostMapping("/register")
    @ResponseBody
    public Map<String, Object> doRegister(@RequestBody Map<String, String> body) {
        String username = body.getOrDefault("username", "").trim();
        String password = body.getOrDefault("password", "");
        if (username.length() < 2) {
            return Map.of("success", false, "message", "用户名至少2个字符");
        }
        if (password.length() < 4) {
            return Map.of("success", false, "message", "密码至少4个字符");
        }
        try {
            userService.register(username, password);
            return Map.of("success", true, "message", "注册成功，请等待管理员审核");
        } catch (IllegalArgumentException e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }
}
