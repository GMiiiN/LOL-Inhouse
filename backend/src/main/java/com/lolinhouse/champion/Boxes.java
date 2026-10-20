package com.lolinhouse.champion;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/**
 * 역할: Gemini가 준 정규화 박스([ymin, xmin, ymax, xmax], 0~1000, y가 먼저)를 원본 이미지의 픽셀 영역으로 바꿔 자른다.
 * 경계를 벗어난 값은 0~1000으로 잘라 쓰고, 면적이 없거나 화면 밖인 박스는 IllegalArgumentException(호출자가 칸마다 cropFailed로 처리).
 * 소수 픽셀은 왼쪽·위는 내림, 오른쪽·아래는 올림으로 둬서 아이콘이 잘리지 않게 한다.
 * 설명: docs/design/m4-design.md §2.2
 */
public final class Boxes {

    private Boxes() {
    }

    /** 정규화 박스를 픽셀 영역으로. */
    public static Rectangle pixels(int[] box, int width, int height) {
        if (box == null || box.length != 4 || width <= 0 || height <= 0 || box[2] <= box[0] || box[3] <= box[1]) {
            throw new IllegalArgumentException("유효하지 않은 박스");
        }
        int left = (int) Math.floor(Math.clamp(box[1], 0, 1000) * width / 1000.0);
        int top = (int) Math.floor(Math.clamp(box[0], 0, 1000) * height / 1000.0);
        int right = (int) Math.ceil(Math.clamp(box[3], 0, 1000) * width / 1000.0);
        int bottom = (int) Math.ceil(Math.clamp(box[2], 0, 1000) * height / 1000.0);
        if (right <= left || bottom <= top) {
            throw new IllegalArgumentException("화면 밖 박스");
        }
        return new Rectangle(left, top, right - left, bottom - top);
    }

    /** 픽셀 좌표로 원본을 자른다(원본 픽셀을 공유하는 부분 이미지). */
    public static BufferedImage crop(BufferedImage image, int[] box) {
        Rectangle r = pixels(box, image.getWidth(), image.getHeight());
        return image.getSubimage(r.x, r.y, r.width, r.height);
    }
}
