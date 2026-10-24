package dev.nerydlg.ui;

import dev.nerydlg.model.Track;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackTableModelTest {

  private static final int FILE_NAME = 1;
  private static final int ARTIST = 2;
  private static final int YEAR = 4;

  @TempDir
  Path directory;

  /** Creates the files on disk too, since the conflict check looks for existing files. */
  private TrackTableModel modelWith(String... fileNames) throws IOException {
    List<Track> tracks = Arrays.stream(fileNames).map(this::createTrack).toList();
    TrackTableModel model = new TrackTableModel();
    model.setTracks(tracks);
    return model;
  }

  private Track createTrack(String fileName) {
    try {
      Path path = Files.createFile(directory.resolve(fileName));
      return new Track(path, "", "", "", null);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static List<String> fileNames(TrackTableModel model) {
    return model.getTracks().stream().map(Track::getFileName).toList();
  }

  @Test
  void replacesTextInEveryFileName() throws IOException {
    TrackTableModel model = modelWith("Artist - One.mp3", "Artist - Two.flac", "Other.wav");

    TrackTableModel.ReplaceResult result = model.replaceInFileNames("Artist - ", "");

    assertEquals(2, result.changed());
    assertTrue(result.conflicts().isEmpty());
    assertEquals(List.of("One.mp3", "Two.flac", "Other.wav"), fileNames(model));
    assertTrue(model.getTrack(0).isDirty());
    assertFalse(model.getTrack(2).isDirty());
  }

  @Test
  void neverTouchesTheExtension() throws IOException {
    TrackTableModel model = modelWith("mp3 mix.mp3");

    model.replaceInFileNames("mp3", "flac");

    assertEquals(List.of("flac mix.mp3"), fileNames(model));
  }

  @Test
  void stripsSpacesLeftAtTheEnds() throws IOException {
    TrackTableModel model = modelWith("Song (Official Audio).mp3");

    model.replaceInFileNames("(Official Audio)", "");

    assertEquals(List.of("Song.mp3"), fileNames(model));
  }

  @Test
  void isCaseSensitive() throws IOException {
    TrackTableModel model = modelWith("Live Song.mp3");

    TrackTableModel.ReplaceResult result = model.replaceInFileNames("live", "");

    assertEquals(0, result.changed());
    assertEquals(List.of("Live Song.mp3"), fileNames(model));
  }

  @Test
  void skipsNamesThatWouldBecomeInvalid() throws IOException {
    TrackTableModel model = modelWith("Intro.mp3", "Intro Song.mp3");

    TrackTableModel.ReplaceResult result = model.replaceInFileNames("Intro", "");

    // "Intro.mp3" would become ".mp3", so only the second one changes.
    assertEquals(1, result.changed());
    assertEquals(List.of("Intro.mp3", "Song.mp3"), fileNames(model));
  }

  @Test
  void skipsNamesWithPathSeparators() throws IOException {
    TrackTableModel model = modelWith("A - B.mp3");

    TrackTableModel.ReplaceResult result = model.replaceInFileNames(" - ", "/");

    assertEquals(0, result.changed());
    assertEquals(List.of("A - B.mp3"), fileNames(model));
  }

  @Test
  void changesNothingWhenTwoTracksWouldGetTheSameName() throws IOException {
    // Both become "Song.mp3" once the leftover spaces are stripped.
    TrackTableModel model = modelWith("Song (Live).mp3", "Song  (Live).mp3");

    TrackTableModel.ReplaceResult result = model.replaceInFileNames("(Live)", "");

    assertEquals(0, result.changed());
    assertEquals(List.of(
        "Song (Live).mp3 -> Song.mp3 (same name as another track)",
        "Song  (Live).mp3 -> Song.mp3 (same name as another track)"), result.conflicts());
    assertFalse(model.hasUnsavedChanges());
  }

  @Test
  void changesNothingWhenANameClashesWithAnUnchangedTrack() throws IOException {
    TrackTableModel model = modelWith("01 Song.mp3", "Song.mp3", "02 Other.mp3");

    TrackTableModel.ReplaceResult result = model.replaceInFileNames("01 ", "");

    assertEquals(0, result.changed());
    assertEquals(List.of("01 Song.mp3 -> Song.mp3 (same name as another track)"), result.conflicts());
    assertEquals(List.of("01 Song.mp3", "Song.mp3", "02 Other.mp3"), fileNames(model));
    assertFalse(model.hasUnsavedChanges());
  }

  @Test
  void changesNothingWhenANameClashesWithAFileThatIsNotATrack() throws IOException {
    TrackTableModel model = modelWith("01 Song.mp3");
    Files.createFile(directory.resolve("Song.mp3"));

    TrackTableModel.ReplaceResult result = model.replaceInFileNames("01 ", "");

    assertEquals(List.of("01 Song.mp3 -> Song.mp3 (a file with that name already exists)"), result.conflicts());
    assertEquals(List.of("01 Song.mp3"), fileNames(model));
  }

  @Test
  void treatsAnotherTracksCurrentFileAsAClash() throws IOException {
    // Saving renames one file at a time, so "x" can't take the name "xx" while "xx" is still on disk.
    TrackTableModel model = modelWith("x.mp3", "xx.mp3");

    TrackTableModel.ReplaceResult result = model.replaceInFileNames("x", "xx");

    assertEquals(0, result.changed());
    assertEquals(List.of("x.mp3 -> xx.mp3 (a file with that name already exists)"), result.conflicts());
  }

  @Test
  void setsTheCoverOnTheGivenRowsOnly() throws IOException {
    TrackTableModel model = modelWith("a.mp3", "b.mp3", "c.mp3");
    byte[] cover = {1, 2, 3};

    int updated = model.setCover(new int[] {0, 2}, cover);

    assertEquals(2, updated);
    assertArrayEquals(cover, model.getTrack(0).getCover());
    assertNull(model.getTrack(1).getCover());
    assertArrayEquals(cover, model.getTrack(2).getCover());
  }

  @Test
  void setsTheCoverOnEveryRowWithApplyToAll() throws IOException {
    TrackTableModel model = modelWith("a.mp3", "b.mp3");
    model.setApplyToAll(true);

    int updated = model.setCover(new int[0], new byte[] {1});

    assertEquals(2, updated);
    assertTrue(model.getTracks().stream().allMatch(Track::isCoverChanged));
  }

  @Test
  void removesTheCoverWithNull() throws IOException {
    TrackTableModel model = modelWith("a.mp3");
    model.setCover(new int[] {0}, new byte[] {1});

    model.setCover(new int[] {0}, null);

    assertNull(model.getTrack(0).getCover());
  }

  @Test
  void coverColumnIsNotEditable() throws IOException {
    TrackTableModel model = modelWith("a.mp3");

    assertFalse(model.isCellEditable(0, TrackTableModel.COVER));
    assertTrue(model.isCellEditable(0, FILE_NAME));
  }

  @Test
  void applyToAllCopiesMetadataToEveryRow() throws IOException {
    TrackTableModel model = modelWith("a.mp3", "b.mp3");
    model.setApplyToAll(true);

    model.setValueAt("Band", 0, ARTIST);

    assertEquals("Band", model.getTrack(1).getArtist());
  }

  @Test
  void rejectsInvalidYearsAndFileNames() throws IOException {
    TrackTableModel model = modelWith("a.mp3");

    model.setValueAt("99", 0, YEAR);
    model.setValueAt("a.flac", 0, FILE_NAME);
    model.setValueAt("dir/a.mp3", 0, FILE_NAME);

    assertEquals("", model.getTrack(0).getYear());
    assertEquals("a.mp3", model.getTrack(0).getFileName());
    assertFalse(model.hasUnsavedChanges());
  }
}
