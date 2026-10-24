package dev.nerydlg.ui;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Component;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/** Shows a track's embedded cover image as a small thumbnail. */
class CoverRenderer extends DefaultTableCellRenderer {

  static final int SIZE = 48;

  // Keyed by array identity: decoding and scaling every repaint would be slow for big folders.
  private final Map<byte[], Optional<Icon>> thumbnails = new WeakHashMap<>();

  CoverRenderer() {
    setHorizontalAlignment(JLabel.CENTER);
  }

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
      int row, int column) {
    super.getTableCellRendererComponent(table, null, isSelected, hasFocus, row, column);
    byte[] cover = (byte[]) value;
    Optional<Icon> thumbnail = cover == null ? Optional.empty() : thumbnails.computeIfAbsent(cover, this::thumbnailOf);
    setIcon(thumbnail.orElse(null));
    // "?" marks a cover that is embedded but can't be decoded.
    setText(cover != null && thumbnail.isEmpty() ? "?" : null);
    return this;
  }

  private Optional<Icon> thumbnailOf(byte[] cover) {
    try {
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(cover));
      if (image == null) {
        return Optional.empty();
      }
      return Optional.of(new ImageIcon(image.getScaledInstance(SIZE, SIZE, Image.SCALE_SMOOTH)));
    } catch (IOException e) {
      return Optional.empty();
    }
  }
}
