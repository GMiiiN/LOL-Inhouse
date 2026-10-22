package com.lolinhouse.registration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.lolinhouse.common.web.ApiException;

/**
 * 역할: Bean Validation으로 표현하기 어려운 요청 "형태" 규칙(팀 안 screenRow 중복, resolution과 playerId·addAlias의 짝)을 검사해
 * 400 VALIDATION_FAILED(+errors[{field, message}])로 돌려준다. 내용 규칙(빈 챔피언, 중복, 피어리스 등)은 SetEvaluator가 422로 다룬다.
 */
public final class RegistrationShape {

    private RegistrationShape() {
    }

    public static void validate(SetRegistrationRequest request) {
        List<Map<String, String>> errors = new ArrayList<>();
        checkTeam("top", request.top(), errors);
        checkTeam("bottom", request.bottom(), errors);
        if (!errors.isEmpty()) {
            throw ApiException.badRequest("VALIDATION_FAILED", "요청 값이 올바르지 않습니다.").with("errors", errors);
        }
    }

    private static void checkTeam(String team, TeamInput input, List<Map<String, String>> errors) {
        Set<Integer> screenRows = new HashSet<>();
        for (int i = 0; i < input.rows().size(); i++) {
            RowInput row = input.rows().get(i);
            String field = team + ".rows[" + i + "]";
            if (!screenRows.add(row.screenRow())) {
                errors.add(error(field + ".screenRow", "팀 안에서 줄 번호가 겹칩니다."));
            }
            if (row.resolution() == RowResolution.EXISTING && row.playerId() == null) {
                errors.add(error(field + ".playerId", "기존 선수로 확정하려면 playerId가 필요합니다."));
            }
            if (row.resolution() == RowResolution.NEW && row.playerId() != null) {
                errors.add(error(field + ".playerId", "새 선수는 playerId 없이 보내야 합니다."));
            }
            if (row.addAlias() && (row.resolution() != RowResolution.EXISTING || row.playerId() == null)) {
                errors.add(error(field + ".addAlias", "별칭 추가는 기존 선수를 고른 행에서만 할 수 있습니다."));
            }
        }
    }

    private static Map<String, String> error(String field, String message) {
        return Map.of("field", field, "message", message);
    }
}
