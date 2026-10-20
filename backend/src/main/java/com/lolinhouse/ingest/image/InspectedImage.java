package com.lolinhouse.ingest.image;

/**
 * 역할: 업로드 검사(ImageInspector, 작업자 B)를 통과한 이미지. 바이트, 시그니처로 판별한 형식, SHA-256 hex 해시, 픽셀 크기를 묶는다.
 * 설명: docs/design/m4-design.md §2.3
 *
 * @param bytes    원본 바이트(업로드 그대로)
 * @param mimeType "image/png" 또는 "image/jpeg"
 * @param hash     SHA-256 소문자 hex 64자
 * @param width    픽셀 가로
 * @param height   픽셀 세로
 */
public record InspectedImage(byte[] bytes, String mimeType, String hash, int width, int height) {
}
