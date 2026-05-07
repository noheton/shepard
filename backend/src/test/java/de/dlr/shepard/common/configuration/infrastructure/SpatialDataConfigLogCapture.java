package de.dlr.shepard.common.configuration.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Test-only helper that attaches to the {@link SpatialDataConfig} category
 * logger and records {@link Level#WARNING} messages so tests can assert on
 * their presence or absence.
 */
final class SpatialDataConfigLogCapture extends Handler {

  private static final SpatialDataConfigLogCapture INSTANCE = new SpatialDataConfigLogCapture();
  private static volatile boolean attached = false;

  private final List<LogRecord> records = new CopyOnWriteArrayList<>();

  private SpatialDataConfigLogCapture() {
    setLevel(Level.ALL);
  }

  static synchronized void attach() {
    if (!attached) {
      Logger logger = Logger.getLogger(SpatialDataConfig.class.getName());
      logger.addHandler(INSTANCE);
      logger.setLevel(Level.ALL);
      attached = true;
    }
  }

  static void reset() {
    INSTANCE.records.clear();
  }

  static List<String> warnings() {
    List<String> out = new ArrayList<>();
    for (LogRecord r : INSTANCE.records) {
      if (r.getLevel().intValue() >= Level.WARNING.intValue()) {
        out.add(r.getMessage());
      }
    }
    return out;
  }

  @Override
  public void publish(LogRecord logRecord) {
    records.add(logRecord);
  }

  @Override
  public void flush() {}

  @Override
  public void close() throws SecurityException {}
}
