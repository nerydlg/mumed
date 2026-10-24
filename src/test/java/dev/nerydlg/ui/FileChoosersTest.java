package dev.nerydlg.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JFileChooser;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileChoosersTest {

  @TempDir
  Path directory;

  private final FileChoosers fileChoosers = new FileChoosers();

  @Test
  void startInTheDefaultFolderBeforeAnyFolderIsOpened() {
    File defaultDirectory = new JFileChooser().getCurrentDirectory();

    assertEquals(defaultDirectory, fileChoosers.directoryChooser().getCurrentDirectory());
    assertEquals(defaultDirectory, fileChoosers.coverChooser().getCurrentDirectory());
  }

  @Test
  void startInTheOpenFolder() {
    fileChoosers.setCurrentDirectory(directory);

    assertEquals(directory.toFile(), fileChoosers.directoryChooser().getCurrentDirectory());
    assertEquals(directory.toFile(), fileChoosers.coverChooser().getCurrentDirectory());
  }

  @Test
  void followTheLatestOpenedFolder() throws IOException {
    Path second = Files.createDirectory(directory.resolve("second album"));

    fileChoosers.setCurrentDirectory(directory);
    fileChoosers.setCurrentDirectory(second);

    assertEquals(second.toFile(), fileChoosers.directoryChooser().getCurrentDirectory());
    assertEquals(second.toFile(), fileChoosers.coverChooser().getCurrentDirectory());
  }

  @Test
  void fallBackToTheParentWhenTheOpenFolderWasDeleted() throws IOException {
    Path deleted = Files.createDirectory(directory.resolve("gone"));
    fileChoosers.setCurrentDirectory(deleted);

    Files.delete(deleted);

    assertEquals(directory.toFile(), fileChoosers.directoryChooser().getCurrentDirectory());
    assertEquals(directory.toFile(), fileChoosers.coverChooser().getCurrentDirectory());
  }

  @Test
  void directoryChooserOnlyPicksFolders() {
    assertEquals(JFileChooser.DIRECTORIES_ONLY, fileChoosers.directoryChooser().getFileSelectionMode());
  }

  @Test
  void coverChooserShowsOnlyImagesByDefault() {
    JFileChooser chooser = fileChoosers.coverChooser();

    assertEquals(true, chooser.getFileFilter().accept(new File(directory.toFile(), "cover.JPG")));
    assertEquals(false, chooser.getFileFilter().accept(new File(directory.toFile(), "song.mp3")));
  }
}
