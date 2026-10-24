package dev.nerydlg.ui;

import org.junit.jupiter.api.Test;

import java.awt.Image;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImageUtilsTest {

  @Test
  void scaleTheAppIconToEveryRequestedSize() {
    List<Image> images = ImageUtils.createIconImages("/ico/mumied.png", 16, 32, 128);

    assertEquals(3, images.size());
    assertEquals(16, images.get(0).getWidth(null));
    assertEquals(32, images.get(1).getHeight(null));
    assertEquals(128, images.get(2).getWidth(null));
  }

  @Test
  void rejectAMissingIcon() {
    assertThrows(IllegalArgumentException.class, () -> ImageUtils.createIconImages("/ico/missing.png", 16));
  }
}
