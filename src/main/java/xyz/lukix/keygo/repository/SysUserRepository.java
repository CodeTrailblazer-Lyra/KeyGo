package xyz.lukix.keygo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.lukix.keygo.entity.Role;
import xyz.lukix.keygo.entity.SysUser;

import java.util.List;
import java.util.Optional;

public interface SysUserRepository extends JpaRepository<SysUser, Long> {
    Optional<SysUser> findByUsername(String username);

    /** 查询待审核的用户（未批准 + 未禁用 + 非管理员） */
    List<SysUser> findByApprovedFalseAndEnabledTrueAndRoleOrderByCreateTimeDesc(Role role);

    /** 查询所有用户按创建时间倒序 */
    List<SysUser> findAllByOrderByCreateTimeDesc();

    /** 检查用户名是否已存在 */
    boolean existsByUsername(String username);
}
