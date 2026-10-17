package com.lolinhouse.player;

import com.lolinhouse.domain.Position;
import jakarta.validation.constraints.Size;

/**
 * 역할: PUT /api/admin/players/{id} 요청(전체 치환: 실명 + 주 포지션). 별칭은 별도 엔드포인트로만 바꾼다.
 * realName이 비어도 형태 오류가 아니다: 미등록 선수는 "계속 미등록", 등록 선수는 서비스가 400으로 거절한다.
 */
public record PlayerUpdateRequest(@Size(max = 50) String realName, Position mainPosition) {
}
