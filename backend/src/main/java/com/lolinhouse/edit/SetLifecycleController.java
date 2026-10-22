package com.lolinhouse.edit;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 역할: 세트 삭제·복구·삭제 목록 HTTP 입구(담당자 전용, /api/admin/** 인가는 SecurityConfig). 규칙은 SetLifecycleService. */
@RestController
@RequestMapping("/api/admin")
public class SetLifecycleController {

    private final SetLifecycleService service;

    public SetLifecycleController(SetLifecycleService service) {
        this.service = service;
    }

    @DeleteMapping("/sets/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable long id) {
        service.delete(id);
    }

    @PostMapping("/sets/{id}/restore")
    RestoreResponse restore(@PathVariable long id) {
        return service.restore(id);
    }

    @GetMapping("/deleted-sets")
    List<DeletedSetRow> deletedSets() {
        return service.deletedSets();
    }
}
