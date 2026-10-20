package com.lolinhouse.champion;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoxesTest {

    @Test
    void boxIsYFirstAndClippedToTheImage() {
        assertThat(Boxes.pixels(new int[] {100, 200, 900, 800}, 200, 100)).isEqualTo(new Rectangle(40, 10, 120, 80));
        assertThat(Boxes.pixels(new int[] {-10, -20, 1010, 1020}, 200, 100)).isEqualTo(new Rectangle(0, 0, 200, 100));
    }

    @Test
    void rejectsInvalidBoxes() {
        assertThatThrownBy(() -> Boxes.pixels(new int[] {5, 5, 1, 1}, 100, 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Boxes.pixels(new int[] {1, 2, 3}, 100, 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Boxes.pixels(new int[] {0, 1100, 10, 1200}, 100, 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Boxes.pixels(null, 100, 100)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tinyBoxHasAtLeastOnePixel() {
        assertThat(Boxes.pixels(new int[] {1, 1, 2, 2}, 10, 10).width).isEqualTo(1);
    }

    @Test
    void cropKeepsOriginalPixels() {
        BufferedImage image = new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB);
        image.setRGB(4, 1, 0x123456);

        BufferedImage crop = Boxes.crop(image, new int[] {100, 200, 900, 800});

        assertThat(crop.getWidth()).isEqualTo(12);
        assertThat(crop.getHeight()).isEqualTo(8);
        assertThat(crop.getRGB(0, 0) & 0xffffff).isEqualTo(0x123456);
    }
}
