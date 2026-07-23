package xyz.lukix.keygo.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import xyz.lukix.keygo.entity.Role;
import xyz.lukix.keygo.entity.SysUser;
import xyz.lukix.keygo.service.UserService;

import java.util.*;

@RestController
@RequestMapping("/admin/api")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;

    /** 获取当前用户信息 */
    @GetMapping("/me")
    public Map<String, Object> me(Authentication auth) {
        Map<String, Object> result = new HashMap<>();
        result.put("username", auth.getName());
        userService.findByUsername(auth.getName()).ifPresent(u -> {
            result.put("role", u.getRole().name());
            result.put("approved", u.getApproved());
        });
        return result;
    }

    /** 获取所有用户（管理员） */
    @GetMapping("/users")
    public List<Map<String, Object>> listUsers(Authentication auth) {
        if (!userService.isAdmin(auth.getName())) {
            return List.of();
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (SysUser u : userService.listAllUsers()) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("role", u.getRole().name());
            m.put("approved", u.getApproved());
            m.put("enabled", u.getEnabled());
            m.put("createTime", u.getCreateTime() != null ? u.getCreateTime().toString() : "");
            list.add(m);
        }
        return list;
    }

    /** 待审核用户列表 */
    @GetMapping("/users/pending")
    public List<Map<String, Object>> pendingUsers(Authentication auth) {
        if (!userService.isAdmin(auth.getName())) {
            return List.of();
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (SysUser u : userService.listPendingUsers()) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("createTime", u.getCreateTime() != null ? u.getCreateTime().toString() : "");
            list.add(m);
        }
        return list;
    }

    /** 审核通过 */
    @PostMapping("/users/{id}/approve")
    public Map<String, Object> approve(@PathVariable Long id, Authentication auth) {
        if (!userService.isAdmin(auth.getName())) {
            return Map.of("success", false, "message", "无权限");
        }
        userService.approveUser(id);
        return Map.of("success", true);
    }

    /** 拒绝用户 */
    @PostMapping("/users/{id}/reject")
    public Map<String, Object> reject(@PathVariable Long id, Authentication auth) {
        if (!userService.isAdmin(auth.getName())) {
            return Map.of("success", false, "message", "无权限");
        }
        userService.rejectUser(id);
        return Map.of("success", true);
    }

    /** 修改角色 */
    @PostMapping("/users/{id}/role")
    public Map<String, Object> changeRole(@PathVariable Long id,
                                           @RequestParam Role role,
                                           Authentication auth) {
        try {
            userService.changeRole(id, role, auth.getName());
            return Map.of("success", true);
        } catch (SecurityException | IllegalArgumentException e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    /** 超级管理员删除用户 */
    @DeleteMapping("/users/{id}")
    public Map<String, Object> deleteUser(@PathVariable Long id, Authentication auth) {
        try {
            userService.deleteUser(id, auth.getName());
            return Map.of("success", true);
        } catch (SecurityException | IllegalArgumentException e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    /** 修改密码 */
    @PostMapping("/password")
    public Map<String, Object> changePassword(@RequestBody Map<String, Object> body,
                                               Authentication auth) {
        try {
            Long targetUserId = body.get("userId") != null ? ((Number) body.get("userId")).longValue() : null;
            String oldPassword = (String) body.get("oldPassword");
            String newPassword = (String) body.get("newPassword");
            userService.changePassword(targetUserId, oldPassword, newPassword, auth.getName());
            return Map.of("success", true, "message", "密码修改成功");
        } catch (SecurityException | IllegalArgumentException e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }
}
