package com.lolinhouse.registration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.lolinhouse.common.web.ApiException;
import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.Side;
import com.lolinhouse.ingest.extract.ExtractedScoreboard.TopResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 역할: 결과창 이미지 없이 세트를 가져오는 명령 본문(ADR 014). 선수는 이미 확정된 playerId로 받고,
 * 팀 합계는 선수 행 합으로 채우며, durationSec이 null이면 경기 시간을 모른다.
 * 진영(BLUE/RED) 기준이라, 평가기에는 블루를 위쪽 팀으로 둔 등록 요청으로 바꿔 넘긴다.
 */
public record SetImportRequest(
        @NotNull String shotDate,
        @Min(1) Integer durationSec,
        @NotNull Side winnerSide,
        @NotNull Map<Side, List<@NotBlank @Size(max = 40) String>> bans,
        @NotNull @Size(min = 10, max = 10) List<@NotNull @Valid ImportPlayer> players) {

    /** 한 선수의 기록. side는 그 선수가 속한 진영. */
    public record ImportPlayer(
            @NotNull Long playerId,
            @NotNull Side side,
            @NotNull Position position,
            @NotBlank @Size(max = 40) String championKey,
            @NotNull @Min(0) Integer kills,
            @NotNull @Min(0) Integer deaths,
            @NotNull @Min(0) Integer assists,
            @NotNull @Min(0) Integer cs,
            @NotNull @Min(0) Integer gold) {
    }

    /** 형태(진영별 선수 5명·밴 5개)가 틀리면 400, 맞으면 평가기가 받는 등록 요청으로 바꾼다. */
    SetRegistrationRequest toRegistration() {
        TeamInput blue = team(Side.BLUE, bans.get(Side.BLUE));
        TeamInput red = team(Side.RED, bans.get(Side.RED));
        return new SetRegistrationRequest(null, Side.BLUE, Position.TOP,
                winnerSide == Side.BLUE ? TopResult.WIN : TopResult.LOSS, shotDate, "", blue, red, null);
    }

    private TeamInput team(Side side, List<String> teamBans) {
        List<ImportPlayer> members = players.stream().filter(player -> player.side() == side).toList();
        if (members.size() != 5 || teamBans == null || teamBans.size() != 5) {
            throw ApiException.badRequest("VALIDATION_FAILED", "진영마다 선수 5명과 밴 5개가 필요합니다.");
        }
        List<RowInput> rows = new ArrayList<>();
        int kills = 0;
        int deaths = 0;
        int assists = 0;
        int gold = 0;
        for (ImportPlayer player : members) {
            rows.add(new RowInput(rows.size() + 1, player.position(), "import", RowResolution.EXISTING,
                    player.playerId(), false, player.championKey(), player.kills(), player.deaths(), player.assists(),
                    player.cs(), player.gold()));
            kills += player.kills();
            deaths += player.deaths();
            assists += player.assists();
            gold += player.gold();
        }
        return new TeamInput(kills, deaths, assists, gold, rows, teamBans);
    }
}
