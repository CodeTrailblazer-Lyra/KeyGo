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

    /** 获取操作者可见的用户列表（同级及以下角色，不含更高等级账户） */
    List<SysUser> listVisibleUsers(String operatorUsername);

    /** 获取待审核用户 */
    List<SysUser> listPendingUsers();

    /** 获取当前登录用户信息 */
    Optional<SysUser> findByUsername(String username);

    /**
     * 修改密码
     * @param targetUserId 目标用户ID（null 表示修改自己）
     * @param oldPassword  当前密码（自己修改时必填，管理员改别人时传 null）
     * @param newPassword  新密码
     * @param operatorUsername 操作者用户名
     */
    void changePassword(Long targetUserId, String oldPassword, String newPassword, String operatorUsername);

    /**
     * 修改用户角色（仅超级管理员可用）
     * <p>规则：超级管理员可修改所有非超级管理员用户的角色；不能修改自己的角色；不能修改超级管理员用户的角色。</p>
     *
     * @param targetUserId     目标用户ID
     * @param newRole          新角色
     * @param operatorUsername 操作者用户名
     */
    void changeRole(Long targetUserId, Role newRole, String operatorUsername);
}
