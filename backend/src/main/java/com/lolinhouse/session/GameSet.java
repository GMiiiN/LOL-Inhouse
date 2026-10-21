package com.lolinhouse.session;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.Side;
import com.lolinhouse.domain.set.SetFingerprint;
import com.lolinhouse.domain.set.SetFingerprint.TeamTotals;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 역할: 세트(한 판) 하나와 그 안의 선수 기록 10명·밴 10개를 묶는 집합 루트. 선수·밴은 이 클래스를 통해서만 추가한다.
 * 지문(fingerprint)은 화면 합계 값으로 {@link SetFingerprint}가 계산해 저장하고, DB가 유효 세트끼리 중복을 막는다(ADR 004·006).
 * 삭제는 deletedAt만 채우는 소프트 삭제다. active_* 생성 컬럼은 DB가 계산하므로 매핑하지 않는다.
 */
@Entity
@Table(name = "game_set")
public class GameSet {

    /** 결과창에 보이는 한 팀의 합계(킬·데스·어시스트·골드). 선수 행 합계와 달라도 저장하며, 불일치는 경고로만 다룬다. */
    public record DisplayedTotals(int kills, int deaths, int assists, int gold) {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "duel_id", nullable = false)
    private Long duelId;

    @Column(name = "set_no", nullable = false)
    private int setNo;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "team1_side", nullable = false, length = 4)
    private Side team1Side;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "winner_team", nullable = false, length = 5)
    private DuelTeam winnerTeam;

    /** 결과창 위쪽(캡처한 사람) 팀. 수동 입력이면 null. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "top_team", length = 5)
    private DuelTeam topTeam;

    @Column(name = "shot_date", nullable = false)
    private LocalDate shotDate;

    /** 경기 시간(초). 결과창 없이 가져온 기록은 모르므로 null. */
    @Column(name = "duration_sec")
    private Integer durationSec;

    @Column(name = "team1_displayed_kills", nullable = false)
    private int team1DisplayedKills;

    @Column(name = "team1_displayed_deaths", nullable = false)
    private int team1DisplayedDeaths;

    @Column(name = "team1_displayed_assists", nullable = false)
    private int team1DisplayedAssists;

    @Column(name = "team1_displayed_gold", nullable = false)
    private int team1DisplayedGold;

    @Column(name = "team2_displayed_kills", nullable = false)
    private int team2DisplayedKills;

    @Column(name = "team2_displayed_deaths", nullable = false)
    private int team2DisplayedDeaths;

    @Column(name = "team2_displayed_assists", nullable = false)
    private int team2DisplayedAssists;

    @Column(name = "team2_displayed_gold", nullable = false)
    private int team2DisplayedGold;

    @Column(name = "fingerprint", nullable = false, length = 100)
    private String fingerprint;

    @Column(name = "image_hash", length = 64)
    private String imageHash;

    @Column(name = "image_key")
    private String imageKey;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "gameSet", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<GameSetPlayer> players = new ArrayList<>();

    @OneToMany(mappedBy = "gameSet", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("turn")
    private List<GameSetBan> bans = new ArrayList<>();

    protected GameSet() {
    }

    /**
     * 선수·밴이 없는 세트를 만든다(이어서 addPlayer/addBan). 지문은 날짜·시간·두 팀 킬/골드로 여기서 계산한다(durationSec이 null이면 시간 자리 "?").
     * imageHash와 imageKey는 둘 다 있거나 둘 다 null이어야 한다(DB CHECK와 같은 규칙).
     */
    public static GameSet create(Long duelId, int setNo, Side team1Side, DuelTeam winnerTeam, DuelTeam topTeam,
            LocalDate shotDate, Integer durationSec, DisplayedTotals team1, DisplayedTotals team2, String imageHash,
            String imageKey) {
        if (duelId == null || team1Side == null || winnerTeam == null || shotDate == null || team1 == null
                || team2 == null) {
            throw new IllegalArgumentException("세트의 필수 값이 비어 있습니다.");
        }
        if (setNo < 1) {
            throw new IllegalArgumentException("세트 번호는 1 이상이어야 합니다.");
        }
        if ((imageHash == null) != (imageKey == null)) {
            throw new IllegalArgumentException("이미지 해시와 이미지 키는 함께 있거나 함께 없어야 합니다.");
        }
        GameSet set = new GameSet();
        set.duelId = duelId;
        set.setNo = setNo;
        set.team1Side = team1Side;
        set.winnerTeam = winnerTeam;
        set.topTeam = topTeam;
        set.shotDate = shotDate;
        set.durationSec = durationSec;
        set.team1DisplayedKills = team1.kills();
        set.team1DisplayedDeaths = team1.deaths();
        set.team1DisplayedAssists = team1.assists();
        set.team1DisplayedGold = team1.gold();
        set.team2DisplayedKills = team2.kills();
        set.team2DisplayedDeaths = team2.deaths();
        set.team2DisplayedAssists = team2.assists();
        set.team2DisplayedGold = team2.gold();
        set.imageHash = imageHash;
        set.imageKey = imageKey;
        TeamTotals totals1 = new TeamTotals(team1.kills(), team1.gold());
        TeamTotals totals2 = new TeamTotals(team2.kills(), team2.gold());
        set.fingerprint = (durationSec == null ? SetFingerprint.ofUnknownDuration(shotDate, totals1, totals2)
                : SetFingerprint.of(shotDate, durationSec, totals1, totals2)).canonical();
        return set;
    }

    /** 이 세트에 선수 기록 한 줄을 추가한다. 10명이 맞는지·포지션 중복은 도메인 검증과 DB UNIQUE가 본다. */
    public GameSetPlayer addPlayer(Long playerId, DuelTeam team, Position position, Integer screenRow,
            String championKey, int kills, int deaths, int assists, int cs, int gold) {
        GameSetPlayer player = new GameSetPlayer(this, playerId, team, position, screenRow, championKey, kills,
                deaths, assists, cs, gold);
        players.add(player);
        return player;
    }

    public GameSetBan addBan(int turn, Side side, String championKey) {
        GameSetBan ban = new GameSetBan(this, turn, side, championKey);
        bans.add(ban);
        return ban;
    }

    /** 소프트 삭제. active_* 생성 컬럼이 NULL이 되어 같은 지문·세트 번호로 다시 등록할 수 있다. */
    public void markDeleted() {
        if (deletedAt != null) {
            throw new IllegalStateException("이미 삭제된 세트입니다.");
        }
        deletedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    /** 복구. 같은 지문·세트 번호의 유효 세트가 이미 있으면 flush 때 UNIQUE 위반(오류 1062)이 난다. */
    public void restore() {
        if (deletedAt == null) {
            throw new IllegalStateException("삭제되지 않은 세트입니다.");
        }
        deletedAt = null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public Long id() {
        return id;
    }

    public Long duelId() {
        return duelId;
    }

    public int setNo() {
        return setNo;
    }

    public Side team1Side() {
        return team1Side;
    }

    public DuelTeam winnerTeam() {
        return winnerTeam;
    }

    public DuelTeam topTeam() {
        return topTeam;
    }

    public LocalDate shotDate() {
        return shotDate;
    }

    public Integer durationSec() {
        return durationSec;
    }

    public DisplayedTotals team1Totals() {
        return new DisplayedTotals(team1DisplayedKills, team1DisplayedDeaths, team1DisplayedAssists,
                team1DisplayedGold);
    }

    public DisplayedTotals team2Totals() {
        return new DisplayedTotals(team2DisplayedKills, team2DisplayedDeaths, team2DisplayedAssists,
                team2DisplayedGold);
    }

    public String fingerprint() {
        return fingerprint;
    }

    public String imageHash() {
        return imageHash;
    }

    public String imageKey() {
        return imageKey;
    }

    public Instant deletedAt() {
        return deletedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    /** 팀 → 포지션(TOP~SUPPORT) 순으로 정렬한 읽기 전용 목록. 정렬은 DB가 아니라 Java enum 선언 순서로 한다. */
    public List<GameSetPlayer> players() {
        List<GameSetPlayer> sorted = new ArrayList<>(players);
        sorted.sort(Comparator.comparing(GameSetPlayer::team).thenComparing(GameSetPlayer::position));
        return Collections.unmodifiableList(sorted);
    }

    /** turn 순(B1 R1 ... R5) 읽기 전용 목록. */
    public List<GameSetBan> bans() {
        return Collections.unmodifiableList(bans);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
