package com.lolinhouse.record;

import com.lolinhouse.common.web.ApiException;
import com.lolinhouse.common.web.PublicReadErrorCodes;
import com.lolinhouse.ingest.image.ImageStore;
import com.lolinhouse.session.GameSet;
import com.lolinhouse.session.GameSetRepository;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 역할: 담당자 전용 원본 결과창 이미지 보기(/api/admin/** 인가는 SecurityConfig). 공개 응답에는 이미지 키가 없고 이 경로로만 바이트를 준다.
 * 없는 세트·삭제된 세트·수동 입력(이미지 없음)·저장소에 파일 없음은 모두 404 SET_IMAGE_NOT_FOUND.
 */
@RestController
public class SetImageAdminController {

    private final GameSetRepository gameSets;
    private final ImageStore imageStore;

    public SetImageAdminController(GameSetRepository gameSets, ImageStore imageStore) {
        this.gameSets = gameSets;
        this.imageStore = imageStore;
    }

    @GetMapping("/api/admin/sets/{id}/image")
    ResponseEntity<byte[]> image(@PathVariable long id) {
        GameSet set = gameSets.findById(id).filter(s -> !s.isDeleted()).orElseThrow(() -> notFound(id));
        String key = set.imageKey();
        if (key == null) {
            throw notFound(id);
        }
        byte[] bytes;
        try {
            bytes = imageStore.get(key);
        } catch (RuntimeException e) {
            throw notFound(id);
        }
        MediaType type = key.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok().contentType(type).cacheControl(CacheControl.noStore().cachePrivate()).body(bytes);
    }

    private static ApiException notFound(long id) {
        return ApiException.notFound(PublicReadErrorCodes.SET_IMAGE_NOT_FOUND, "원본 이미지가 없습니다: " + id);
    }
}
