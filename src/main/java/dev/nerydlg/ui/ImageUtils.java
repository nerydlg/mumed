package dev.nerydlg.ui;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ImageUtils {

  public static ImageIcon createImageIcon(String path, int width, int height) {
    Image img = new ImageIcon(requireResource(path)).getImage()
        .getScaledInstance(width, height, Image.SCALE_SMOOTH);
    return new ImageIcon(img);
  }

  /** Scales the image to each square size, so the window system can pick the sharpest one. */
  public static List<Image> createIconImages(String path, int... sizes) {
    BufferedImage source;
    try {
      source = ImageIO.read(requireResource(path));
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read icon " + path, e);
    }
    if (source == null) {
      throw new IllegalArgumentException("Icon is not a readable image: " + path);
    }
    List<Image> images = new ArrayList<>(sizes.length);
    for (int size : sizes) {
      images.add(scale(source, size));
    }
    return images;
  }

  public static URL getResource(String path) {
    return ImageUtils.class.getResource(path);
  }

  private static URL requireResource(String path) {
    URL url = getResource(path);
    if (url == null) {
      throw new IllegalArgumentException("Icon not found on classpath: " + path);
    }
    return url;
  }

  private static BufferedImage scale(BufferedImage source, int size) {
    BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = scaled.createGraphics();
    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    graphics.drawImage(source, 0, 0, size, size, null);
    graphics.dispose();
    return scaled;
  }
}
