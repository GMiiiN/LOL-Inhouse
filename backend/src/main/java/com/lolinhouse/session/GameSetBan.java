package com.lolinhouse.session;

import com.lolinhouse.domain.Side;
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
 * 역할: 한 세트의 밴 한 칸. turn 1~10 = B1 R1 B2 R2 ... B5 R5 (홀수=블루, 짝수=레드. DB CHECK가 turn과 side의 짝을 검증).
 * 세트(GameSet)의 일부라 GameSet.addBan으로만 만든다.
 */
@Entity
@Table(name = "game_set_ban")
public class GameSetBan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_set_id", nullable = false)
    private GameSet gameSet;

    @Column(name = "turn", nullable = false)
    private int turn;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "side", nullable = false, length = 4)
    private Side side;

    @Column(name = "champion_key", nullable = false, length = 40)
    private String championKey;

    protected GameSetBan() {
    }

    GameSetBan(GameSet gameSet, int turn, Side side, String championKey) {
        this.gameSet = gameSet;
        this.turn = turn;
        this.side = side;
        this.championKey = championKey;
    }

    public Long id() {
        return id;
    }

    public int turn() {
        return turn;
    }

    public Side side() {
        return side;
    }

    public String championKey() {
        return championKey;
    }
}
