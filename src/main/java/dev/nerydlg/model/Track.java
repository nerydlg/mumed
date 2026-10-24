package dev.nerydlg.model;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

/**
 * An audio file plus its editable metadata. Edits are kept in memory (and the track marked dirty)
 * until they are written to disk by {@link dev.nerydlg.service.TrackService#save(Track)}.
 */
public class Track {

  private Path path;
  private String fileName;
  private String artist;
  private String album;
  private String year;
  /** Encoded image (JPEG or PNG) of the front cover, or null when the file has none. */
  private byte[] cover;
  private boolean coverChanged;
  private boolean dirty;

  public Track(Path path, String artist, String album, String year, byte[] cover) {
    this.path = path;
    this.fileName = path.getFileName().toString();
    this.artist = artist;
    this.album = album;
    this.year = year;
    this.cover = cover;
  }

  public static String extensionOf(String fileName) {
    int dot = fileName.lastIndexOf('.');
    return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase();
  }

  public Path getPath() {
    return path;
  }

  public String getFileName() {
    return fileName;
  }

  public String getArtist() {
    return artist;
  }

  public String getAlbum() {
    return album;
  }

  public String getYear() {
    return year;
  }

  public byte[] getCover() {
    return cover;
  }

  /** True when the cover was set or removed since the last save, so it has to be written to the file. */
  public boolean isCoverChanged() {
    return coverChanged;
  }

  public boolean isDirty() {
    return dirty;
  }

  /** True when the edited file name differs from the file's current name on disk. */
  public boolean isRenamed() {
    return !fileName.equals(path.getFileName().toString());
  }

  public void setFileName(String fileName) {
    markDirtyIfChanged(this.fileName, fileName);
    this.fileName = fileName;
  }

  public void setArtist(String artist) {
    markDirtyIfChanged(this.artist, artist);
    this.artist = artist;
  }

  public void setAlbum(String album) {
    markDirtyIfChanged(this.album, album);
    this.album = album;
  }

  public void setYear(String year) {
    markDirtyIfChanged(this.year, year);
    this.year = year;
  }

  /** {@code cover} is the encoded image, or null to remove the cover. */
  public void setCover(byte[] cover) {
    if (!Arrays.equals(this.cover, cover)) {
      coverChanged = true;
      dirty = true;
    }
    this.cover = cover;
  }

  /** Called after a successful save; {@code newPath} differs from the old path if the file was renamed. */
  public void markSaved(Path newPath) {
    this.path = newPath;
    this.coverChanged = false;
    this.dirty = false;
  }

  private void markDirtyIfChanged(String oldValue, String newValue) {
    if (!Objects.equals(oldValue, newValue)) {
      dirty = true;
    }
  }
}
