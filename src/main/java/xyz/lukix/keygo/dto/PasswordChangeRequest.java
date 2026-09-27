package xyz.lukix.keygo.dto;

/**
 * 修改密码请求体（userId 为空或等于操作者自身时为修改自己密码）
 */
public record PasswordChangeRequest(Long userId, String oldPassword, String newPassword) {
}
