package dev.nerydlg;

import dev.nerydlg.configuration.UIConfiguration;
import dev.nerydlg.ui.UIWindowContainer;

import javax.swing.SwingUtilities;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {

  public static void main(String[] args) {
    // Swing components must be created and used on the Event Dispatch Thread.
    SwingUtilities.invokeLater(() -> {
      UIConfiguration uiConfiguration = new UIConfiguration();
      uiConfiguration.setupLookAndFeel();
      UIWindowContainer windowContainer = new UIWindowContainer(uiConfiguration.createMainFrame());
      windowContainer.show();
    });
  }
}