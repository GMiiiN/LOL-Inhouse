package com.lolinhouse.registration;

import java.util.ArrayList;
import java.util.List;

import com.lolinhouse.champion.CatalogSnapshot;
import com.lolinhouse.champion.IconMatch;
import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.player.MatchType;
import com.lolinhouse.domain.player.NicknameMatchResult;
import com.lolinhouse.domain.player.NicknameMatcher;
import com.lolinhouse.domain.player.NicknameNormalizer;
import com.lolinhouse.domain.set.PositionAssignment;
import com.lolinhouse.ingest.job.ExtractionJob;
import com.lolinhouse.ingest.job.ExtractionResult;
import com.lolinhouse.registration.DraftResponse.Ban;
import com.lolinhouse.registration.DraftResponse.Candidate;
import com.lolinhouse.registration.DraftResponse.ChampionCandidate;
import com.lolinhouse.registration.DraftResponse.ChampionGuess;
import com.lolinhouse.registration.DraftResponse.Match;
import com.lolinhouse.registration.DraftResponse.Row;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 역할: 추출 사실(ExtractionResult)과 대결 맥락으로 초안(DraftResponse)을 만든다. DB를 읽지 않는 계산이라 입력은 모두 인자로 받는다.
 * 규칙: 포지션은 캡처한 사람 포지션으로 배정, 닉네임은 매처 결과에 따라 EXACT/자동 연결은 EXISTING(별칭 추가 안 함),
 * 후보 여러 명과 잘린 새 닉네임은 UNRESOLVED(담당자가 고름), 잘리지 않은 새 닉네임은 NEW. 챔피언·밴은 1순위로 미리 채운다.
 * 설명: docs/design/m4-design.md §3, §11.2-1
 */
@Component
public class DraftService {

    private static final String ELLIPSIS = "…";

    private final SetEvaluator evaluator;
    private final NicknameMatcher matcher = new NicknameMatcher();
    private final double uncertainMargin;

    public DraftService(SetEvaluator evaluator,
            @Value("${app.extraction.uncertain-margin:1.0}") double uncertainMargin) {
        this.evaluator = evaluator;
        this.uncertainMargin = uncertainMargin;
    }

    public DraftResponse build(ExtractionJob job, ExtractionResult result, DuelState duel, PlayerDirectory players,
            CatalogSnapshot catalog, DuplicateLookup duplicates) {
        DraftBuilt top = team(result.top(), PositionAssignment.topTeamPositions(job.capturerPosition()), players);
        DraftBuilt bottom = team(result.bottom(), PositionAssignment.bottomTeamPositions(), players);
        SetRegistrationRequest defaults = new SetRegistrationRequest(job.id(), job.topSide(),
                job.capturerPosition(), result.topResult(), result.shotDate(), result.duration(), top.input(),
                bottom.input(), null);
        Evaluation evaluation = evaluator.evaluate(defaults, duel, players, catalog, duplicates);
        return new DraftResponse(job.id(), job.duelId(), job.topSide(), job.capturerPosition(), result.topResult(),
                result.shotDate(), result.duration(), top.view(), bottom.view(), defaults, evaluation);
    }

    private record DraftBuilt(DraftResponse.Team view, TeamInput input) {
    }

    private DraftBuilt team(ExtractionResult.Team extracted, List<Position> positions, PlayerDirectory players) {
        List<Row> rows = new ArrayList<>();
        List<RowInput> inputs = new ArrayList<>();
        for (int i = 0; i < extracted.rows().size(); i++) {
            ExtractionResult.Row row = extracted.rows().get(i);
            NicknameMatchResult match = match(row.nickname(), players);
            ChampionGuess champion = guess(row.champion());
            rows.add(new Row(i + 1, positions.get(i), row.nickname(), isTruncated(row.nickname()),
                    matchView(match, players), row.kills(), row.deaths(), row.assists(), row.cs(), row.gold(),
                    row.portraitBox(), champion));
            inputs.add(defaultInput(i + 1, positions.get(i), row, match, champion));
        }
        List<Ban> bans = new ArrayList<>();
        List<String> banKeys = new ArrayList<>();
        for (int i = 0; i < extracted.bans().size(); i++) {
            ExtractionResult.Ban ban = extracted.bans().get(i);
            ChampionGuess guess = guess(ban.champion());
            bans.add(new Ban(i + 1, ban.box(), guess));
            banKeys.add(firstCandidate(guess));
        }
        return new DraftBuilt(
                new DraftResponse.Team(extracted.kills(), extracted.deaths(), extracted.assists(), extracted.gold(),
                        rows, bans),
                new TeamInput(extracted.kills(), extracted.deaths(), extracted.assists(), extracted.gold(), inputs,
                        banKeys));
    }

    // 닉네임이 "…"뿐이라 매처가 예외를 던지면 잘린 새 닉네임과 같이 취급한다(담당자가 전체 닉네임을 입력)
    private NicknameMatchResult match(String nickname, PlayerDirectory players) {
        try {
            return matcher.match(nickname, players.knownAliases());
        } catch (IllegalArgumentException e) {
            return new NicknameMatchResult(MatchType.NEW_UNREGISTERED, null, 0.0, 0, List.of(), true, null);
        }
    }

    private RowInput defaultInput(int screenRow, Position position, ExtractionResult.Row row,
            NicknameMatchResult match, ChampionGuess champion) {
        RowResolution resolution;
        Long playerId = null;
        String nickname = row.nickname() == null || row.nickname().isBlank() ? ELLIPSIS : row.nickname();
        switch (match.type()) {
            case EXACT, ONE_CHAR_AUTO, TRUNCATED_AUTO -> {
                resolution = RowResolution.EXISTING;
                playerId = Long.valueOf(match.playerId());
            }
            case CANDIDATES -> resolution = RowResolution.UNRESOLVED;
            default -> {
                if (match.fullNicknameRequired() || match.newPlayerName() == null) {
                    resolution = RowResolution.UNRESOLVED;
                } else {
                    resolution = RowResolution.NEW;
                    nickname = match.newPlayerName();
                }
            }
        }
        return new RowInput(screenRow, position, nickname, resolution, playerId, false, firstCandidate(champion),
                row.kills(), row.deaths(), row.assists(), row.cs(), row.gold());
    }

    private Match matchView(NicknameMatchResult match, PlayerDirectory players) {
        Long playerId = match.playerId() == null ? null : Long.valueOf(match.playerId());
        List<Candidate> candidates = match.candidates().stream()
                .map(candidate -> new Candidate(Long.valueOf(candidate.playerId()),
                        players.displayNames().get(Long.valueOf(candidate.playerId())), candidate.nickname(),
                        candidate.similarity()))
                .toList();
        return new Match(match.type(), playerId, playerId == null ? null : players.displayNames().get(playerId),
                match.similarity(), candidates, match.fullNicknameRequired(), match.newPlayerName());
    }

    private ChampionGuess guess(IconMatch icon) {
        List<ChampionCandidate> candidates = icon.candidates().stream()
                .map(candidate -> new ChampionCandidate(candidate.id(), candidate.score())).toList();
        return new ChampionGuess(candidates, icon.margin(), icon.cropFailed() || icon.margin() < uncertainMargin,
                icon.cropFailed());
    }

    private static String firstCandidate(ChampionGuess guess) {
        return guess.candidates().isEmpty() ? null : guess.candidates().get(0).id();
    }

    private static boolean isTruncated(String nickname) {
        return nickname == null || nickname.isBlank()
                || NicknameNormalizer.stripUnicodeSpaces(nickname).endsWith(ELLIPSIS);
    }
}
