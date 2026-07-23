package xyz.lukix.keygo.service;

import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import xyz.lukix.keygo.entity.SysUser;
import xyz.lukix.keygo.repository.SysUserRepository;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DbUserDetailsService implements UserDetailsService {

    private final SysUserRepository sysUserRepository;

    @Override
    public UserDetails loadUserByUsername(@Nullable String username) throws UsernameNotFoundException {
        SysUser user = sysUserRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("账号不存在：" + username));

        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(user.getRole().name()));

        boolean enabled = Boolean.TRUE.equals(user.getEnabled()) && Boolean.TRUE.equals(user.getApproved());

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                enabled,
                true,
                true,
                true,
                authorities
        );
    }
}
