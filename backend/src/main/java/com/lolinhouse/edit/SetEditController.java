package com.lolinhouse.edit;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 역할: 저장된 세트 수정 HTTP 입구(담당자 전용, /api/admin/** 인가는 SecurityConfig). 규칙은 SetEditService. */
@RestController
@RequestMapping("/api/admin/sets")
public class SetEditController {

    private final SetEditService service;

    public SetEditController(SetEditService service) {
        this.service = service;
    }

    @GetMapping("/{id}/form")
    SetFormResponse form(@PathVariable long id) {
        return service.form(id);
    }

    @PostMapping("/{id}/check")
    EditEvaluation check(@PathVariable long id, @Valid @RequestBody SetEditRequest request) {
        return service.check(id, request);
    }

    @PutMapping("/{id}")
    SetEditResponse update(@PathVariable long id, @Valid @RequestBody SetEditRequest request) {
        return service.update(id, request);
    }
}
