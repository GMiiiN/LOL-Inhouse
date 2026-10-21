package com.lolinhouse.teammaker;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 역할: 팀 짜기 계산 HTTP 입구(로그인 없이 공개, CSRF는 필요). 접속 주소는 request.getRemoteAddr()를 쓴다(데스크탑 운영에서는 forward-headers-strategy=native라 nginx가 넘긴 실제 접속 주소가 된다).
 */
@RestController
@RequestMapping("/api/team-maker")
public class TeamMakerController {

    private final TeamMakerService service;

    public TeamMakerController(TeamMakerService service) {
        this.service = service;
    }

    @PostMapping("/auto")
    TeamMakerResponse auto(@RequestBody TeamMakerAutoRequest request, HttpServletRequest http) {
        return service.auto(request, http.getRemoteAddr());
    }

    @PostMapping("/manual")
    TeamMakerResponse manual(@RequestBody TeamMakerManualRequest request, HttpServletRequest http) {
        return service.manual(request, http.getRemoteAddr());
    }
}
