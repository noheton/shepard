package de.dlr.shepard.plugin.fileformat.svdx;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Pure-Java parser for the TwinCAT Scope Export Tool TSV/CSV format.
 *
 * <p>Reads the operator-generated sibling file of a {@code .svdx}: a
 * tab-separated text export produced by Beckhoff's TwinCAT Scope Export
 * Tool (see Infosys docs linked from {@code byte-layout-notes.md}).
 *
 * <p>The format is informed by — but does not depend on — the community
 * {@code pytcs} reader ({@link <a href="https://github.com/CagtayFabry/pytcs">github.com/CagtayFabry/pytcs</a>},
 * MIT). We keep the parser pure-Java (no third-party CSV library) for
 * two reasons: (1) the plugin module is standalone (no backend deps; see
 * the Jandex hang documented in {@code pom.xml}), and (2) the format is
 * stable, narrow, and trivially parseable with {@link BufferedReader}
 * + {@code split("\t", -1)} — pulling in OpenCSV would be overkill per
 * the {@code feedback_reuse_trusted_code.md} rule.
 *
 * <p>The format, observed on every campaign file (verified against
 * the 3 matched {@code .csv}/{@code .svdx} pairs in
 * {@code /mnt/pve/unas/dump/dataset/Punktschweißungen/} on 2026-06-02):
 *
 * <ol>
 *   <li>Lines 1-4: project-level metadata (key TAB value [TAB extra]):
 *       {@code Name}, {@code File}, {@code Starttime of export}
 *       (Windows FILETIME 100-ns ticks since 1601-01-01),
 *       {@code Endtime of export}.</li>
 *   <li>One blank line.</li>
 *   <li>~17 channel-header lines, each shaped as a repeating
 *       {@code <key>\t<value>} pair per channel. Known keys:
 *       {@code Name}, {@code SymbolComment}, {@code Data-Type},
 *       {@code SampleTime[ms]}, {@code VariableSize},
 *       {@code SymbolBased}, {@code IndexGroup}, {@code IndexOffset},
 *       {@code SymbolName} (fully qualified TwinCAT path),
 *       {@code NetID}, {@code Port}, {@code Offset}, {@code ScaleFactor},
 *       {@code BitMask}, {@code Unit}, {@code Unit ScaleFactor},
 *       {@code Unit Offset}.</li>
 *   <li>One blank line.</li>
 *   <li>Data rows: per channel, a {@code <sampleIndex>\t<value>}
 *       pair — same column count as the headers, so column 2k = time
 *       index, column 2k+1 = the channel's value at that index for k =
 *       0..N-1.</li>
 * </ol>
 *
 * <p>Locale: values use {@code ,} (comma) as decimal separator
 * (TwinCAT defaults to OS locale; the DLR corpus is German). The
 * parser normalises {@code ,} → {@code .} before {@link Double#parseDouble}.
 *
 * <p>Timestamp model: sample index → absolute timestamp = start FILETIME
 * + sampleIndex × SampleTime[ms]. FILETIME is in 100-ns ticks since
 * 1601-01-01 UTC; unix-ns = (filetime - {@link #FILETIME_EPOCH_DELTA_100NS}) * 100.
 * Verified against the 2023-03-20 19:03:23.992 CET wall-clock that the
 * CSV header advertises for the {@code 133238090039920000} export.
 *
 * <p>Channel data-type → Shepard {@code DataPointValueType} mapping:
 * IEC 61131-3 numeric types (INT16/INT32/UINT32/UINT64/REAL32/REAL64)
 * → numeric; {@code BIT} → boolean; everything else → string. The
 * parser does not validate this mapping — it preserves the original
 * scalar value and lets the ingestion layer coerce.
 */
public final class TcScopeCsvParser {

  /** Number of 100-ns ticks between 1601-01-01 (FILETIME epoch) and 1970-01-01 (Unix epoch). */
  public static final long FILETIME_EPOCH_DELTA_100NS = 116_444_736_000_000_000L;

  /** Maximum number of data rows we accept in one ingest call (~24h at 1ms). */
  public static final int MAX_ROWS = 100_000_000;

  /**
   * Convert a Windows FILETIME (100-ns ticks since 1601-01-01 UTC) into
   * a Unix-epoch nanosecond count. Public so the ingestion service can
   * also build {@code sampleIndex × sampleTimeMs} offsets in the same
   * unit.
   */
  public static long fileTimeToUnixNanos(long fileTime) {
    long delta = fileTime - FILETIME_EPOCH_DELTA_100NS;
    if (delta < 0) {
      throw new IllegalArgumentException("FILETIME before 1970 unix epoch: " + fileTime);
    }
    return delta * 100L;
  }

  /**
   * One parsed channel column with header metadata + values. Timestamps
   * are reconstructed externally via {@link #startTimeFileTime()} +
   * the channel's own {@link #sampleTimeMs()}.
   */
  public record Channel(
      String name,
      String symbolName,
      String dataType,
      int sampleTimeMs,
      String netId,
      String port,
      String symbolComment,
      String unit,
      List<Object> values
  ) {
    public Channel {
      values = values == null ? List.of() : Collections.unmodifiableList(values);
    }

    /** Index of the last non-null sample (inclusive). */
    public int sampleCount() {
      return values.size();
    }
  }

  /** Top-level parse result. */
  public record ParsedScopeCsv(
      Optional<String> projectName,
      Optional<String> sourceFilePath,
      long startTimeFileTime,
      long endTimeFileTime,
      List<Channel> channels
  ) {
    public ParsedScopeCsv {
      channels = channels == null ? List.of() : Collections.unmodifiableList(channels);
    }

    public int channelCount() {
      return channels.size();
    }

    public int maxRowCount() {
      int max = 0;
      for (Channel c : channels) max = Math.max(max, c.sampleCount());
      return max;
    }

    /** Absolute Unix-epoch nanoseconds for sample index {@code k} on a channel with the given sample-time. */
    public long sampleTimestampNs(int sampleIndex, int sampleTimeMs) {
      long startNs = fileTimeToUnixNanos(startTimeFileTime);
      // Avoid long overflow: 24h * 1ms = 86_400_000 ms; * 1e6 ns = 8.64e13; safe.
      return startNs + (long) sampleIndex * (long) sampleTimeMs * 1_000_000L;
    }
  }

  /** Thrown when the CSV cannot be parsed as a TwinCAT Scope Export. */
  public static final class CsvParseException extends RuntimeException {
    public CsvParseException(String msg) { super(msg); }
    public CsvParseException(String msg, Throwable cause) { super(msg, cause); }
  }

  private TcScopeCsvParser() {}

  /** Parse a TwinCAT Scope Export Tool CSV from a stream. UTF-8 expected. */
  public static ParsedScopeCsv parse(InputStream in) {
    if (in == null) throw new CsvParseException("input stream is null");
    try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
      return parseReader(r);
    } catch (IOException e) {
      throw new CsvParseException("I/O failure reading CSV: " + e.getMessage(), e);
    }
  }

  /** Visible for unit tests. */
  static ParsedScopeCsv parseReader(BufferedReader r) throws IOException {
    // ───────── project metadata block (header lines 1..N until first blank) ─────────
    String projectName = null;
    String sourceFilePath = null;
    long startFt = 0L;
    long endFt = 0L;
    String line;
    while ((line = r.readLine()) != null) {
      if (line.isBlank()) break;
      String[] parts = line.split("\t", -1);
      if (parts.length < 2) continue;
      String key = parts[0].trim();
      String val = parts[1];
      switch (key) {
        case "Name" -> projectName = stripTrailing(val);
        case "File" -> sourceFilePath = stripTrailing(val);
        case "Starttime of export" -> {
          try { startFt = Long.parseLong(stripTrailing(val).trim()); }
          catch (NumberFormatException ignored) { /* leave 0 — emit warning later */ }
        }
        case "Endtime of export" -> {
          try { endFt = Long.parseLong(stripTrailing(val).trim()); }
          catch (NumberFormatException ignored) { /* leave 0 */ }
        }
        default -> { /* unknown — ignore */ }
      }
    }
    if (startFt == 0L) {
      throw new CsvParseException("missing 'Starttime of export' or non-numeric value");
    }

    // ───────── channel header block ─────────
    // Each header line: <key>\t<value>\t<key>\t<value>... repeated per channel.
    // We collect into a key → List<String> map until the next blank line.
    Map<String, List<String>> hdr = new LinkedHashMap<>();
    int channelCount = -1;
    while ((line = r.readLine()) != null) {
      if (line.isBlank()) break;
      String[] parts = line.split("\t", -1);
      String key = parts[0].trim();
      List<String> vals = new ArrayList<>(parts.length / 2);
      for (int i = 1; i < parts.length; i += 2) {
        vals.add(parts[i]);
      }
      if (channelCount < 0) {
        channelCount = vals.size();
      } else if (vals.size() != channelCount) {
        // Tolerate jagged tail (some keys end short) — pad to channelCount.
        while (vals.size() < channelCount) vals.add("");
      }
      hdr.put(key, vals);
    }
    if (channelCount <= 0) {
      throw new CsvParseException("no channel header columns detected");
    }

    List<String> names = hdr.getOrDefault("Name", padded(channelCount));
    List<String> symbolNames = hdr.getOrDefault("SymbolName", padded(channelCount));
    List<String> dataTypes = hdr.getOrDefault("Data-Type", padded(channelCount));
    List<String> sampleTimes = hdr.getOrDefault("SampleTime[ms]", padded(channelCount));
    List<String> netIds = hdr.getOrDefault("NetID", padded(channelCount));
    List<String> ports = hdr.getOrDefault("Port", padded(channelCount));
    List<String> symbolComments = hdr.getOrDefault("SymbolComment", padded(channelCount));
    List<String> units = hdr.getOrDefault("Unit", padded(channelCount));

    // ───────── data block ─────────
    // Each row: <idx>\t<val>\t<idx>\t<val>... one (idx, val) pair per channel.
    // We collect the value column per channel; the idx column is redundant
    // (same monotonic index across all channels in the campaign we audited)
    // so we drop it. If the format ever changes to per-channel
    // independent sample indices, switch to indexed maps here.
    @SuppressWarnings("unchecked")
    List<Object>[] values = new List[channelCount];
    for (int i = 0; i < channelCount; i++) values[i] = new ArrayList<>(1024);

    int row = 0;
    while ((line = r.readLine()) != null) {
      if (line.isBlank()) continue;
      if (row >= MAX_ROWS) {
        throw new CsvParseException("CSV exceeds MAX_ROWS=" + MAX_ROWS);
      }
      String[] parts = line.split("\t", -1);
      // Expected 2 * channelCount columns; tolerate short tail.
      for (int c = 0; c < channelCount; c++) {
        int vCol = c * 2 + 1;
        if (vCol >= parts.length) {
          values[c].add(null);
          continue;
        }
        String raw = parts[vCol];
        values[c].add(parseValue(raw, dataTypes.get(c)));
      }
      row++;
    }

    List<TcScopeCsvParser.Channel> channels = new ArrayList<>(channelCount);
    for (int c = 0; c < channelCount; c++) {
      int sampleTime = parseIntOrDefault(sampleTimes.get(c), 1);
      channels.add(new TcScopeCsvParser.Channel(
          safeGet(names, c),
          safeGet(symbolNames, c),
          safeGet(dataTypes, c),
          sampleTime,
          safeGet(netIds, c),
          safeGet(ports, c),
          safeGet(symbolComments, c),
          safeGet(units, c),
          values[c]
      ));
    }

    return new ParsedScopeCsv(
        Optional.ofNullable(projectName).filter(s -> !s.isBlank()),
        Optional.ofNullable(sourceFilePath).filter(s -> !s.isBlank()),
        startFt,
        endFt,
        channels
    );
  }

  // ---------------------------------------------------------------- helpers

  /** Parse one CSV cell into a typed scalar. {@code null} for empty cells. */
  static Object parseValue(String raw, String dataType) {
    if (raw == null || raw.isEmpty()) return null;
    String trimmed = raw.trim();
    if (trimmed.isEmpty()) return null;
    String dt = dataType == null ? "" : dataType.trim().toUpperCase();
    try {
      switch (dt) {
        case "BIT", "BOOL":
          if ("0".equals(trimmed)) return Boolean.FALSE;
          if ("1".equals(trimmed)) return Boolean.TRUE;
          if ("TRUE".equalsIgnoreCase(trimmed) || "FALSE".equalsIgnoreCase(trimmed)) {
            return Boolean.valueOf(trimmed);
          }
          return trimmed;
        case "REAL32", "REAL64", "LREAL", "FLOAT", "DOUBLE":
          return Double.parseDouble(trimmed.replace(',', '.'));
        case "INT8", "INT16", "INT32", "UINT8", "UINT16", "UINT32",
             "SINT", "USINT", "INT", "UINT", "DINT", "UDINT":
          // No decimal expected, but be lenient — strip a stray comma.
          return Long.parseLong(stripDecimalPart(trimmed));
        case "INT64", "UINT64", "LINT", "ULINT":
          return Long.parseLong(stripDecimalPart(trimmed));
        default:
          // Fallback: try number-with-comma, then string.
          String numeric = trimmed.replace(',', '.');
          if (looksNumeric(numeric)) return Double.parseDouble(numeric);
          return trimmed;
      }
    } catch (NumberFormatException nfe) {
      // Malformed cell — preserve as a string so a downstream
      // sanity check can WARN-and-skip without aborting the row.
      return trimmed;
    }
  }

  private static String stripDecimalPart(String s) {
    int comma = s.indexOf(',');
    int dot = s.indexOf('.');
    int cut = -1;
    if (comma >= 0 && dot >= 0) cut = Math.min(comma, dot);
    else if (comma >= 0) cut = comma;
    else if (dot >= 0) cut = dot;
    return cut < 0 ? s : s.substring(0, cut);
  }

  private static boolean looksNumeric(String s) {
    if (s.isEmpty()) return false;
    int start = (s.charAt(0) == '-' || s.charAt(0) == '+') ? 1 : 0;
    if (start >= s.length()) return false;
    for (int i = start; i < s.length(); i++) {
      char c = s.charAt(i);
      if (!(Character.isDigit(c) || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-')) {
        return false;
      }
    }
    return true;
  }

  private static String stripTrailing(String s) {
    if (s == null) return null;
    // strip trailing whitespace + tabs but preserve internal content
    int end = s.length();
    while (end > 0 && (s.charAt(end - 1) == '\t' || s.charAt(end - 1) == ' ')) end--;
    return s.substring(0, end);
  }

  private static int parseIntOrDefault(String s, int def) {
    if (s == null) return def;
    try { return Integer.parseInt(s.trim()); }
    catch (NumberFormatException nfe) { return def; }
  }

  private static String safeGet(List<String> xs, int i) {
    return (xs != null && i >= 0 && i < xs.size()) ? xs.get(i) : "";
  }

  private static List<String> padded(int n) {
    List<String> xs = new ArrayList<>(n);
    for (int i = 0; i < n; i++) xs.add("");
    return xs;
  }
}
