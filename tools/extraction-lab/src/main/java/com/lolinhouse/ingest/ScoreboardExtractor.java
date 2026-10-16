package com.lolinhouse.ingest;

import com.fasterxml.jackson.databind.JsonNode;

/** 백엔드 이식 시 모델 공급자를 교체할 경계다. 관련 노트: docs/study/05-extraction-lab.md */
public interface ScoreboardExtractor {
    /** 이미지 한 장에서 정답지 없이 초안을 만든다. 관련 노트: docs/study/05-extraction-lab.md */
    JsonNode extract(byte[] png) throws Exception;
}
