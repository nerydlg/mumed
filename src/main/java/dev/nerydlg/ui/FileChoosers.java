package dev.nerydlg.ui;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.nio.file.Files;
import java.nio.file.Path;

/** Creates the app's file choosers, starting them in the open folder so the user doesn't have to navigate again. */
class FileChoosers {

  /** The folder whose tracks are shown; null until one is opened, then choosers start in the home folder. */
  private Path currentDirectory;

  void setCurrentDirectory(Path currentDirectory) {
    this.currentDirectory = currentDirectory;
  }

  JFileChooser directoryChooser() {
    JFileChooser fileChooser = inCurrentDirectory();
    fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    fileChooser.setAcceptAllFileFilterUsed(false);
    fileChooser.setDialogTitle("Choose Directory");
    return fileChooser;
  }

  JFileChooser coverChooser() {
    JFileChooser fileChooser = inCurrentDirectory();
    fileChooser.setFileFilter(new FileNameExtensionFilter("Images (JPEG, PNG)", "jpg", "jpeg", "png"));
    fileChooser.setDialogTitle("Choose Album Cover");
    return fileChooser;
  }

  private JFileChooser inCurrentDirectory() {
    Path start = nearestExistingDirectory(currentDirectory);
    return start == null ? new JFileChooser() : new JFileChooser(start.toFile());
  }

  /** JFileChooser jumps back to the home folder for a missing folder, so walk up to the closest one that exists. */
  private static Path nearestExistingDirectory(Path directory) {
    Path candidate = directory;
    while (candidate != null && !Files.isDirectory(candidate)) {
      candidate = candidate.getParent();
    }
    return candidate;
  }
}
