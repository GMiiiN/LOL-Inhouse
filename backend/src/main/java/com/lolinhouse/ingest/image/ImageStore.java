package com.lolinhouse.ingest.image;

/**
 * 역할: 업로드 이미지 저장소 경계. M4 구현은 로컬 폴더(LocalImageStore, 작업자 B), M7에서 S3 구현으로 바꾼다.
 * 키는 {hash 앞 2자}/{hash}.{png|jpg} 형식이라 같은 이미지는 같은 키이고 다시 써도 무해하다(멱등).
 * 이미지에 친구 닉네임이 있으므로 저장 위치는 git 제외 폴더여야 한다.
 * 설명: docs/design/m4-design.md D7, §2.3
 */
public interface ImageStore {

    /**
     * 이미지를 저장하고 키를 돌려준다. 이미 같은 키가 있으면 다시 쓰지 않는다.
     *
     * @param hash     SHA-256 hex 64자
     * @param mimeType "image/png" 또는 "image/jpeg"
     * @throws IllegalArgumentException 해시·형식이 키 규칙에 맞지 않음
     * @throws java.io.UncheckedIOException 쓰기 실패
     */
    String put(String hash, String mimeType, byte[] bytes);

    /**
     * 키로 바이트를 읽는다.
     *
     * @throws IllegalArgumentException 키 형식이 규칙에 맞지 않거나 저장소 밖을 가리킴
     * @throws java.io.UncheckedIOException 없거나 읽을 수 없음(작업은 IMAGE_UNREADABLE로 실패)
     */
    byte[] get(String key);
}
