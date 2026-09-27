package xyz.lukix.keygo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.lukix.keygo.entity.Role;
import xyz.lukix.keygo.entity.SysUser;
import xyz.lukix.keygo.repository.SysUserRepository;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final SysUserRepository sysUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public SysUser register(String username, String password) {
        if (sysUserRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(Role.ROLE_USER);
        user.setApproved(false);
        user.setEnabled(true);
        return sysUserRepository.save(user);
    }

    @Override
    @Transactional
    public void approveUser(Long userId) {
        SysUser u = sysUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        u.setApproved(true);
        sysUserRepository.save(u);
    }

    @Override
    @Transactional
    public void rejectUser(Long userId) {
        SysUser u = sysUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        u.setApproved(false);
        u.setEnabled(false);
        sysUserRepository.save(u);
    }

    @Override
    @Transactional
    public void changePassword(Long targetUserId, String oldPassword, String newPassword, String operatorUsername) {
        SysUser operator = sysUserRepository.findByUsername(operatorUsername)
                .orElseThrow(() -> new IllegalArgumentException("操作者不存在"));

        // 确定目标用户
        SysUser target;
        if (targetUserId == null || targetUserId.equals(operator.getId())) {
            target = operator;
        } else {
            target = sysUserRepository.findById(targetUserId)
                    .orElseThrow(() -> new IllegalArgumentException("目标用户不存在"));
        }

        boolean isSelf = target.getId().equals(operator.getId());

        if (isSelf) {
            // 修改自己的密码：必须验证旧密码 + 二次确认（由前端保证）
            if (oldPassword == null || oldPassword.isBlank()) {
                throw new IllegalArgumentException("请输入当前密码");
            }
            if (!passwordEncoder.matches(oldPassword, target.getPassword())) {
                throw new IllegalArgumentException("当前密码错误");
            }
        } else {
            // 修改他人密码：必须是管理员及以上
            if (operator.getRole() != Role.ROLE_SUPER_ADMIN && operator.getRole() != Role.ROLE_ADMIN) {
                throw new SecurityException("无权修改他人密码");
            }
            // 管理员不能修改超级管理员的密码
            if (operator.getRole() == Role.ROLE_ADMIN && target.getRole() == Role.ROLE_SUPER_ADMIN) {
                throw new SecurityException("无权修改超级管理员的密码");
            }
        }

        if (newPassword == null || newPassword.length() < 4) {
            throw new IllegalArgumentException("新密码至少4个字符");
        }

        target.setPassword(passwordEncoder.encode(newPassword));
        sysUserRepository.save(target);
    }

    @Override
    @Transactional
    public void deleteUser(Long userId, String operatorUsername) {
        SysUser operator = sysUserRepository.findByUsername(operatorUsername)
                .orElseThrow(() -> new IllegalArgumentException("操作者不存在"));
        if (operator.getRole() != Role.ROLE_SUPER_ADMIN) {
            throw new SecurityException("仅超级管理员可删除用户");
        }
        SysUser target = sysUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        if (target.getUsername().equals(operatorUsername)) {
            throw new IllegalArgumentException("不能删除自己");
        }
        sysUserRepository.delete(target);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SysUser> listVisibleUsers(String operatorUsername) {
        SysUser operator = sysUserRepository.findByUsername(operatorUsername)
                .orElseThrow(() -> new IllegalArgumentException("操作者不存在"));
        // 角色等级：ROLE_SUPER_ADMIN(0) > ROLE_ADMIN(1) > ROLE_USER(2)，依赖枚举声明顺序
        // 仅返回同级及以下等级的用户，不暴露更高等级账户
        return sysUserRepository.findAllByOrderByCreateTimeDesc().stream()
                .filter(u -> u.getRole().ordinal() >= operator.getRole().ordinal())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SysUser> listPendingUsers() {
        return sysUserRepository.findByApprovedFalseAndEnabledTrueAndRoleOrderByCreateTimeDesc(Role.ROLE_USER);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SysUser> findByUsername(String username) {
        return sysUserRepository.findByUsername(username);
    }

    @Override
    @Transactional
    public void changeRole(Long targetUserId, Role newRole, String operatorUsername) {
        if (newRole == null) {
            throw new IllegalArgumentException("新角色不能为空");
        }
        SysUser operator = sysUserRepository.findByUsername(operatorUsername)
                .orElseThrow(() -> new IllegalArgumentException("操作者不存在"));
        // 仅超级管理员可修改角色
        if (operator.getRole() != Role.ROLE_SUPER_ADMIN) {
            throw new SecurityException("仅超级管理员可修改用户角色");
        }
        SysUser target = sysUserRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("目标用户不存在"));
        // 不能修改自己的角色
        if (target.getId().equals(operator.getId())) {
            throw new IllegalArgumentException("不能修改自己的角色");
        }
        // 不能修改超级管理员用户的角色
        if (target.getRole() == Role.ROLE_SUPER_ADMIN) {
            throw new SecurityException("不能修改超级管理员的角色");
        }
        target.setRole(newRole);
        sysUserRepository.save(target);
    }
}
