package com.lolinhouse.auth;

import java.util.List;

import com.lolinhouse.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 역할: 계정 초기화 규칙 검증. 빈 테이블에만 만들고, 멱등이며, 이미 있으면 env 없이도 통과하고,
 * 빈 값·UTF-8 72바이트 초과는 실패하며 오류 메시지에 값이 새지 않는다.
 * 테스트마다 트랜잭션을 열고 끝나면 롤백하므로(@Transactional) 다른 통합 테스트가 쓰는 테스트 계정은 그대로 남는다.
 */
@IntegrationTest
@Transactional
class AdminAccountInitializerTest {

    @Autowired
    AdminAccountRepository repository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void emptyTable() {
        jdbcTemplate.update("DELETE FROM admin_account");
    }

    private AdminAccountInitializer initializer(String username, String password) {
        return new AdminAccountInitializer(repository, passwordEncoder, username, password);
    }

    @Test
    void createsOneBCryptAccountInEmptyTable() {
        initializer("owner", "pw-for-test").run(null);

        List<AdminAccount> accounts = repository.findAll();
        assertThat(accounts).hasSize(1);
        assertThat(accounts.get(0).username()).isEqualTo("owner");
        assertThat(accounts.get(0).passwordHash()).isNotEqualTo("pw-for-test").startsWith("$2");
        assertThat(passwordEncoder.matches("pw-for-test", accounts.get(0).passwordHash())).isTrue();
    }

    @Test
    void runningTwiceKeepsExactlyOneAccount() {
        AdminAccountInitializer initializer = initializer("owner", "pw-for-test");
        initializer.run(null);
        initializer.run(null);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void existingAccountPassesEvenWithoutEnvValuesAndIsNotChanged() {
        initializer("owner", "pw-for-test").run(null);
        String hash = repository.findByUsername("owner").orElseThrow().passwordHash();

        initializer("", "").run(null);
        initializer("other", "other-pw").run(null);

        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findByUsername("owner").orElseThrow().passwordHash()).isEqualTo(hash);
    }

    @Test
    void shortPasswordIsAllowedBecauseThereIsNoLengthMinimum() {
        initializer("owner", "a").run(null);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void passwordOfExactly72BytesIsAllowed() {
        initializer("owner", "a".repeat(72)).run(null);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void blankOrMissingValuesFailWithoutCreatingAnything() {
        assertThatThrownBy(() -> initializer("", "pw").run(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> initializer("owner", "").run(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> initializer("  ", "pw").run(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> initializer("owner", "   ").run(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> initializer(null, null).run(null)).isInstanceOf(IllegalStateException.class);
        assertThat(repository.count()).isZero();
    }

    @Test
    void passwordOver72BytesFailsEvenIfCharacterCountIsSmall() {
        // "가"는 UTF-8로 3바이트: 25자 = 75바이트 > 72, 하지만 글자 수는 25.
        String multibyte = "가".repeat(25);
        assertThatThrownBy(() -> initializer("owner", multibyte).run(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> initializer("owner", "a".repeat(73)).run(null)).isInstanceOf(IllegalStateException.class);
        // 24자 = 72바이트는 통과
        initializer("owner", "가".repeat(24)).run(null);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void usernameOver50CharactersFails() {
        assertThatThrownBy(() -> initializer("u".repeat(51), "pw").run(null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void errorMessagesNeverContainTheConfiguredValues() {
        String secretPassword = "가".repeat(25) + "-SECRET-PW";
        String secretUsername = "u".repeat(51) + "-SECRET-NAME";

        Throwable overlongPassword = catchRun(initializer("owner-name", secretPassword));
        Throwable overlongUsername = catchRun(initializer(secretUsername, "pw"));
        Throwable blank = catchRun(initializer("owner-name", ""));

        for (Throwable error : List.of(overlongPassword, overlongUsername, blank)) {
            assertThat(error.getMessage()).doesNotContain("SECRET").doesNotContain("owner-name");
        }
    }

    private Throwable catchRun(AdminAccountInitializer initializer) {
        try {
            initializer.run(null);
        } catch (RuntimeException e) {
            return e;
        }
        throw new AssertionError("예외가 나야 합니다.");
    }
}
