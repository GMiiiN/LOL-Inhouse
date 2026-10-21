package com.lolinhouse.tier;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.lolinhouse.common.web.ApiException;
import com.lolinhouse.common.web.TierTeamErrorCodes;
import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.tier.LaneTier;
import com.lolinhouse.domain.tier.ScoreTable;
import com.lolinhouse.domain.tier.TierSub;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 역할: 점수표 조회·수정·기본값 복원. 수정은 score_setting 행을 잠가(프로필 저장과 같은 전역 잠금) 한 번에 하나씩 반영한다.
 */
@Service
public class ScoreSettingService {

    private final ScoreSettingRepository scoreSettings;

    public ScoreSettingService(ScoreSettingRepository scoreSettings) {
        this.scoreSettings = scoreSettings;
    }

    @Transactional(readOnly = true)
    public ScoreTableResponse get() {
        return ScoreTableResponse.from(scoreSettings.findById(ScoreSetting.SINGLE_ID).orElseThrow().toTable());
    }

    @Transactional
    public ScoreTableResponse update(ScoreTableRequest request) {
        ScoreTable table = toTable(request);
        ScoreSetting setting = scoreSettings.findForUpdate().orElseThrow();
        setting.apply(table);
        scoreSettings.saveAndFlush(setting);
        return ScoreTableResponse.from(table);
    }

    @Transactional
    public ScoreTableResponse reset() {
        ScoreSetting setting = scoreSettings.findForUpdate().orElseThrow();
        setting.apply(ScoreTable.DEFAULT);
        scoreSettings.saveAndFlush(setting);
        return ScoreTableResponse.from(ScoreTable.DEFAULT);
    }

    /** 14칸이 모두 있고 정수 -999~999인지 검사해 ScoreTable로 만든다. 아니면 400 INVALID_SCORE_TABLE. */
    private ScoreTable toTable(ScoreTableRequest request) {
        if (request == null || request.base() == null || request.sub() == null || request.lane() == null) {
            throw invalid("base, sub, lane이 모두 필요합니다.");
        }
        List<Integer> base = new ArrayList<>();
        for (int tier = LaneTier.MIN_TIER; tier <= LaneTier.MAX_TIER; tier++) {
            base.add(requireCell(request.base().get(tier), tier + "티어 기본 점수"));
        }
        Map<TierSub, Integer> sub = new EnumMap<>(TierSub.class);
        for (TierSub s : TierSub.values()) {
            sub.put(s, requireCell(request.sub().get(s), s.label() + " 보정"));
        }
        Map<Position, Integer> lane = new EnumMap<>(Position.class);
        for (Position p : Position.values()) {
            lane.put(p, requireCell(request.lane().get(p), p + " 라인 보정"));
        }
        if (request.base().size() != base.size() || request.sub().size() != sub.size()
                || request.lane().size() != lane.size()) {
            throw invalid("알 수 없는 칸이 들어 있습니다.");
        }
        return new ScoreTable(base, sub, lane);
    }

    private int requireCell(Integer value, String label) {
        if (value == null) {
            throw invalid(label + " 값이 없습니다.");
        }
        if (value < ScoreTable.MIN_VALUE || value > ScoreTable.MAX_VALUE) {
            throw invalid(label + "은(는) " + ScoreTable.MIN_VALUE + "~" + ScoreTable.MAX_VALUE + " 정수여야 합니다.");
        }
        return value;
    }

    private ApiException invalid(String detail) {
        return ApiException.badRequest(TierTeamErrorCodes.INVALID_SCORE_TABLE, detail);
    }
}
