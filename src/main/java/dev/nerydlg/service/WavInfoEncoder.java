package dev.nerydlg.service;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

/**
 * jaudiotagger always writes the text of a WAV's RIFF INFO chunk as UTF-8, but most players (Sony Walkmans
 * included) read INFO text as Windows-1252, so "Joaquín" shows up as "JoaquÃ­n". This re-encodes the INFO
 * strings as Windows-1252 in place, padding with NULs, so the file layout stays exactly the same.
 */
final class WavInfoEncoder {

  private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

  private WavInfoEncoder() {
  }

  /** Must only be called right after jaudiotagger wrote the file, while the INFO text is still UTF-8. */
  static void convertInfoToWindows1252(Path wavFile) throws IOException {
    try (FileChannel channel = FileChannel.open(wavFile, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
      ByteBuffer header = read(channel, 0, 12);
      if (!fourCC(header, 0).equals("RIFF") || !fourCC(header, 8).equals("WAVE")) {
        return;
      }
      long position = 12;
      long fileSize = channel.size();
      while (position + 8 <= fileSize) {
        ByteBuffer chunkHeader = read(channel, position, 8);
        String id = fourCC(chunkHeader, 0);
        long size = Integer.toUnsignedLong(chunkHeader.getInt(4));
        if (id.equals("LIST") && size >= 4 && fourCC(read(channel, position + 8, 4), 0).equals("INFO")) {
          reencodeInfoEntries(channel, position + 12, (int) (size - 4));
          return;
        }
        position += 8 + size + (size & 1);
      }
    }
  }

  /** The INFO list is a sequence of entries: 4-char id, little-endian size, NUL-terminated text, padding. */
  private static void reencodeInfoEntries(FileChannel channel, long start, int length) throws IOException {
    ByteBuffer entries = read(channel, start, length);
    boolean changed = false;
    int position = 0;
    while (position + 8 <= length) {
      int size = entries.getInt(position + 4);
      int valueStart = position + 8;
      if (size < 0 || valueStart + size > length) {
        break;
      }
      byte[] value = Arrays.copyOfRange(entries.array(), valueStart, valueStart + size);
      byte[] encoded = toWindows1252(value);
      if (encoded != null) {
        // Windows-1252 never needs more bytes than UTF-8 for the same text, so it always fits.
        byte[] padded = Arrays.copyOf(encoded, size);
        entries.put(valueStart, padded);
        changed = true;
      }
      position = valueStart + size + (size & 1);
    }
    if (changed) {
      entries.position(0);
      while (entries.hasRemaining()) {
        channel.write(entries, start + entries.position());
      }
    }
  }

  /** Returns the value re-encoded as Windows-1252, or null if it needs no change (ASCII or not valid UTF-8). */
  private static byte[] toWindows1252(byte[] value) {
    int textLength = 0;
    while (textLength < value.length && value[textLength] != 0) {
      textLength++;
    }
    boolean ascii = true;
    for (int i = 0; i < textLength; i++) {
      if (value[i] < 0) {
        ascii = false;
        break;
      }
    }
    if (ascii) {
      return null;
    }
    CharsetDecoder strictUtf8 = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);
    try {
      CharBuffer text = strictUtf8.decode(ByteBuffer.wrap(value, 0, textLength));
      // Characters Windows-1252 can't represent become '?'.
      return text.toString().getBytes(WINDOWS_1252);
    } catch (CharacterCodingException e) {
      // Not UTF-8, so it was not written by jaudiotagger; leave it alone rather than garble it.
      return null;
    }
  }

  private static ByteBuffer read(FileChannel channel, long position, int length) throws IOException {
    ByteBuffer buffer = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN);
    while (buffer.hasRemaining()) {
      if (channel.read(buffer, position + buffer.position()) < 0) {
        throw new EOFException("Unexpected end of WAV file");
      }
    }
    return buffer;
  }

  private static String fourCC(ByteBuffer buffer, int offset) {
    return new String(buffer.array(), offset, 4, StandardCharsets.US_ASCII);
  }
}
