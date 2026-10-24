package dev.nerydlg.ui;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class MainMenuBar {

  private final JMenuBar menuBar = new JMenuBar();
  private final JFrame frame;
  private final Consumer<Path> onOpenDirectory;
  private final Runnable onSave;
  private final Consumer<Boolean> onApplyToAllChanged;
  private final BiConsumer<String, String> onReplaceInFileNames;
  private final Consumer<Path> onSetCover;
  private final Runnable onRemoveCover;
  private final FileChoosers fileChoosers = new FileChoosers();

  public MainMenuBar(JFrame frame, Consumer<Path> onOpenDirectory, Runnable onSave,
      Consumer<Boolean> onApplyToAllChanged, BiConsumer<String, String> onReplaceInFileNames,
      Consumer<Path> onSetCover, Runnable onRemoveCover) {
    this.frame = frame;
    this.onOpenDirectory = onOpenDirectory;
    this.onSave = onSave;
    this.onApplyToAllChanged = onApplyToAllChanged;
    this.onReplaceInFileNames = onReplaceInFileNames;
    this.onSetCover = onSetCover;
    this.onRemoveCover = onRemoveCover;
    init();
  }

  public JMenuBar getMenuBar() {
    return menuBar;
  }

  /** Called once a folder has been loaded, so the next file chooser opens there instead of the home folder. */
  public void setCurrentDirectory(Path currentDirectory) {
    fileChoosers.setCurrentDirectory(currentDirectory);
  }

  private void init() {
    int shortcutMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

    JMenu fileMenu = new JMenu("File");
    fileMenu.setMnemonic(KeyEvent.VK_F);

    JMenuItem openDirectory = new JMenuItem("Open Directory");
    openDirectory.setMnemonic(KeyEvent.VK_O);
    openDirectory.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, shortcutMask));
    openDirectory.addActionListener(this::openDirectory);

    JMenuItem save = new JMenuItem("Save");
    save.setMnemonic(KeyEvent.VK_S);
    save.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, shortcutMask));
    save.addActionListener(event -> onSave.run());

    JMenuItem exitMenuItem = new JMenuItem("Exit");
    exitMenuItem.setMnemonic(KeyEvent.VK_X);
    exitMenuItem.addActionListener(event -> onExit());

    // Add Items
    fileMenu.add(openDirectory);
    fileMenu.add(save);
    fileMenu.addSeparator();
    fileMenu.add(exitMenuItem);
    menuBar.add(fileMenu);

    JMenu editMenu = new JMenu("Edit");
    editMenu.setMnemonic(KeyEvent.VK_E);

    JCheckBoxMenuItem applyToAll = new JCheckBoxMenuItem("Apply to All Tracks");
    applyToAll.setMnemonic(KeyEvent.VK_A);
    applyToAll.setToolTipText("Copy edited Artist, Album, Year and cover values to every track in the folder");
    applyToAll.addItemListener(event -> onApplyToAllChanged.accept(applyToAll.isSelected()));

    JMenuItem replaceInFileNames = new JMenuItem("Replace in File Names...");
    replaceInFileNames.setMnemonic(KeyEvent.VK_R);
    replaceInFileNames.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, shortcutMask));
    replaceInFileNames.setToolTipText("Replace a piece of text in the name of every track in the folder");
    replaceInFileNames.addActionListener(event -> replaceInFileNames());

    JMenuItem setCover = new JMenuItem("Set Album Cover...");
    setCover.setMnemonic(KeyEvent.VK_C);
    setCover.setToolTipText("Embed a JPEG or PNG image as the cover of the selected tracks");
    setCover.addActionListener(event -> chooseCover());

    JMenuItem removeCover = new JMenuItem("Remove Album Cover");
    removeCover.setMnemonic(KeyEvent.VK_M);
    removeCover.setToolTipText("Remove the embedded cover from the selected tracks");
    removeCover.addActionListener(event -> onRemoveCover.run());

    editMenu.add(applyToAll);
    editMenu.addSeparator();
    editMenu.add(replaceInFileNames);
    editMenu.addSeparator();
    editMenu.add(setCover);
    editMenu.add(removeCover);
    menuBar.add(editMenu);
  }

  private void replaceInFileNames() {
    JTextField findField = new JTextField(20);
    JTextField replaceField = new JTextField(20);
    JPanel panel = new JPanel(new GridLayout(0, 1, 0, 4));
    panel.add(new JLabel("Find:"));
    panel.add(findField);
    panel.add(new JLabel("Replace with:"));
    panel.add(replaceField);

    int choice = JOptionPane.showConfirmDialog(frame, panel, "Replace in File Names",
        JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
    if (choice != JOptionPane.OK_OPTION || findField.getText().isEmpty()) {
      return;
    }
    onReplaceInFileNames.accept(findField.getText(), replaceField.getText());
  }

  private File chooseDirectory() {
    JFileChooser fileChooser = fileChoosers.directoryChooser();
    if (fileChooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) {
      return null;
    }
    return fileChooser.getSelectedFile();
  }

  private void chooseCover() {
    JFileChooser fileChooser = fileChoosers.coverChooser();
    if (fileChooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
      onSetCover.accept(fileChooser.getSelectedFile().toPath());
    }
  }

  private void openDirectory(ActionEvent actionEvent) {
    File directory = chooseDirectory();
    if (directory == null) {
      return;
    }
    if (!Files.isDirectory(directory.toPath())) {
      JOptionPane.showMessageDialog(frame, "The chosen file is not a directory", "Warning!", JOptionPane.WARNING_MESSAGE);
      return;
    }
    onOpenDirectory.accept(directory.toPath());
  }

  private void onExit() {
    // Goes through the frame's window listener, so unsaved changes are checked before exiting.
    frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
  }

}
