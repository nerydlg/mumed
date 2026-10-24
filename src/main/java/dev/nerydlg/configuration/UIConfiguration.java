package dev.nerydlg.configuration;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.JFrame;

import static dev.nerydlg.ui.ImageUtils.createIconImages;

public class UIConfiguration {

  public JFrame createMainFrame() {
    JFrame frame = new JFrame("Mumed");
    frame.setIconImages(createIconImages("/ico/mumied.png", 16, 24, 32, 48, 64, 128, 256));
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.setResizable(false);
    frame.setSize(800, 600);
    return frame;
  }

  public void setupLookAndFeel() {
    FlatLightLaf.setup();
  }
}
