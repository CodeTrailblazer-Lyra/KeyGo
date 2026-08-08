package xyz.lukix.keygo.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import xyz.lukix.keygo.common.ApiResponse;
import xyz.lukix.keygo.entity.Role;
import xyz.lukix.keygo.entity.SysUser;
import xyz.lukix.keygo.service.UserService;

import java.util.*;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;

    /** 获取当前用户信息 */
    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(Authentication auth) {
        Map<String, Object> result = new HashMap<>();
        result.put("username", auth.getName());
        userService.findByUsername(auth.getName()).ifPresent(u -> {
            result.put("role", u.getRole().name());
            result.put("approved", u.getApproved());
        });
        return ApiResponse.ok(result);
    }

    /** 获取所有用户（管理员） */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listUsers() {
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
        return ApiResponse.ok(list);
    }

    /** 待审核用户列表 */
    @GetMapping("/pending")
    public ApiResponse<List<Map<String, Object>>> pendingUsers() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (SysUser u : userService.listPendingUsers()) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("createTime", u.getCreateTime() != null ? u.getCreateTime().toString() : "");
            list.add(m);
        }
        return ApiResponse.ok(list);
    }

    /** 审核通过 */
    @PatchMapping("/{id}/approve")
    public ApiResponse<Void> approve(@PathVariable Long id) {
        userService.approveUser(id);
        return ApiResponse.ok("审核通过", null);
    }

    /** 拒绝用户 */
    @PatchMapping("/{id}/reject")
    public ApiResponse<Void> reject(@PathVariable Long id) {
        userService.rejectUser(id);
        return ApiResponse.ok("已拒绝该用户", null);
    }

    /** 修改角色（JSON body：{"role":"ROLE_ADMIN"}） */
    @PatchMapping("/{id}/role")
    public ApiResponse<Void> changeRole(@PathVariable Long id,
                                        @RequestBody Map<String, String> body,
                                        Authentication auth) {
        String roleStr = body.get("role");
        if (roleStr == null || roleStr.isBlank()) {
            return ApiResponse.error(400, "缺少角色参数");
        }
        Role role;
        try {
            role = Role.valueOf(roleStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, "非法角色值: " + roleStr);
        }
        try {
            userService.changeRole(id, role, auth.getName());
            return ApiResponse.ok("角色已更新", null);
        } catch (SecurityException | IllegalArgumentException e) {
            return ApiResponse.error(403, e.getMessage());
        }
    }

    /** 超级管理员删除用户 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteUser(@PathVariable Long id, Authentication auth) {
        try {
            userService.deleteUser(id, auth.getName());
            return ApiResponse.ok("用户已删除", null);
        } catch (SecurityException | IllegalArgumentException e) {
            return ApiResponse.error(403, e.getMessage());
        }
    }

    /** 修改密码（JSON body） */
    @PatchMapping("/password")
    public ApiResponse<Void> changePassword(@RequestBody Map<String, Object> body,
                                            Authentication auth) {
        try {
            Long targetUserId = body.get("userId") != null ? ((Number) body.get("userId")).longValue() : null;
            String oldPassword = (String) body.get("oldPassword");
            String newPassword = (String) body.get("newPassword");
            userService.changePassword(targetUserId, oldPassword, newPassword, auth.getName());
            return ApiResponse.ok("密码修改成功", null);
        } catch (SecurityException | IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
}
