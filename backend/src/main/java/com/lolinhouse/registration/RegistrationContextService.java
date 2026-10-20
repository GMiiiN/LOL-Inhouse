package com.lolinhouse.registration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.lolinhouse.common.web.ApiException;
import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.player.KnownAlias;
import com.lolinhouse.domain.set.DuelTeamRules;
import com.lolinhouse.domain.set.FearlessChain;
import com.lolinhouse.domain.set.FearlessChain.ChainDuel;
import com.lolinhouse.player.Player;
import com.lolinhouse.player.PlayerAlias;
import com.lolinhouse.player.PlayerAliasRepository;
import com.lolinhouse.player.PlayerRepository;
import com.lolinhouse.registration.RegistrationContextResponse.RegisteredSetSummary;
import com.lolinhouse.session.Duel;
import com.lolinhouse.session.DuelRepository;
import com.lolinhouse.session.FearlessMode;
import com.lolinhouse.session.GameSet;
import com.lolinhouse.session.GameSetPlayer;
import com.lolinhouse.session.GameSetRepository;
import com.lolinhouse.session.InhouseSession;
import com.lolinhouse.session.InhouseSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 역할: 대결 하나의 등록 맥락을 DB에서 모은다 — 다음 세트 번호, 직전 세트(팀별 선수·포지션), 이어가기 체인을 포함한 피어리스 제한,
 * 알려진 별칭. 평가기(SetEvaluator)가 DB를 읽지 않도록 값 묶음(DuelState, PlayerDirectory)으로 바꿔 준다.
 * 메서드는 호출자의 트랜잭션에 참여하며(직전 세트의 선수 목록이 지연 로딩이라 트랜잭션이 필요하다) 아무것도 쓰지 않는다.
 * 설명: docs/design/m4-design.md §2.5, §11.1
 */
@Service
public class RegistrationContextService {

    private final DuelRepository duelRepository;
    private final InhouseSessionRepository sessionRepository;
    private final GameSetRepository gameSetRepository;
    private final PlayerRepository playerRepository;
    private final PlayerAliasRepository aliasRepository;

    public RegistrationContextService(DuelRepository duelRepository, InhouseSessionRepository sessionRepository,
            GameSetRepository gameSetRepository, PlayerRepository playerRepository,
            PlayerAliasRepository aliasRepository) {
        this.duelRepository = duelRepository;
        this.sessionRepository = sessionRepository;
        this.gameSetRepository = gameSetRepository;
        this.playerRepository = playerRepository;
        this.aliasRepository = aliasRepository;
    }

    /** GET registration-context 응답. */
    @Transactional(readOnly = true)
    public RegistrationContextResponse context(Long duelId) {
        Duel duel = duelRepository.findById(duelId).orElseThrow(() -> duelNotFound(duelId));
        InhouseSession session = sessionRepository.findById(duel.sessionId()).orElseThrow(() -> duelNotFound(duelId));
        DuelState state = stateFor(duel);
        DuelTeam previousTop = state.previousSet() == null ? null : state.previousSet().topTeam();
        List<RegisteredSetSummary> sets = gameSetRepository.findByDuelIdAndDeletedAtIsNullOrderBySetNoAsc(duelId)
                .stream()
                .map(set -> new RegisteredSetSummary(set.id(), set.setNo(), set.team1Side(), set.winnerTeam(),
                        set.topTeam()))
                .toList();
        return new RegistrationContextResponse(duel.id(), duel.sessionId(), session.startDate(), duel.seq(),
                duel.fearlessMode(), state.nextSetNo(),
                DuelTeamRules.defaultTopSide(state.nextSetNo(), previousTop).orElse(null),
                List.copyOf(new TreeSet<>(state.restrictedChampions())), sets);
    }

    /** 기존 대결의 현재 상태. */
    @Transactional(readOnly = true)
    public DuelState stateFor(Duel duel) {
        List<Long> chain = chainDuelIds(duel.sessionId(), duel.id(), duel.seq(), duel.fearlessMode());
        Set<String> restricted = new HashSet<>(gameSetRepository.findActivePickChampionKeysByDuelIds(chain));
        int nextSetNo = gameSetRepository.findMaxActiveSetNo(duel.id()) + 1;
        DuelState.PreviousSet previous = gameSetRepository
                .findFirstByDuelIdAndDeletedAtIsNullOrderBySetNoDesc(duel.id()).map(this::toPreviousSet).orElse(null);
        return new DuelState(duel.id(), nextSetNo, previous, restricted);
    }

    /**
     * "새 대결로 등록"을 위한 가상 상태: 같은 묶음의 다음 번호(max+1) 대결을 mode로 만든다고 가정한다(아직 만들지 않는다).
     * RESET이면 제한 없음, CONTINUE면 지금 마지막 대결부터 거슬러 올라가 처음 만나는 RESET까지의 픽 전부.
     */
    @Transactional(readOnly = true)
    public DuelState stateForNewDuel(Duel original, FearlessMode mode) {
        Set<String> restricted = new HashSet<>();
        if (mode == FearlessMode.CONTINUE) {
            int nextSeq = duelRepository.findMaxSeq(original.sessionId()) + 1;
            List<Long> chain = chainDuelIds(original.sessionId(), VIRTUAL_DUEL_ID, nextSeq, FearlessMode.CONTINUE);
            chain.remove(VIRTUAL_DUEL_ID);
            restricted.addAll(gameSetRepository.findActivePickChampionKeysByDuelIds(chain));
        }
        return DuelState.firstSet(null, restricted);
    }

    /** 모든 선수와 별칭(친구 모임 규모라 통째로 읽는다). */
    @Transactional(readOnly = true)
    public PlayerDirectory directory() {
        Map<Long, Player> players = new HashMap<>();
        playerRepository.findAll().forEach(player -> players.put(player.id(), player));
        Map<Long, String> displayNames = new HashMap<>();
        players.forEach((id, player) -> displayNames.put(id, player.displayName()));
        Map<String, Long> owners = new HashMap<>();
        List<KnownAlias> known = new ArrayList<>();
        for (PlayerAlias alias : aliasRepository.findAll()) {
            owners.put(alias.nicknameKey(), alias.playerId());
            Player owner = players.get(alias.playerId());
            known.add(alias.toKnownAlias(owner != null && owner.isRegistered()));
        }
        return new PlayerDirectory(displayNames, owners, known);
    }

    private static final Long VIRTUAL_DUEL_ID = -1L;

    // 같은 묶음의 대결들로 체인을 계산한다. targetId가 아직 없는 대결이면 (targetSeq, targetMode)로 가상 추가한다
    private List<Long> chainDuelIds(Long sessionId, Long targetId, int targetSeq, FearlessMode targetMode) {
        List<ChainDuel> duels = new ArrayList<>();
        for (Duel duel : duelRepository.findBySessionIdOrderBySeqAsc(sessionId)) {
            duels.add(new ChainDuel(duel.id(), duel.seq(), duel.fearlessMode() == FearlessMode.CONTINUE));
        }
        if (VIRTUAL_DUEL_ID.equals(targetId)) {
            duels.add(new ChainDuel(VIRTUAL_DUEL_ID, targetSeq, targetMode == FearlessMode.CONTINUE));
        }
        return new ArrayList<>(FearlessChain.chainDuelIds(duels, targetId));
    }

    private DuelState.PreviousSet toPreviousSet(GameSet set) {
        Map<String, Position> team1 = new LinkedHashMap<>();
        Map<String, Position> team2 = new LinkedHashMap<>();
        for (GameSetPlayer player : set.players()) {
            (player.team() == DuelTeam.TEAM1 ? team1 : team2).put(String.valueOf(player.playerId()), player.position());
        }
        return new DuelState.PreviousSet(set.id(), set.setNo(), set.topTeam(), team1, team2);
    }

    private ApiException duelNotFound(Long duelId) {
        return ApiException.notFound("DUEL_NOT_FOUND", "대결을 찾을 수 없습니다: " + duelId);
    }
}
