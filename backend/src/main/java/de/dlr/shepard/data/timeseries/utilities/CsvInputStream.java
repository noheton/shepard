package de.dlr.shepard.data.timeseries.utilities;

import jakarta.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import lombok.NonNull;

public class CsvInputStream extends InputStream {

  @NonNull
  private final CsvLineProvider lineProvider;

  private String lineBuffer;
  private int lineBufferLen = 0;
  private int lineBufferPos = 0;

  public CsvInputStream(@NonNull CsvLineProvider lineProvider) {
    this.lineProvider = Objects.requireNonNull(lineProvider);
    try {
      readCSVLine();
    } catch (IOException e) {
      // Ignore, will be thrown again in read method
    }
  }

  @Override
  public int read() throws IOException {
    var remaining = checkBuffer();
    if (remaining <= 0) return -1;

    return lineBuffer.charAt(lineBufferPos++);
  }

  @Override
  public int read(@Nonnull byte[] b, int off, int len) throws IOException {
    var remaining = checkBuffer();
    if (remaining <= 0) return -1;

    var strLen = Math.min(len, remaining);
    System.arraycopy(lineBuffer.getBytes(), lineBufferPos, b, off, strLen);
    lineBufferPos += strLen;
    return strLen;
  }

  @Override
  public int available() throws IOException {
    return lineBufferLen - lineBufferPos;
  }

  private int checkBuffer() throws IOException {
    // Check whether a new CSV line must be generated
    if (lineBufferPos >= lineBufferLen || lineBufferLen <= 0) readCSVLine();

    if (lineBufferLen > 0 && lineBufferPos >= lineBufferLen) {
      // It should be impossible to reach this
      throw new IOException("Buffer overflow");
    }
    return lineBufferLen - lineBufferPos;
  }

  private void readCSVLine() throws IOException {
    lineBuffer = lineProvider.readCsvLine();
    lineBufferLen = lineBuffer.length();
    lineBufferPos = 0;
  }
}
