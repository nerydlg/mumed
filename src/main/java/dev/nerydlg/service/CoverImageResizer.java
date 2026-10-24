package dev.nerydlg.service;

import org.w3c.dom.Node;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Makes cover images safe for portable players, which often ignore large or progressive JPEGs: images bigger
 * than {@link #MAX_SIZE} pixels, and progressive JPEGs, are re-encoded as a baseline JPEG that fits in
 * {@link #MAX_SIZE} x {@link #MAX_SIZE}. Anything else is kept byte for byte, so it loses no quality.
 */
final class CoverImageResizer {

  static final int MAX_SIZE = 600;
  private static final float JPEG_QUALITY = 0.9f;
  private static final String JPEG_METADATA_FORMAT = "javax_imageio_jpeg_image_1.0";
  private static final String PROGRESSIVE_PROCESS = "2";

  private CoverImageResizer() {
  }

  static byte[] makePlayerFriendly(byte[] image) throws IOException {
    try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(image))) {
      Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) {
        throw new IOException("the image can't be read");
      }
      ImageReader reader = readers.next();
      try {
        reader.setInput(input);
        BufferedImage decoded = reader.read(0);
        boolean tooBig = decoded.getWidth() > MAX_SIZE || decoded.getHeight() > MAX_SIZE;
        if (!tooBig && !isProgressiveJpeg(reader.getImageMetadata(0))) {
          return image;
        }
        return encodeJpeg(fitInMaxSize(decoded));
      } finally {
        reader.dispose();
      }
    }
  }

  private static boolean isProgressiveJpeg(IIOMetadata metadata) {
    if (metadata == null || !JPEG_METADATA_FORMAT.equals(metadata.getNativeMetadataFormatName())) {
      return false;
    }
    // The start-of-frame marker says how the JPEG was encoded; process 2 is progressive.
    Node sof = findNode(metadata.getAsTree(JPEG_METADATA_FORMAT), "sof");
    Node process = sof == null ? null : sof.getAttributes().getNamedItem("process");
    return process != null && PROGRESSIVE_PROCESS.equals(process.getNodeValue());
  }

  private static Node findNode(Node node, String name) {
    if (name.equals(node.getNodeName())) {
      return node;
    }
    for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
      Node found = findNode(child, name);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  /** Scales down keeping the aspect ratio, onto a white background since JPEG has no transparency. */
  private static BufferedImage fitInMaxSize(BufferedImage image) {
    double scale = Math.min(1.0, (double) MAX_SIZE / Math.max(image.getWidth(), image.getHeight()));
    int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
    int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
    BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = result.createGraphics();
    try {
      graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      graphics.drawImage(image, 0, 0, width, height, Color.WHITE, null);
    } finally {
      graphics.dispose();
    }
    return result;
  }

  /** Java's JPEG writer produces baseline JPEGs unless asked otherwise. */
  private static byte[] encodeJpeg(BufferedImage image) throws IOException {
    ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
      writer.setOutput(output);
      ImageWriteParam param = writer.getDefaultWriteParam();
      param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      param.setCompressionQuality(JPEG_QUALITY);
      writer.write(null, new IIOImage(image, null, null), param);
    } finally {
      writer.dispose();
    }
    return bytes.toByteArray();
  }
}
