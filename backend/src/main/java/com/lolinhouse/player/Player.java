package com.lolinhouse.player;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.player.NicknameNormalizer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 역할: 선수 한 명. 등록 여부와 표시 이름은 컬럼으로 저장하지 않고 realName 유무로 계산한다(같은 사실을 두 곳에 두지 않는다).
 * gameNickname = 대표 닉네임(미등록이면 표시 이름). 실제 매칭에 쓰는 별칭은 {@link PlayerAlias}.
 */
@Entity
@Table(name = "player")
public class Player {

    private static final int MAX_GAME_NICKNAME_LENGTH = 100;
    private static final int MAX_REAL_NAME_LENGTH = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_nickname", nullable = false, length = 100)
    private String gameNickname;

    @Column(name = "real_name", length = 50)
    private String realName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "main_position", length = 10)
    private Position mainPosition;

    @Column(name = "flagged", nullable = false)
    private boolean flagged;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Player() {
    }

    private Player(String gameNickname, String realName, Position mainPosition) {
        this.gameNickname = requireGameNickname(gameNickname);
        this.realName = realName;
        this.mainPosition = mainPosition;
    }

    /** 실명을 알고 등록한 선수. 대표 닉네임은 보통 첫 별칭. mainPosition은 몰라도 된다(null). */
    public static Player registered(String gameNickname, String realName, Position mainPosition) {
        return new Player(gameNickname, requireRealName(realName), mainPosition);
    }

    /** 결과창에서 처음 본 미등록 선수. 표시 이름은 닉네임이다. */
    public static Player unregistered(String gameNickname) {
        return new Player(gameNickname, null, null);
    }

    public Long id() {
        return id;
    }

    public String gameNickname() {
        return gameNickname;
    }

    /** 미등록이면 null. */
    public String realName() {
        return realName;
    }

    public Position mainPosition() {
        return mainPosition;
    }

    /** 요주 인물 표시. 팀 짜기 계산에는 영향이 없다. */
    public boolean isFlagged() {
        return flagged;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public boolean isRegistered() {
        return realName != null;
    }

    /** 화면에 보이는 이름: 등록 선수는 실명, 미등록은 대표 닉네임. */
    public String displayName() {
        return isRegistered() ? realName : gameNickname;
    }

    /** 미등록 선수를 등록하거나 실명을 고친다. 실명을 지우는 기능은 없다(등록 취소 없음). */
    public void changeRealName(String realName) {
        this.realName = requireRealName(realName);
    }

    public void changeFlagged(boolean flagged) {
        this.flagged = flagged;
    }

    public void changeMainPosition(Position mainPosition) {
        this.mainPosition = mainPosition;
    }

    /** 대표 닉네임 별칭이 삭제되면 서비스가 남은 가장 오래된 별칭으로 바꾼다(설계 §9-2). */
    public void changeGameNickname(String gameNickname) {
        this.gameNickname = requireGameNickname(gameNickname);
    }

    private static String requireGameNickname(String gameNickname) {
        String normalized = gameNickname == null ? "" : NicknameNormalizer.normalize(gameNickname);
        if (normalized.isEmpty() || normalized.codePointCount(0, normalized.length()) > MAX_GAME_NICKNAME_LENGTH) {
            throw new IllegalArgumentException("대표 닉네임은 1~" + MAX_GAME_NICKNAME_LENGTH + "자여야 합니다.");
        }
        return normalized;
    }

    private static String requireRealName(String realName) {
        String stripped = realName == null ? "" : realName.strip();
        if (stripped.isEmpty() || stripped.codePointCount(0, stripped.length()) > MAX_REAL_NAME_LENGTH) {
            throw new IllegalArgumentException("실명은 1~" + MAX_REAL_NAME_LENGTH + "자여야 합니다.");
        }
        return stripped;
    }

    @PrePersist
    void onCreate() {
        // DATETIME(6)은 마이크로초까지만 저장한다. 나노초를 남기면 저장 후 재조회한 값이 달라진다.
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
