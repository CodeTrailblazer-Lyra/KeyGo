package xyz.lukix.keygo.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import xyz.lukix.keygo.common.ApiResponse;
import xyz.lukix.keygo.dto.PasswordChangeRequest;
import xyz.lukix.keygo.dto.RoleChangeRequest;
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

    /** 获取用户列表（仅返回同级及以下角色，不暴露更高等级账户） */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listUsers(Authentication auth) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (SysUser u : userService.listVisibleUsers(auth.getName())) {
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
        try {
            userService.approveUser(id);
            return ApiResponse.ok("审核通过", null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    /** 拒绝用户 */
    @PatchMapping("/{id}/reject")
    public ApiResponse<Void> reject(@PathVariable Long id) {
        try {
            userService.rejectUser(id);
            return ApiResponse.ok("已拒绝该用户", null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    /** 超级管理员删除用户 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteUser(@PathVariable Long id, Authentication auth) {
        try {
            userService.deleteUser(id, auth.getName());
            return ApiResponse.ok("用户已删除", null);
        } catch (SecurityException e) {
            return ApiResponse.error(403, e.getMessage());
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /** 修改密码（JSON body） */
    @PatchMapping("/password")
    public ApiResponse<Void> changePassword(@RequestBody PasswordChangeRequest body,
                                            Authentication auth) {
        try {
            userService.changePassword(body.userId(), body.oldPassword(), body.newPassword(), auth.getName());
            return ApiResponse.ok("密码修改成功", null);
        } catch (SecurityException e) {
            return ApiResponse.error(403, e.getMessage());
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 修改用户角色（仅超级管理员）
     * 规则：可修改所有非超级管理员用户的角色；不能修改自己；不能修改超级管理员用户
     */
    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Void> changeRole(@PathVariable Long id,
                                        @RequestBody RoleChangeRequest body,
                                        Authentication auth) {
        String roleStr = body.role() != null ? body.role() : "";
        Role newRole;
        try {
            newRole = Role.valueOf(roleStr);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, "无效的角色: " + roleStr);
        }
        try {
            userService.changeRole(id, newRole, auth.getName());
            return ApiResponse.ok("角色已更新为 " + newRole.name(), null);
        } catch (SecurityException e) {
            return ApiResponse.error(403, e.getMessage());
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
}
