package com.lolinhouse.stats;

import java.time.LocalDate;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 역할: 공개 통계 HTTP 입구(선수 목록·프로필, 랭킹, 챔피언 통계, 홈). 로그인 없이 열리는 경로는 SecurityConfig가 정한다.
 * 날짜·enum은 문자열로 받아 형식이 틀리면 400 INVALID_QUERY로 통일한다.
 */
@RestController
@RequestMapping("/api")
public class StatsController {

    private final PlayerRecordService playerService;
    private final RankingService rankingService;
    private final ChampionStatsService championStatsService;
    private final HomeService homeService;
    private final DuoService duoService;

    public StatsController(PlayerRecordService playerService, RankingService rankingService,
            ChampionStatsService championStatsService, HomeService homeService, DuoService duoService) {
        this.duoService = duoService;
        this.playerService = playerService;
        this.rankingService = rankingService;
        this.championStatsService = championStatsService;
        this.homeService = homeService;
    }

    @GetMapping("/players")
    PlayerListResponse players(@RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        LocalDate[] period = period(from, to);
        return playerService.list(period[0], period[1]);
    }

    @GetMapping("/players/{id}")
    PlayerRecordResponse player(@PathVariable long id, @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        LocalDate[] period = period(from, to);
        return playerService.profile(id, period[0], period[1]);
    }

    @GetMapping("/rankings")
    RankingResponse rankings(@RequestParam(required = false) String position,
            @RequestParam(required = false) String sort, @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        var parsedPosition = StatsQuery.position(position);
        var parsedSort = StatsQuery.sort(sort);
        LocalDate[] period = period(from, to);
        return rankingService.rank(parsedPosition, parsedSort, period[0], period[1]);
    }

    @GetMapping("/champion-stats")
    ChampionStatsResponse championStats(@RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        LocalDate[] period = period(from, to);
        return championStatsService.get(period[0], period[1]);
    }

    @GetMapping("/home")
    HomeResponse home() {
        return homeService.get();
    }

    @GetMapping("/duos")
    DuoResponse duos(@RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        LocalDate[] period = period(from, to);
        return duoService.get(period[0], period[1]);
    }

    private static LocalDate[] period(String from, String to) {
        LocalDate parsedFrom = StatsQuery.date("from", from);
        LocalDate parsedTo = StatsQuery.date("to", to);
        StatsQuery.requireOrdered(parsedFrom, parsedTo);
        return new LocalDate[] {parsedFrom, parsedTo};
    }
}
