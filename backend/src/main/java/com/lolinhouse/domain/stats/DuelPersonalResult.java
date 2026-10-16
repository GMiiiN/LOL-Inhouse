package com.lolinhouse.domain.stats;

/**
 * 역할: 한 대결 안에서 선수가 직접 뛴 세트의 승·패 수와 그로부터 나오는 개인 결과.
 * 승이 더 많으면 WIN, 같으면 DRAW(1승 1패 = 50%), 적으면 LOSS.
 * 설명: docs/study/07-domain-player-stats.md
 */
public record DuelPersonalResult(int wins, int losses) {

    public DuelResult result() {
        return Integer.compare(wins, losses) > 0 ? DuelResult.WIN
                : wins == losses ? DuelResult.DRAW
                : DuelResult.LOSS;
    }

    /** 이 대결 안에서의 세트 승률. */
    public double setWinRate() {
        return wins / (double) (wins + losses);
    }
}
