package xyz.lukix.keygo.dto;

import java.time.LocalDateTime;

/**
 * 用户信息响应
 *
 * @param id         用户ID
 * @param username   用户名
 * @param role       角色
 * @param approved   是否已审核
 * @param enabled    是否启用
 * @param createTime 创建时间
 */
public record UserResponse(
        Long id,
        String username,
        String role,
        Boolean approved,
        Boolean enabled,
        LocalDateTime createTime
) {
    /**
     * 构建简化的用户信息响应（用于当前登录用户）
     *
     * @param username 用户名
     * @param role     角色
     * @param approved 是否已审核
     * @return 用户信息响应
     */
    public static UserResponse of(String username, String role, Boolean approved) {
        return new UserResponse(null, username, role, approved, null, null);
    }
}
