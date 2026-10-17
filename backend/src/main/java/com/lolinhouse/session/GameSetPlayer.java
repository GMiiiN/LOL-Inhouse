package com.lolinhouse.session;

import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.Position;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 역할: 한 세트에서 한 선수의 기록(팀·포지션·챔피언·KDA·CS·골드). 세트(GameSet)의 일부라 GameSet을 통해서만 만든다.
 * 선수는 연관관계 없이 playerId로만 가리킨다. 승패는 저장하지 않고 team == GameSet.winnerTeam으로 계산한다.
 */
@Entity
@Table(name = "game_set_player")
public class GameSetPlayer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_set_id", nullable = false)
    private GameSet gameSet;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "team", nullable = false, length = 5)
    private DuelTeam team;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "position", nullable = false, length = 10)
    private Position position;

    /** 결과창에서 팀 안 몇 번째 줄이었는지(1~5). 수동 입력이면 null. */
    @Column(name = "screen_row")
    private Integer screenRow;

    @Column(name = "champion_key", nullable = false, length = 40)
    private String championKey;

    @Column(name = "kills", nullable = false)
    private int kills;

    @Column(name = "deaths", nullable = false)
    private int deaths;

    @Column(name = "assists", nullable = false)
    private int assists;

    @Column(name = "cs", nullable = false)
    private int cs;

    @Column(name = "gold", nullable = false)
    private int gold;

    protected GameSetPlayer() {
    }

    GameSetPlayer(GameSet gameSet, Long playerId, DuelTeam team, Position position, Integer screenRow,
            String championKey, int kills, int deaths, int assists, int cs, int gold) {
        this.gameSet = gameSet;
        this.playerId = playerId;
        this.team = team;
        this.position = position;
        this.screenRow = screenRow;
        this.championKey = championKey;
        this.kills = kills;
        this.deaths = deaths;
        this.assists = assists;
        this.cs = cs;
        this.gold = gold;
    }

    public Long id() {
        return id;
    }

    public Long playerId() {
        return playerId;
    }

    public DuelTeam team() {
        return team;
    }

    public Position position() {
        return position;
    }

    public Integer screenRow() {
        return screenRow;
    }

    public String championKey() {
        return championKey;
    }

    public int kills() {
        return kills;
    }

    public int deaths() {
        return deaths;
    }

    public int assists() {
        return assists;
    }

    public int cs() {
        return cs;
    }

    public int gold() {
        return gold;
    }
}
