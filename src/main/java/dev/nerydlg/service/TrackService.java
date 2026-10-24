package dev.nerydlg.service;

import dev.nerydlg.model.Track;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.wav.WavSaveOptions;
import org.jaudiotagger.tag.FieldDataInvalidException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagOptionSingleton;
import org.jaudiotagger.tag.id3.valuepair.ImageFormats;
import org.jaudiotagger.tag.images.Artwork;
import org.jaudiotagger.tag.images.ArtworkFactory;
import org.jaudiotagger.tag.reference.PictureTypes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/** Reads and writes track metadata on disk. */
public class TrackService {

  private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("mp3", "wav", "flac");

  // jaudiotagger logs routine messages even at SEVERE; real failures reach us as exceptions anyway.
  // Keep a strong reference to the logger, otherwise it can be garbage-collected and lose the level.
  private static final Logger JAUDIOTAGGER_LOGGER = Logger.getLogger("org.jaudiotagger");

  static {
    JAUDIOTAGGER_LOGGER.setLevel(Level.OFF);
    // WAV files can hold tags in an ID3 chunk and in a RIFF INFO chunk, and players differ in which
    // one they read. Write both, copying the edited (ID3) values into INFO.
    TagOptionSingleton.getInstance().setWavSaveOptions(WavSaveOptions.SAVE_BOTH_AND_SYNC);
  }

  public static boolean isSupported(Path path) {
    return SUPPORTED_EXTENSIONS.contains(Track.extensionOf(path.getFileName().toString()));
  }

  /**
   * Reads an image to embed as a cover. Only JPEG and PNG are accepted, since those are what players support.
   * Large or progressive images are converted to a baseline JPEG of at most 600x600 pixels.
   */
  public static byte[] readCoverImage(Path imageFile) throws IOException {
    byte[] image = Files.readAllBytes(imageFile);
    String mimeType = ImageFormats.getMimeTypeForBinarySignature(image);
    if (!ImageFormats.MIME_TYPE_JPEG.equals(mimeType) && !ImageFormats.MIME_TYPE_PNG.equals(mimeType)) {
      throw new IOException(imageFile.getFileName() + " is not a JPEG or PNG image");
    }
    return CoverImageResizer.makePlayerFriendly(image);
  }

  /** Lists the supported audio files directly inside {@code directory} (not recursive), sorted by name. */
  public List<Track> loadDirectory(Path directory) throws IOException {
    try (Stream<Path> files = Files.list(directory)) {
      return files
          .filter(Files::isRegularFile)
          .filter(TrackService::isSupported)
          .sorted()
          .map(this::read)
          .toList();
    }
  }

  /** Writes the track's tags to disk, then renames the file if its name was edited. */
  public void save(Track track) throws Exception {
    AudioFile audioFile = AudioFileIO.read(track.getPath().toFile());
    Tag tag = audioFile.getTagOrCreateAndSetDefault();
    setOrDelete(tag, FieldKey.ARTIST, track.getArtist());
    setOrDelete(tag, FieldKey.ALBUM, track.getAlbum());
    setOrDelete(tag, FieldKey.YEAR, track.getYear());
    if (track.isCoverChanged()) {
      // Only rewritten when edited, so untouched files keep their original artwork exactly as it was.
      tag.deleteArtworkField();
      if (track.getCover() != null) {
        tag.setField(createCover(track.getCover()));
      }
    }
    audioFile.commit();
    if (Track.extensionOf(track.getPath().getFileName().toString()).equals("wav")) {
      WavInfoEncoder.convertInfoToWindows1252(track.getPath());
    }

    Path newPath = track.getPath();
    if (track.isRenamed()) {
      // No REPLACE_EXISTING: fails instead of overwriting another file with the same name.
      newPath = Files.move(track.getPath(), track.getPath().resolveSibling(track.getFileName()));
    }
    track.markSaved(newPath);
  }

  private Track read(Path path) {
    try {
      Tag tag = AudioFileIO.read(path.toFile()).getTag();
      if (tag == null) {
        return new Track(path, "", "", "", null);
      }
      Artwork artwork = tag.getFirstArtwork();
      byte[] cover = artwork == null || artwork.isLinked() ? null : artwork.getBinaryData();
      return new Track(path, tag.getFirst(FieldKey.ARTIST), tag.getFirst(FieldKey.ALBUM), tag.getFirst(FieldKey.YEAR),
          cover);
    } catch (Exception e) {
      // Still list files whose tags can't be read, so they show up in the table.
      return new Track(path, "", "", "", null);
    }
  }

  private static Artwork createCover(byte[] image) {
    Artwork artwork = ArtworkFactory.getNew();
    artwork.setBinaryData(image);
    artwork.setMimeType(ImageFormats.getMimeTypeForBinarySignature(image));
    artwork.setPictureType(PictureTypes.DEFAULT_ID);
    // FLAC stores the image size alongside the picture.
    artwork.setImageFromData();
    return artwork;
  }

  private static void setOrDelete(Tag tag, FieldKey key, String value) throws FieldDataInvalidException {
    if (value == null || value.isBlank()) {
      tag.deleteField(key);
    } else {
      tag.setField(key, value.trim());
    }
  }
}
