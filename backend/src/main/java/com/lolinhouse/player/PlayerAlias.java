package com.lolinhouse.player;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.lolinhouse.domain.player.KnownAlias;
import com.lolinhouse.domain.player.NicknameNormalizer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * 역할: 선수가 쓰는 게임 닉네임 하나. nickname은 화면용(대소문자 유지), nicknameKey는 중복 판정용 비교 키이며
 * DB UNIQUE(바이너리 콜레이션)가 같은 키를 두 선수가 갖지 못하게 막는다. 키는 {@link #of}에서만 계산한다.
 */
@Entity
@Table(name = "player_alias")
public class PlayerAlias {

    /** 입력 닉네임 최대 길이(정규화 후, 코드포인트). 컬럼은 NFKC·소문자 확장을 감안해 100자. */
    public static final int MAX_NICKNAME_LENGTH = 50;
    private static final int MAX_COLUMN_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Column(name = "nickname", nullable = false, length = 100)
    private String nickname;

    @Column(name = "nickname_key", nullable = false, length = 100)
    private String nicknameKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PlayerAlias() {
    }

    private PlayerAlias(Long playerId, String nickname, String nicknameKey) {
        this.playerId = playerId;
        this.nickname = nickname;
        this.nicknameKey = nicknameKey;
    }

    /** 입력 닉네임을 정규화해 별칭을 만든다. 정규화 후 비었거나 50자를 넘으면 거절한다. */
    public static PlayerAlias of(Long playerId, String raw) {
        if (playerId == null) {
            throw new IllegalArgumentException("선수 id가 필요합니다.");
        }
        String nickname = raw == null ? "" : NicknameNormalizer.normalize(raw);
        if (nickname.isEmpty() || length(nickname) > MAX_NICKNAME_LENGTH) {
            throw new IllegalArgumentException("닉네임은 1~" + MAX_NICKNAME_LENGTH + "자여야 합니다.");
        }
        String key = NicknameNormalizer.comparisonKey(nickname);
        if (length(key) > MAX_COLUMN_LENGTH) {
            throw new IllegalArgumentException("닉네임 비교 키가 너무 깁니다.");
        }
        return new PlayerAlias(playerId, nickname, key);
    }

    public Long id() {
        return id;
    }

    public Long playerId() {
        return playerId;
    }

    public String nickname() {
        return nickname;
    }

    public String nicknameKey() {
        return nicknameKey;
    }

    public Instant createdAt() {
        return createdAt;
    }

    /** 닉네임 매처(M2)의 입력 형태로 바꾼다. 별칭은 선수의 등록 여부를 모르므로 호출자가 알려 준다. */
    public KnownAlias toKnownAlias(boolean registered) {
        return new KnownAlias(String.valueOf(playerId), nickname, registered);
    }

    private static int length(String text) {
        return text.codePointCount(0, text.length());
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
