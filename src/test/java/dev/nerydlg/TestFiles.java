package dev.nerydlg;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/** Generates the audio files and images the tests work on, so no real music is needed. */
public final class TestFiles {

  private TestFiles() {
  }

  /** Writes one second of silence as a 16-bit mono WAV file. */
  public static Path wav(Path file) throws IOException {
    AudioFormat format = new AudioFormat(44100, 16, 1, true, false);
    byte[] silence = new byte[44100 * 2];
    AudioInputStream audio = new AudioInputStream(new ByteArrayInputStream(silence), format, 44100);
    AudioSystem.write(audio, AudioFileFormat.Type.WAVE, file.toFile());
    return file;
  }

  public static BufferedImage image(int width, int height, int type) {
    BufferedImage image = new BufferedImage(width, height, type);
    Graphics2D graphics = image.createGraphics();
    graphics.fillRect(width / 4, height / 4, width / 2, height / 2);
    graphics.dispose();
    return image;
  }

  public static byte[] png(int width, int height) {
    return encode(image(width, height, BufferedImage.TYPE_INT_RGB), "png", false);
  }

  public static byte[] jpeg(int width, int height, boolean progressive) {
    return encode(image(width, height, BufferedImage.TYPE_INT_RGB), "jpeg", progressive);
  }

  public static byte[] encode(BufferedImage image, String format, boolean progressive) {
    ImageWriter writer = ImageIO.getImageWritersByFormatName(format).next();
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
      writer.setOutput(output);
      ImageWriteParam param = writer.getDefaultWriteParam();
      if (progressive) {
        param.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
      }
      writer.write(null, new IIOImage(image, null, null), param);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } finally {
      writer.dispose();
    }
    return bytes.toByteArray();
  }

  public static BufferedImage decode(byte[] image) throws IOException {
    return ImageIO.read(new ByteArrayInputStream(image));
  }
}
