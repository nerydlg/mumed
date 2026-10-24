package dev.nerydlg.ui;

import dev.nerydlg.model.Track;
import dev.nerydlg.service.TrackService;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingWorker;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

public class UIWindowContainer {

  private static final String TITLE = "Mumed";

  private final JFrame frame;
  private final TrackService trackService = new TrackService();
  private final TrackTableModel tableModel = new TrackTableModel();
  private final JTable table;
  private final MainMenuBar mainMenuBar;

  public UIWindowContainer(JFrame frame) {
    this.frame = frame;
    this.table = createTable();

    this.mainMenuBar = new MainMenuBar(frame, this::openDirectory, this::saveChanges, tableModel::setApplyToAll,
        this::replaceInFileNames, this::setCover, this::removeCover);
    frame.setJMenuBar(mainMenuBar.getMenuBar());
    // The table is added once; opening another directory only swaps the model's data.
    frame.add(new JScrollPane(table), BorderLayout.CENTER);

    frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
    frame.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent event) {
        if (confirmLeavingUnsavedChanges()) {
          System.exit(0);
        }
      }
    });
  }

  public void show() {
    frame.setVisible(true);
  }

  private JTable createTable() {
    JTable newTable = new JTable(tableModel) {
      @Override
      public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
        // Show rows with unsaved edits in bold.
        Component component = super.prepareRenderer(renderer, row, column);
        Track track = tableModel.getTrack(convertRowIndexToModel(row));
        component.setFont(component.getFont().deriveFont(track.isDirty() ? Font.BOLD : Font.PLAIN));
        return component;
      }
    };
    newTable.setAutoCreateRowSorter(true);
    ((TableRowSorter<?>) newTable.getRowSorter()).setSortable(TrackTableModel.COVER, false);
    newTable.setFillsViewportHeight(true);
    newTable.setRowHeight(CoverRenderer.SIZE + 4);
    TableColumn coverColumn = newTable.getColumnModel().getColumn(TrackTableModel.COVER);
    coverColumn.setCellRenderer(new CoverRenderer());
    coverColumn.setMinWidth(CoverRenderer.SIZE + 8);
    coverColumn.setMaxWidth(CoverRenderer.SIZE + 8);
    // Commit the edit in progress when the user clicks elsewhere (e.g. the menu).
    newTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
    return newTable;
  }

  private void openDirectory(Path directory) {
    if (!confirmLeavingUnsavedChanges()) {
      return;
    }
    frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    // Reading tags can take a while for big folders, so do it off the Event Dispatch Thread.
    new SwingWorker<List<Track>, Void>() {
      @Override
      protected List<Track> doInBackground() throws Exception {
        return trackService.loadDirectory(directory);
      }

      @Override
      protected void done() {
        frame.setCursor(Cursor.getDefaultCursor());
        try {
          tableModel.setTracks(get());
          frame.setTitle(TITLE + " - " + directory);
          mainMenuBar.setCurrentDirectory(directory);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
          JOptionPane.showMessageDialog(frame, "Could not read " + directory + ":\n" + e.getCause().getMessage(),
              "Error", JOptionPane.ERROR_MESSAGE);
        }
      }
    }.execute();
  }

  /** Saves every edited track. Returns true if all of them were saved. */
  private boolean saveChanges() {
    commitPendingEdit();
    List<String> failures = new ArrayList<>();
    for (Track track : tableModel.getTracks()) {
      if (!track.isDirty()) {
        continue;
      }
      try {
        trackService.save(track);
      } catch (Exception e) {
        failures.add(track.getPath().getFileName() + ": " + Objects.toString(e.getMessage(), e.getClass().getSimpleName()));
      }
    }
    tableModel.refreshAll();

    if (!failures.isEmpty()) {
      JOptionPane.showMessageDialog(frame, "Some files could not be saved:\n" + String.join("\n", failures),
          "Save failed", JOptionPane.ERROR_MESSAGE);
      return false;
    }
    return true;
  }

  private void replaceInFileNames(String target, String replacement) {
    commitPendingEdit();
    TrackTableModel.ReplaceResult result = tableModel.replaceInFileNames(target, replacement);
    if (!result.conflicts().isEmpty()) {
      JOptionPane.showMessageDialog(frame, "Nothing was changed, because these names would clash:\n"
          + String.join("\n", result.conflicts()), "Replace in File Names", JOptionPane.WARNING_MESSAGE);
      return;
    }
    String message = result.changed() == 0
        ? "No file names were changed."
        : result.changed() + " file name(s) changed. Use File > Save to rename the files on disk.";
    JOptionPane.showMessageDialog(frame, message, "Replace in File Names", JOptionPane.INFORMATION_MESSAGE);
  }

  private void setCover(Path imageFile) {
    byte[] cover;
    try {
      cover = TrackService.readCoverImage(imageFile);
    } catch (IOException e) {
      JOptionPane.showMessageDialog(frame, "Could not use " + imageFile.getFileName() + ":\n" + e.getMessage(),
          "Set Album Cover", JOptionPane.ERROR_MESSAGE);
      return;
    }
    applyCover(cover, "Set Album Cover");
  }

  private void removeCover() {
    applyCover(null, "Remove Album Cover");
  }

  /** Sets the cover on the selected tracks (or all of them with "Apply to All Tracks"); null removes it. */
  private void applyCover(byte[] cover, String title) {
    commitPendingEdit();
    int[] rows = Arrays.stream(table.getSelectedRows()).map(table::convertRowIndexToModel).toArray();
    if (rows.length == 0 && !tableModel.isApplyToAll()) {
      JOptionPane.showMessageDialog(frame, "Select one or more tracks first.", title, JOptionPane.INFORMATION_MESSAGE);
      return;
    }
    tableModel.setCover(rows, cover);
  }

  /** Asks what to do with unsaved edits. Returns true if it is OK to discard the current tracks. */
  private boolean confirmLeavingUnsavedChanges() {
    commitPendingEdit();
    if (!tableModel.hasUnsavedChanges()) {
      return true;
    }
    int choice = JOptionPane.showConfirmDialog(frame, "You have unsaved changes. Save them first?",
        "Unsaved changes", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
    return switch (choice) {
      case JOptionPane.YES_OPTION -> saveChanges();
      case JOptionPane.NO_OPTION -> true;
      default -> false;
    };
  }

  private void commitPendingEdit() {
    if (table.isEditing() && !table.getCellEditor().stopCellEditing()) {
      table.getCellEditor().cancelCellEditing();
    }
  }
}
