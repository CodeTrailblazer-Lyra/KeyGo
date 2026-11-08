package xyz.lukix.keygo.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import xyz.lukix.keygo.common.ApiResponse;
import xyz.lukix.keygo.dto.PasswordChangeRequest;
import xyz.lukix.keygo.dto.RoleChangeRequest;
import xyz.lukix.keygo.dto.UserResponse;
import xyz.lukix.keygo.entity.Role;
import xyz.lukix.keygo.entity.SysUser;
import xyz.lukix.keygo.service.UserService;

import java.util.List;

/**
 * 用户管理接口（管理员可用）
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;

    /**
     * 获取当前用户信息
     */
    @GetMapping("/me")
    public ApiResponse<UserResponse> me(Authentication auth) {
        String username = auth.getName();
        SysUser user = userService.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        return ApiResponse.ok(UserResponse.of(username, user.getRole().name(), user.getApproved()));
    }

    /**
     * 获取用户列表（仅返回同级及以下角色，不暴露更高等级账户）
     */
    @GetMapping
    public ApiResponse<List<UserResponse>> listUsers(Authentication auth) {
        List<UserResponse> users = userService.listVisibleUsers(auth.getName())
                .stream()
                .map(this::toUserResponse)
                .toList();
        return ApiResponse.ok(users);
    }

    /**
     * 待审核用户列表
     */
    @GetMapping("/pending")
    public ApiResponse<List<UserResponse>> pendingUsers() {
        List<UserResponse> users = userService.listPendingUsers()
                .stream()
                .map(this::toUserResponse)
                .toList();
        return ApiResponse.ok(users);
    }

    /**
     * 审核通过
     */
    @PatchMapping("/{id}/approve")
    public ApiResponse<Void> approve(@PathVariable Long id) {
        userService.approveUser(id);
        return ApiResponse.ok("审核通过", null);
    }

    /**
     * 拒绝用户
     */
    @PatchMapping("/{id}/reject")
    public ApiResponse<Void> reject(@PathVariable Long id) {
        userService.rejectUser(id);
        return ApiResponse.ok("已拒绝该用户", null);
    }

    /**
     * 超级管理员删除用户
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteUser(@PathVariable Long id, Authentication auth) {
        userService.deleteUser(id, auth.getName());
        return ApiResponse.ok("用户已删除", null);
    }

    /**
     * 修改密码（JSON body）
     */
    @PatchMapping("/password")
    public ApiResponse<Void> changePassword(@RequestBody PasswordChangeRequest body, Authentication auth) {
        userService.changePassword(body.userId(), body.oldPassword(), body.newPassword(), auth.getName());
        return ApiResponse.ok("密码修改成功", null);
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
        userService.changeRole(id, newRole, auth.getName());
        return ApiResponse.ok("角色已更新为 " + newRole.name(), null);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 将用户实体转换为响应 DTO
     */
    private UserResponse toUserResponse(SysUser user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getRole().name(),
                user.getApproved(),
                user.getApproved(),
                user.getCreateTime()
        );
    }
}
