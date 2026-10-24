package dev.nerydlg.ui;

import dev.nerydlg.model.Track;

import javax.swing.UIManager;
import javax.swing.table.AbstractTableModel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TrackTableModel extends AbstractTableModel {

  private static final String[] COLUMNS = {"Cover", "File Name", "Artist", "Album", "Year"};
  public static final int COVER = 0;
  private static final int FILE_NAME = 1;
  private static final int ARTIST = 2;
  private static final int ALBUM = 3;
  private static final int YEAR = 4;
  private static final Pattern YEAR_PATTERN = Pattern.compile("\\d{4}");

  private List<Track> tracks = List.of();
  private boolean applyToAll;

  /** Replaces the table contents, e.g. after opening a new directory. */
  public void setTracks(List<Track> tracks) {
    this.tracks = List.copyOf(tracks);
    fireTableDataChanged();
  }

  public List<Track> getTracks() {
    return tracks;
  }

  public Track getTrack(int row) {
    return tracks.get(row);
  }

  /** When enabled, editing Artist, Album, Year or the cover in one row copies the value to every row. */
  public void setApplyToAll(boolean applyToAll) {
    this.applyToAll = applyToAll;
  }

  public boolean isApplyToAll() {
    return applyToAll;
  }

  public boolean hasUnsavedChanges() {
    return tracks.stream().anyMatch(Track::isDirty);
  }

  /** Repaints every row, e.g. after saving cleared the dirty flags. */
  public void refreshAll() {
    if (!tracks.isEmpty()) {
      fireTableRowsUpdated(0, tracks.size() - 1);
    }
  }

  @Override
  public int getRowCount() {
    return tracks.size();
  }

  @Override
  public int getColumnCount() {
    return COLUMNS.length;
  }

  @Override
  public String getColumnName(int column) {
    return COLUMNS[column];
  }

  @Override
  public boolean isCellEditable(int row, int column) {
    // The cover is changed through the Edit menu, not by typing in the cell.
    return column != COVER;
  }

  @Override
  public Object getValueAt(int row, int column) {
    Track track = tracks.get(row);
    return switch (column) {
      case COVER -> track.getCover();
      case FILE_NAME -> track.getFileName();
      case ARTIST -> track.getArtist();
      case ALBUM -> track.getAlbum();
      case YEAR -> track.getYear();
      default -> throw new IllegalArgumentException("Unknown column " + column);
    };
  }

  @Override
  public void setValueAt(Object value, int row, int column) {
    Track track = tracks.get(row);
    String text = value == null ? "" : value.toString().trim();
    switch (column) {
      case FILE_NAME -> {
        // Never applied to all rows: every file needs its own name.
        if (!isValidFileName(track, text)) {
          rejectEdit();
          return;
        }
        track.setFileName(text);
        fireTableRowsUpdated(row, row);
      }
      case ARTIST, ALBUM -> applyEdit(row, column, text);
      case YEAR -> {
        if (!text.isEmpty() && !YEAR_PATTERN.matcher(text).matches()) {
          rejectEdit();
          return;
        }
        applyEdit(row, column, text);
      }
      default -> throw new IllegalArgumentException("Unknown column " + column);
    }
  }

  /** Outcome of {@link #replaceInFileNames}: either some names changed, or nothing changed because of conflicts. */
  public record ReplaceResult(int changed, List<String> conflicts) {
  }

  /**
   * Replaces {@code target} with {@code replacement} in every file name, leaving the extension untouched.
   * Names that would become invalid are skipped. If any new name would clash with another track's name or
   * with a file already in the folder, nothing is changed and the clashes are returned instead.
   */
  public ReplaceResult replaceInFileNames(String target, String replacement) {
    Map<Track, String> newNames = new LinkedHashMap<>();
    for (Track track : tracks) {
      String fileName = track.getFileName();
      int dot = fileName.lastIndexOf('.');
      String baseName = dot < 0 ? fileName : fileName.substring(0, dot);
      String extension = dot < 0 ? "" : fileName.substring(dot);
      String newName = baseName.replace(target, replacement).strip() + extension;
      if (!newName.equals(fileName) && isValidFileName(track, newName) && !newName.startsWith(".")) {
        newNames.put(track, newName);
      }
    }

    List<String> conflicts = findConflicts(newNames);
    if (!conflicts.isEmpty()) {
      return new ReplaceResult(0, conflicts);
    }
    newNames.forEach(Track::setFileName);
    refreshAll();
    return new ReplaceResult(newNames.size(), List.of());
  }

  /** Describes every planned rename that would end up with the same name as another track or an existing file. */
  private List<String> findConflicts(Map<Track, String> newNames) {
    Map<String, Long> finalNameCounts = tracks.stream()
        .collect(Collectors.groupingBy(track -> newNames.getOrDefault(track, track.getFileName()), Collectors.counting()));

    List<String> conflicts = new ArrayList<>();
    newNames.forEach((track, newName) -> {
      String current = track.getFileName() + " -> " + newName;
      if (finalNameCounts.get(newName) > 1) {
        conflicts.add(current + " (same name as another track)");
      } else if (existsAsOtherFile(track, newName)) {
        // Also covers another track's current file: tracks are saved one by one, so it would still be there.
        conflicts.add(current + " (a file with that name already exists)");
      }
    });
    return conflicts;
  }

  private static boolean existsAsOtherFile(Track track, String newName) {
    Path newPath = track.getPath().resolveSibling(newName);
    if (!Files.exists(newPath)) {
      return false;
    }
    try {
      // Same file when only the letter case changed on a case-insensitive file system.
      return !Files.isSameFile(newPath, track.getPath());
    } catch (IOException e) {
      return true;
    }
  }

  /**
   * Sets the cover (null removes it) on the given model rows, or on every row when "apply to all" is enabled.
   * Returns how many tracks were updated.
   */
  public int setCover(int[] rows, byte[] cover) {
    List<Track> targets = applyToAll ? tracks : Arrays.stream(rows).mapToObj(tracks::get).toList();
    targets.forEach(track -> track.setCover(cover));
    refreshAll();
    return targets.size();
  }

  /** Sets a metadata value on the edited row, or on every row when "apply to all" is enabled. */
  private void applyEdit(int row, int column, String text) {
    List<Track> targets = applyToAll ? tracks : List.of(tracks.get(row));
    for (Track target : targets) {
      switch (column) {
        case ARTIST -> target.setArtist(text);
        case ALBUM -> target.setAlbum(text);
        case YEAR -> target.setYear(text);
        default -> throw new IllegalArgumentException("Column " + column + " is not metadata");
      }
    }
    if (applyToAll) {
      refreshAll();
    } else {
      fireTableRowsUpdated(row, row);
    }
  }

  /** Must be a plain file name that keeps the original extension, so the file stays a valid audio file. */
  private static boolean isValidFileName(Track track, String name) {
    return !name.isBlank()
        && !name.contains("/")
        && !name.contains("\\")
        && Track.extensionOf(name).equals(Track.extensionOf(track.getPath().getFileName().toString()));
  }

  private static void rejectEdit() {
    UIManager.getLookAndFeel().provideErrorFeedback(null);
  }
}
