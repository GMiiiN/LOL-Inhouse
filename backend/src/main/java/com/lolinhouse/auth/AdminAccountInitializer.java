package com.lolinhouse.auth;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 역할: 기동 때 admin_account가 비어 있으면 env 값(app.admin.username/password)으로 BCrypt 계정을 한 개 만든다.
 * 이미 계정이 있으면 env를 검사하지 않고 INFO 로그만 남긴다(운영에서 평문 비밀번호 env 제거 가능).
 * 계정이 없는데 값이 비었거나 비밀번호가 UTF-8 72바이트를 넘으면 기동을 실패시킨다. 오류 메시지·로그에 값은 넣지 않는다.
 */
@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

    /** BCrypt는 입력의 앞 72바이트만 쓴다. 그 이상은 조용히 잘리므로 아예 거절한다. */
    static final int MAX_PASSWORD_BYTES = 72;
    /** admin_account.username 컬럼 길이. */
    static final int MAX_USERNAME_LENGTH = 50;

    private final AdminAccountRepository adminAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;

    public AdminAccountInitializer(AdminAccountRepository adminAccountRepository, PasswordEncoder passwordEncoder,
            @Value("${app.admin.username:}") String username, @Value("${app.admin.password:}") String password) {
        this.adminAccountRepository = adminAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminAccountRepository.count() > 0) {
            log.info("담당자 계정이 이미 있어 초기 계정을 만들지 않습니다.");
            return;
        }
        validate();
        adminAccountRepository.save(AdminAccount.of(username, passwordEncoder.encode(password)));
        log.info("담당자 초기 계정을 만들었습니다.");
    }

    private void validate() {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "담당자 계정이 없고 app.admin.username / app.admin.password(ADMIN_USERNAME / ADMIN_PASSWORD)도 비어 있습니다.");
        }
        if (username.length() > MAX_USERNAME_LENGTH) {
            throw new IllegalStateException("app.admin.username이 " + MAX_USERNAME_LENGTH + "자를 넘습니다.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new IllegalStateException(
                    "app.admin.password가 UTF-8 기준 " + MAX_PASSWORD_BYTES + "바이트를 넘습니다(BCrypt 한계).");
        }
    }
}
