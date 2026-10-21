package com.lolinhouse.domain.team;

import java.util.List;

/**
 * 역할: 팀 짜기 계산 결과. 성공이면 방안 1~4개(좌우 교체 전), 실패면 이유 하나.
 */
public record TeamMakeResult(List<TeamOption> options, Failure failure) {

    /** 실패 이유. 자동 = NO_VALID_SPLIT, 수동 = PAIR_UNSATISFIABLE(설계 §1.5-6, §1.6). */
    public enum Failure { NO_VALID_SPLIT, PAIR_UNSATISFIABLE }

    public TeamMakeResult {
        options = options == null ? List.of() : List.copyOf(options);
        if ((failure == null) == options.isEmpty()) {
            throw new IllegalArgumentException("성공이면 방안이 1개 이상, 실패면 방안이 없어야 합니다.");
        }
    }

    public static TeamMakeResult success(List<TeamOption> options) {
        return new TeamMakeResult(options, null);
    }

    public static TeamMakeResult failure(Failure failure) {
        return new TeamMakeResult(List.of(), failure);
    }

    public boolean isSuccess() {
        return failure == null;
    }
}
