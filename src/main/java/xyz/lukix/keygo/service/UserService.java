package xyz.lukix.keygo.service;

import xyz.lukix.keygo.entity.Role;
import xyz.lukix.keygo.entity.SysUser;

import java.util.List;
import java.util.Optional;

public interface UserService {

    /** 注册新用户（默认 role=USER, approved=false） */
    SysUser register(String username, String password);

    /** 审核通过用户 */
    void approveUser(Long userId);

    /** 拒绝/删除用户 */
    void rejectUser(Long userId);

    /** 超级管理员删除用户 */
    void deleteUser(Long userId, String operatorUsername);

    /** 修改用户角色（仅 SUPER_ADMIN 可操作 ADMIN） */
    SysUser changeRole(Long targetUserId, Role newRole, String operatorUsername);

    /** 获取所有用户 */
    List<SysUser> listAllUsers();

    /** 获取待审核用户 */
    List<SysUser> listPendingUsers();

    /** 获取当前登录用户信息 */
    Optional<SysUser> findByUsername(String username);

    /** 判断是否为 SUPER_ADMIN */
    boolean isSuperAdmin(String username);

    /** 判断是否为 ADMIN 及以上 */
    boolean isAdmin(String username);

    /**
     * 修改密码
     * @param targetUserId 目标用户ID（null 表示修改自己）
     * @param oldPassword  当前密码（自己修改时必填，管理员改别人时传 null）
     * @param newPassword  新密码
     * @param operatorUsername 操作者用户名
     */
    void changePassword(Long targetUserId, String oldPassword, String newPassword, String operatorUsername);
}
