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
        sysUserRepository.findById(userId).ifPresent(u -> {
            u.setApproved(true);
            sysUserRepository.save(u);
        });
    }

    @Override
    @Transactional
    public void rejectUser(Long userId) {
        sysUserRepository.findById(userId).ifPresent(u -> {
            u.setApproved(false);
            u.setEnabled(false);
            sysUserRepository.save(u);
        });
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
    @Transactional
    public SysUser changeRole(Long targetUserId, Role newRole, String operatorUsername) {
        SysUser operator = sysUserRepository.findByUsername(operatorUsername)
                .orElseThrow(() -> new IllegalArgumentException("操作者不存在"));
        SysUser target = sysUserRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("目标用户不存在"));

        if (operator.getRole() == Role.ROLE_SUPER_ADMIN) {
            // SUPER_ADMIN 可以修改任何人
            target.setRole(newRole);
            return sysUserRepository.save(target);
        } else if (operator.getRole() == Role.ROLE_ADMIN) {
            // ADMIN 只能修改 USER 角色
            if (target.getRole() == Role.ROLE_USER) {
                target.setRole(newRole);
                return sysUserRepository.save(target);
            }
            throw new SecurityException("无权修改该用户的角色");
        }
        throw new SecurityException("无权修改用户角色");
    }

    @Override
    @Transactional(readOnly = true)
    public List<SysUser> listAllUsers() {
        return sysUserRepository.findAllByOrderByCreateTimeDesc();
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
    public boolean isSuperAdmin(String username) {
        return sysUserRepository.findByUsername(username)
                .map(u -> u.getRole() == Role.ROLE_SUPER_ADMIN)
                .orElse(false);
    }

    @Override
    public boolean isAdmin(String username) {
        return sysUserRepository.findByUsername(username)
                .map(u -> u.getRole() == Role.ROLE_ADMIN || u.getRole() == Role.ROLE_SUPER_ADMIN)
                .orElse(false);
    }
}
