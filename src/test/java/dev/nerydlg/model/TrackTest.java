package dev.nerydlg.model;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackTest {

  private static final Path PATH = Path.of("music", "Song.mp3");

  private static Track track(byte[] cover) {
    return new Track(PATH, "Artist", "Album", "2001", cover);
  }

  @Test
  void newTrackIsClean() {
    Track track = track(null);

    assertFalse(track.isDirty());
    assertFalse(track.isRenamed());
    assertFalse(track.isCoverChanged());
    assertEquals("Song.mp3", track.getFileName());
  }

  @Test
  void settingTheSameValueDoesNotMarkDirty() {
    Track track = track(new byte[] {1, 2, 3});

    track.setArtist("Artist");
    track.setFileName("Song.mp3");
    track.setCover(new byte[] {1, 2, 3});

    assertFalse(track.isDirty());
    assertFalse(track.isCoverChanged());
  }

  @Test
  void changingMetadataMarksDirtyButNotCoverChanged() {
    Track track = track(null);

    track.setAlbum("Other Album");

    assertTrue(track.isDirty());
    assertFalse(track.isCoverChanged());
  }

  @Test
  void settingAndRemovingACoverMarksItChanged() {
    Track withoutCover = track(null);
    withoutCover.setCover(new byte[] {1});
    assertTrue(withoutCover.isCoverChanged());
    assertTrue(withoutCover.isDirty());

    Track withCover = track(new byte[] {1});
    withCover.setCover(null);
    assertTrue(withCover.isCoverChanged());
    assertNull(withCover.getCover());
  }

  @Test
  void renamingIsComparedWithTheNameOnDisk() {
    Track track = track(null);

    track.setFileName("New.mp3");
    assertTrue(track.isRenamed());

    track.setFileName("Song.mp3");
    assertFalse(track.isRenamed());
  }

  @Test
  void markSavedClearsFlagsAndMovesThePath() {
    Track track = track(null);
    track.setFileName("New.mp3");
    track.setCover(new byte[] {1});

    Path newPath = PATH.resolveSibling("New.mp3");
    track.markSaved(newPath);

    assertEquals(newPath, track.getPath());
    assertFalse(track.isDirty());
    assertFalse(track.isRenamed());
    assertFalse(track.isCoverChanged());
  }

  @Test
  void extensionIsLowerCaseAndEmptyWithoutADot() {
    assertEquals("mp3", Track.extensionOf("Song.MP3"));
    assertEquals("flac", Track.extensionOf("a.b.flac"));
    assertEquals("", Track.extensionOf("README"));
  }
}
