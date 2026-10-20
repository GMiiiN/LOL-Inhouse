package com.lolinhouse.ingest.image;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;

import javax.imageio.ImageIO;

import com.lolinhouse.common.web.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 역할: 업로드 이미지 검사 규칙(용량·시그니처·크기 상한·해시)을 확인한다. 스프링 없이 순수 단위로 돌린다.
 */
class ImageInspectorTest {

    final ImageInspector inspector = new ImageInspector(10 * 1024 * 1024, 4096);

    static byte[] encode(int width, int height, String format) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, format, out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private void assertRejected(byte[] bytes, HttpStatus status, String code) {
        assertThatThrownBy(() -> inspector.inspect(bytes))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.status()).isEqualTo(status);
                    assertThat(e.code()).isEqualTo(code);
                });
    }

    @Test
    void pngPassesWithSizeMimeAndHex64Hash() {
        byte[] png = encode(40, 30, "png");

        InspectedImage image = inspector.inspect(png);

        assertThat(image.mimeType()).isEqualTo("image/png");
        assertThat(image.width()).isEqualTo(40);
        assertThat(image.height()).isEqualTo(30);
        assertThat(image.hash()).matches("[0-9a-f]{64}");
        assertThat(image.bytes()).isEqualTo(png);
    }

    @Test
    void jpegPasses() {
        InspectedImage image = inspector.inspect(encode(50, 20, "jpg"));

        assertThat(image.mimeType()).isEqualTo("image/jpeg");
        assertThat(image.width()).isEqualTo(50);
        assertThat(image.height()).isEqualTo(20);
    }

    @Test
    void sameBytesGiveSameHashAndDifferentBytesDiffer() {
        byte[] a = encode(10, 10, "png");
        byte[] b = encode(11, 10, "png");

        assertThat(inspector.inspect(a).hash()).isEqualTo(inspector.inspect(a.clone()).hash());
        assertThat(inspector.inspect(a).hash()).isNotEqualTo(inspector.inspect(b).hash());
    }

    @Test
    void gifGarbageAndEmptyAreRejectedAsUnsupported() {
        assertRejected(encode(10, 10, "gif"), HttpStatus.BAD_REQUEST, "UNSUPPORTED_IMAGE");
        assertRejected("이건 이미지가 아닙니다".getBytes(), HttpStatus.BAD_REQUEST, "UNSUPPORTED_IMAGE");
        assertRejected(new byte[0], HttpStatus.BAD_REQUEST, "UNSUPPORTED_IMAGE");
        assertRejected(null, HttpStatus.BAD_REQUEST, "UNSUPPORTED_IMAGE");
    }

    @Test
    void signatureOnlyWithBrokenBodyIsRejected() {
        assertRejected(new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 1, 2, 3}, HttpStatus.BAD_REQUEST, "UNSUPPORTED_IMAGE");
        assertRejected(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2}, HttpStatus.BAD_REQUEST, "UNSUPPORTED_IMAGE");
    }

    @Test
    void pngSignatureOnJpegBodyIsRejected() {
        byte[] jpeg = encode(10, 10, "jpg");
        byte[] fake = Arrays.copyOf(jpeg, jpeg.length);
        fake[0] = (byte) 0x89;
        fake[1] = 0x50;
        fake[2] = 0x4E;
        fake[3] = 0x47;

        assertRejected(fake, HttpStatus.BAD_REQUEST, "UNSUPPORTED_IMAGE");
    }

    @Test
    void bytesOverLimitAreRejectedWith413BeforeAnyDecoding() {
        ImageInspector small = new ImageInspector(100, 4096);

        assertThatThrownBy(() -> small.inspect(encode(100, 100, "png")))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
                    assertThat(e.code()).isEqualTo("IMAGE_TOO_LARGE");
                });
    }

    @Test
    void sideOverLimitIsRejectedUsingOnlyTheHeader() {
        ImageInspector strict = new ImageInspector(10 * 1024 * 1024, 64);

        assertThat(strict.inspect(encode(64, 64, "png")).width()).isEqualTo(64);
        assertThatThrownBy(() -> strict.inspect(encode(65, 10, "png")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("IMAGE_DIMENSIONS_EXCEEDED"));
        assertThatThrownBy(() -> strict.inspect(encode(10, 65, "png")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("IMAGE_DIMENSIONS_EXCEEDED"));
    }

    @Test
    void largeDimensionsAreRejectedWithoutDecodingAllPixels() {
        // 8000x8000 단색 PNG는 파일이 작지만 풀면 256MB다. 헤더만 읽으므로 즉시 거절된다.
        byte[] huge = encode(8000, 8000, "png");

        assertThat(huge.length).isLessThan(10 * 1024 * 1024);
        assertRejected(huge, HttpStatus.BAD_REQUEST, "IMAGE_DIMENSIONS_EXCEEDED");
    }
}
