package com.lolinhouse.domain.set;

import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.Side;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 저장 전 세트 초안(확인 화면에서 수정 중인 값). 검증 전이라 null·빈 값이 들어올 수 있어 값은 검사하지 않는다.
 * 목록은 복사해 두고 수정할 수 없게 막아서, 만든 뒤에는 바뀌지 않는다. 검증은 {@link SetDraftValidator}가 맡는다.
 *
 * @param topSide           위쪽 팀 진영
 * @param capturerPosition  캡처한 사람의 실제 포지션
 * @param topTeam           위쪽 팀(캡처한 사람의 팀)
 * @param bottomTeam        아래쪽 팀
 * @param bans              밴 챔피언 10개(턴 순서 B1 R1 ... B5 R5)
 */
public record SetDraft(Side topSide, Position capturerPosition, DraftTeam topTeam, DraftTeam bottomTeam,
                       List<String> bans) {

    public SetDraft {
        bans = unmodifiableCopy(bans);
    }

    /** 선수 한 명의 초안: 선수 id, 실제 포지션, 픽 챔피언 키, K/D/A, 골드. */
    public record DraftPlayer(String playerId, Position position, String championKey,
                              int kills, int deaths, int assists, int gold) {
    }

    /** 한 팀의 초안: 선수 행들(null 행 허용)과 결과창이 팀 이름 옆에 표시한 K/D/A·골드 합계. */
    public record DraftTeam(List<DraftPlayer> players, int displayedKills, int displayedDeaths,
                            int displayedAssists, int displayedGold) {

        public DraftTeam {
            players = unmodifiableCopy(players);
        }
    }

    /** 복사 후 수정 불가로 만든다. 초안은 null 원소를 허용해야 해서 List.copyOf를 쓰지 않는다. null 목록은 빈 목록. */
    static <T> List<T> unmodifiableCopy(List<T> list) {
        return list == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(list));
    }
}
