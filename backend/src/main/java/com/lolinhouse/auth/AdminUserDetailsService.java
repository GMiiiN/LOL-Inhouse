package com.lolinhouse.auth;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 역할: 로그인 때 시큐리티가 부르는 사용자 조회. admin_account에서 찾아 ROLE_ADMIN 사용자로 돌려준다.
 * 이 빈이 있어서 Boot의 기본 사용자("Using generated security password")가 만들어지지 않는다.
 */
@Service
public class AdminUserDetailsService implements UserDetailsService {

    private final AdminAccountRepository adminAccountRepository;

    public AdminUserDetailsService(AdminAccountRepository adminAccountRepository) {
        this.adminAccountRepository = adminAccountRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AdminAccount account = adminAccountRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("담당자 계정을 찾을 수 없습니다."));
        return User.withUsername(account.username())
                .password(account.passwordHash())
                .roles("ADMIN")
                .build();
    }
}
