package dev.nerydlg.service;

import dev.nerydlg.TestFiles;
import dev.nerydlg.model.Track;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Writes real WAV files, the one format the JDK can generate; MP3 and FLAC go through the same jaudiotagger API. */
class TrackServiceTest {

  @TempDir
  Path directory;

  private final TrackService service = new TrackService();

  private Track loadOnly() throws IOException {
    List<Track> tracks = service.loadDirectory(directory);
    assertEquals(1, tracks.size());
    return tracks.getFirst();
  }

  @Test
  void listsOnlySupportedAudioFilesSortedByName() throws IOException {
    TestFiles.wav(directory.resolve("b.wav"));
    TestFiles.wav(directory.resolve("a.wav"));
    Files.writeString(directory.resolve("notes.txt"), "not music");
    Files.createDirectory(directory.resolve("folder.mp3"));

    List<String> names = service.loadDirectory(directory).stream().map(Track::getFileName).toList();

    assertEquals(List.of("a.wav", "b.wav"), names);
  }

  @Test
  void stillListsFilesWhoseTagsCantBeRead() throws IOException {
    Files.writeString(directory.resolve("broken.mp3"), "not really an mp3");

    Track track = loadOnly();

    assertEquals("", track.getArtist());
    assertNull(track.getCover());
  }

  @Test
  void savesAndReloadsMetadata() throws Exception {
    TestFiles.wav(directory.resolve("song.wav"));
    Track track = loadOnly();
    track.setArtist("Joaquín");
    track.setAlbum("Album");
    track.setYear("2001");

    service.save(track);

    assertFalse(track.isDirty());
    Track reloaded = loadOnly();
    assertEquals("Joaquín", reloaded.getArtist());
    assertEquals("Album", reloaded.getAlbum());
    assertEquals("2001", reloaded.getYear());
  }

  @Test
  void savesAndReloadsTheCover() throws Exception {
    TestFiles.wav(directory.resolve("song.wav"));
    byte[] cover = TestFiles.png(300, 300);
    Track track = loadOnly();
    assertNull(track.getCover());

    track.setCover(cover);
    service.save(track);

    assertArrayEquals(cover, loadOnly().getCover());
  }

  @Test
  void keepsTheCoverWhenOnlyOtherFieldsChange() throws Exception {
    TestFiles.wav(directory.resolve("song.wav"));
    byte[] cover = TestFiles.jpeg(200, 200, false);
    Track track = loadOnly();
    track.setCover(cover);
    service.save(track);

    Track reloaded = loadOnly();
    reloaded.setAlbum("Other");
    service.save(reloaded);

    assertArrayEquals(cover, loadOnly().getCover());
  }

  @Test
  void removesTheCover() throws Exception {
    TestFiles.wav(directory.resolve("song.wav"));
    Track track = loadOnly();
    track.setArtist("Artist");
    track.setCover(TestFiles.png(100, 100));
    service.save(track);

    Track reloaded = loadOnly();
    reloaded.setCover(null);
    service.save(reloaded);

    Track afterRemoval = loadOnly();
    assertNull(afterRemoval.getCover());
    assertEquals("Artist", afterRemoval.getArtist());
  }

  @Test
  void renamesTheFileOnSave() throws Exception {
    TestFiles.wav(directory.resolve("old.wav"));
    Track track = loadOnly();
    track.setFileName("new.wav");

    service.save(track);

    assertEquals(directory.resolve("new.wav"), track.getPath());
    assertTrue(Files.exists(directory.resolve("new.wav")));
    assertFalse(Files.exists(directory.resolve("old.wav")));
  }

  @Test
  void neverOverwritesAnotherFileWhenRenaming() throws Exception {
    TestFiles.wav(directory.resolve("a.wav"));
    Path other = Files.writeString(directory.resolve("b.wav"), "keep me");
    Track track = service.loadDirectory(directory).getFirst();
    track.setFileName("b.wav");

    assertThrows(FileAlreadyExistsException.class, () -> service.save(track));
    assertEquals("keep me", Files.readString(other));
  }

  @Test
  void readCoverImageAcceptsPngAndJpeg() throws IOException {
    byte[] png = TestFiles.png(100, 100);
    byte[] jpeg = TestFiles.jpeg(100, 100, false);

    assertArrayEquals(png, TrackService.readCoverImage(Files.write(directory.resolve("c.png"), png)));
    assertArrayEquals(jpeg, TrackService.readCoverImage(Files.write(directory.resolve("c.jpg"), jpeg)));
  }

  @Test
  void readCoverImageResizesLargeImages() throws IOException {
    Path big = Files.write(directory.resolve("big.png"), TestFiles.png(1000, 1000));

    byte[] cover = TrackService.readCoverImage(big);

    assertEquals(CoverImageResizer.MAX_SIZE, TestFiles.decode(cover).getWidth());
  }

  @Test
  void readCoverImageRejectsOtherFiles() throws IOException {
    Path gif = Files.write(directory.resolve("c.gif"), TestFiles.encode(TestFiles.image(10, 10, 1), "gif", false));
    Path wav = TestFiles.wav(directory.resolve("c.wav"));

    assertThrows(IOException.class, () -> TrackService.readCoverImage(gif));
    assertThrows(IOException.class, () -> TrackService.readCoverImage(wav));
  }
}
