package de.dlr.shepard.common.exceptions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Test-only helper that attaches to the {@link ShepardExceptionMapper}
 * category logger and records {@link Level#FINE} (DEBUG) and
 * {@link Level#SEVERE} (ERROR) records so tests can assert what was logged
 * at which level.
 *
 * <p>Quarkus' {@link io.quarkus.logging.Log} routes through JBoss Logging,
 * which in turn delegates to {@link Logger} when the JBoss
 * {@code LogManager} is the system log manager (configured for the maven
 * surefire JVM in {@code backend/pom.xml}). That makes a stock JUL
 * {@link Handler} sufficient for capture.
 */
final class ExceptionMapperLogCapture extends Handler {

  private static final ExceptionMapperLogCapture INSTANCE = new ExceptionMapperLogCapture();
  private static volatile boolean attached = false;

  private final List<LogRecord> records = new CopyOnWriteArrayList<>();

  private ExceptionMapperLogCapture() {
    setLevel(Level.ALL);
  }

  static synchronized void attach() {
    if (!attached) {
      Logger logger = Logger.getLogger(ShepardExceptionMapper.class.getName());
      logger.addHandler(INSTANCE);
      logger.setLevel(Level.ALL);
      attached = true;
    }
  }

  static void reset() {
    INSTANCE.records.clear();
  }

  /** Messages emitted at SEVERE (== Quarkus / JBoss "ERROR") level. */
  static List<String> errorMessages() {
    List<String> out = new ArrayList<>();
    for (LogRecord r : INSTANCE.records) {
      if (r.getLevel().intValue() >= Level.SEVERE.intValue()) {
        out.add(formatRecord(r));
      }
    }
    return out;
  }

  /**
   * The {@code thrown}-side text of records emitted at FINE / FINER / FINEST
   * (== Quarkus / JBoss "DEBUG" / "TRACE") level. We expose this separately
   * because the leaky exception's {@code getMessage()} ends up on the
   * {@link Throwable} attached to the record, not in the format string.
   */
  static List<String> debugThrownMessages() {
    List<String> out = new ArrayList<>();
    for (LogRecord r : INSTANCE.records) {
      if (r.getLevel().intValue() < Level.INFO.intValue() && r.getThrown() != null) {
        out.add(r.getThrown().getMessage());
      }
    }
    return out;
  }

  private static String formatRecord(LogRecord record) {
    String msg = record.getMessage();
    Object[] params = record.getParameters();
    if (msg == null) return "";
    if (params == null || params.length == 0) return msg;
    try {
      return String.format(msg, params);
    } catch (Exception e) {
      return msg + " " + java.util.Arrays.toString(params);
    }
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
