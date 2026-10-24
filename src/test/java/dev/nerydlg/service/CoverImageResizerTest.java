package dev.nerydlg.service;

import dev.nerydlg.TestFiles;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoverImageResizerTest {

  private static final byte JPEG_FIRST_BYTE = (byte) 0xFF;

  @Test
  void keepsSmallPngUnchanged() throws IOException {
    byte[] png = TestFiles.png(300, 300);

    assertArrayEquals(png, CoverImageResizer.makePlayerFriendly(png));
  }

  @Test
  void keepsSmallBaselineJpegUnchanged() throws IOException {
    byte[] jpeg = TestFiles.jpeg(600, 600, false);

    assertArrayEquals(jpeg, CoverImageResizer.makePlayerFriendly(jpeg));
  }

  @Test
  void convertsProgressiveJpegToBaselineWithoutResizing() throws IOException {
    byte[] progressive = TestFiles.jpeg(500, 500, true);

    byte[] result = CoverImageResizer.makePlayerFriendly(progressive);

    BufferedImage image = TestFiles.decode(result);
    assertEquals(500, image.getWidth());
    assertEquals(500, image.getHeight());
    // A baseline JPEG passes through unchanged, which proves it is no longer progressive.
    assertArrayEquals(result, CoverImageResizer.makePlayerFriendly(result));
  }

  @Test
  void shrinksLargeJpegToTheMaximumSize() throws IOException {
    byte[] result = CoverImageResizer.makePlayerFriendly(TestFiles.jpeg(3000, 3000, false));

    BufferedImage image = TestFiles.decode(result);
    assertEquals(CoverImageResizer.MAX_SIZE, image.getWidth());
    assertEquals(CoverImageResizer.MAX_SIZE, image.getHeight());
  }

  @Test
  void keepsTheAspectRatioOfWideAndTallImages() throws IOException {
    BufferedImage wide = TestFiles.decode(CoverImageResizer.makePlayerFriendly(TestFiles.png(1200, 800)));
    assertEquals(600, wide.getWidth());
    assertEquals(400, wide.getHeight());

    BufferedImage tall = TestFiles.decode(CoverImageResizer.makePlayerFriendly(TestFiles.png(400, 1600)));
    assertEquals(150, tall.getWidth());
    assertEquals(600, tall.getHeight());
  }

  @Test
  void convertsLargePngWithTransparencyToJpeg() throws IOException {
    byte[] png = TestFiles.encode(TestFiles.image(1200, 1200, BufferedImage.TYPE_INT_ARGB), "png", false);

    byte[] result = CoverImageResizer.makePlayerFriendly(png);

    assertEquals(JPEG_FIRST_BYTE, result[0]);
    // Transparent areas become white instead of black.
    Color corner = new Color(TestFiles.decode(result).getRGB(0, 0));
    assertTrue(corner.getRed() > 245 && corner.getGreen() > 245 && corner.getBlue() > 245, corner.toString());
  }

  @Test
  void runningTwiceDoesNotRecompress() throws IOException {
    byte[] once = CoverImageResizer.makePlayerFriendly(TestFiles.jpeg(2000, 1000, false));

    assertArrayEquals(once, CoverImageResizer.makePlayerFriendly(once));
  }

  @Test
  void rejectsDataThatIsNotAnImage() {
    assertThrows(IOException.class, () -> CoverImageResizer.makePlayerFriendly(new byte[] {1, 2, 3, 4}));
  }
}
