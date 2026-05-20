package de.dlr.shepard.context.references.videostreamreference.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * VID1a — runs {@code ffprobe} on a video payload and extracts metadata.
 *
 * <p>Accepts an {@link InputStream}, writes it to a temp file, shells out
 * to {@code ffprobe -v quiet -print_format json -show_format -show_streams},
 * parses the JSON output, and returns a {@link VideoProbeResult}.
 *
 * <p><strong>Graceful degradation.</strong> If {@code ffprobe} is not on
 * {@code PATH}, if the process fails to start, or if a particular field
 * is missing from the output, the corresponding field in the result is
 * {@code null} — the upload succeeds regardless. A WARN-level log line
 * is emitted for each failure so operators can diagnose missing tooling.
 *
 * <p>The probe logic is split into two methods so unit tests can exercise
 * the JSON-parsing path without invoking a real OS process:
 * <ul>
 *   <li>{@link #probe(InputStream, String)} — public entry point; writes the
 *       temp file and calls the process.</li>
 *   <li>{@link #parseProbeResult(String)} — package-private; parses the
 *       ffprobe JSON string and returns the result record. Testable in
 *       isolation with fixed JSON strings.</li>
 * </ul>
 */
@ApplicationScoped
public class VideoProbeService {

  /** Timeout for ffprobe execution. */
  private static final long FFPROBE_TIMEOUT_SECONDS = 60L;

  @Inject
  ObjectMapper objectMapper;

  /**
   * Run ffprobe on the supplied input stream.
   *
   * <p>Writes {@code payload} to a temp file, invokes ffprobe, delegates
   * to {@link #parseProbeResult(String)} for the JSON parse, and deletes
   * the temp file in a {@code finally} block.
   *
   * @param payload   the video bytes; the stream is NOT closed by this method.
   * @param mimeType  hint for the log; not passed to ffprobe.
   * @return probe result with whatever fields were extractable; never null.
   */
  public VideoProbeResult probe(InputStream payload, String mimeType) {
    Path tmp = null;
    try {
      tmp = Files.createTempFile("shepard-vid-probe-", ".tmp");
      Files.copy(payload, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      return runFfprobe(tmp);
    } catch (IOException ex) {
      Log.warnf("VID1a probe: failed to write temp file for %s — %s", mimeType, ex.getMessage());
      return VideoProbeResult.empty();
    } finally {
      if (tmp != null) {
        try {
          Files.deleteIfExists(tmp);
        } catch (IOException ignored) {
          Log.warnf("VID1a probe: could not delete temp file %s", tmp);
        }
      }
    }
  }

  /**
   * Run ffprobe against a temp file path and parse the output.
   *
   * @param videoFile path to the file to probe
   * @return probe result; never null
   */
  private VideoProbeResult runFfprobe(Path videoFile) {
    List<String> cmd = List.of(
      "ffprobe",
      "-v", "quiet",
      "-print_format", "json",
      "-show_format",
      "-show_streams",
      videoFile.toAbsolutePath().toString()
    );

    Process process;
    try {
      process = new ProcessBuilder(cmd)
        .redirectErrorStream(false)
        .start();
    } catch (IOException ex) {
      Log.warnf("VID1a probe: ffprobe not available or failed to start — %s", ex.getMessage());
      return VideoProbeResult.empty();
    }

    String json;
    try {
      json = new String(process.getInputStream().readAllBytes());
      boolean exited = process.waitFor(FFPROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      if (!exited) {
        process.destroyForcibly();
        Log.warn("VID1a probe: ffprobe timed out");
        return VideoProbeResult.empty();
      }
      if (process.exitValue() != 0) {
        String stderr = new String(process.getErrorStream().readAllBytes());
        Log.warnf("VID1a probe: ffprobe exited with code %d — %s", process.exitValue(), stderr);
        return VideoProbeResult.empty();
      }
    } catch (IOException | InterruptedException ex) {
      Thread.currentThread().interrupt();
      Log.warnf("VID1a probe: error reading ffprobe output — %s", ex.getMessage());
      return VideoProbeResult.empty();
    }

    return parseProbeResult(json);
  }

  /**
   * Parse an ffprobe JSON string into a {@link VideoProbeResult}.
   *
   * <p>Package-private for unit testing with fixed JSON strings. Any field
   * that is absent or unparseable in the JSON silently becomes {@code null}
   * in the result — a single bad field must not abort the entire probe.
   *
   * @param json the raw stdout of {@code ffprobe -print_format json}
   * @return parsed result; never null
   */
  VideoProbeResult parseProbeResult(String json) {
    if (json == null || json.isBlank()) {
      return VideoProbeResult.empty();
    }

    JsonNode root;
    try {
      root = objectMapper.readTree(json);
    } catch (IOException ex) {
      Log.warnf("VID1a probe: could not parse ffprobe JSON — %s", ex.getMessage());
      return VideoProbeResult.empty();
    }

    // ── format fields ────────────────────────────────────────────────────────
    Double durationSeconds = null;
    Long fileSizeBytes = null;
    Long wallClockTimestamp = null;

    JsonNode format = root.path("format");
    if (!format.isMissingNode()) {
      durationSeconds = parseDouble(format, "duration");
      fileSizeBytes = parseLong(format, "size");
      JsonNode tags = format.path("tags");
      if (!tags.isMissingNode()) {
        wallClockTimestamp = parseCreationTime(tags.path("creation_time").asText(null));
      }
    }

    // ── stream fields ─────────────────────────────────────────────────────────
    Integer width = null;
    Integer height = null;
    Double frameRate = null;
    String videoCodec = null;
    String audioCodec = null;

    JsonNode streams = root.path("streams");
    if (streams.isArray()) {
      for (JsonNode stream : streams) {
        String codecType = stream.path("codec_type").asText(null);
        if ("video".equals(codecType) && videoCodec == null) {
          videoCodec = stream.path("codec_name").asText(null);
          if (videoCodec != null && videoCodec.isEmpty()) videoCodec = null;
          width = parseInteger(stream, "width");
          height = parseInteger(stream, "height");
          frameRate = parseFrameRate(stream.path("r_frame_rate").asText(null));
        } else if ("audio".equals(codecType) && audioCodec == null) {
          audioCodec = stream.path("codec_name").asText(null);
          if (audioCodec != null && audioCodec.isEmpty()) audioCodec = null;
        }
      }
    }

    return new VideoProbeResult(
      durationSeconds, fileSizeBytes, width, height,
      frameRate, videoCodec, audioCodec, wallClockTimestamp
    );
  }

  // ── private helpers ───────────────────────────────────────────────────────

  private Double parseDouble(JsonNode node, String field) {
    JsonNode v = node.path(field);
    if (v.isMissingNode() || v.isNull()) return null;
    try {
      return Double.parseDouble(v.asText());
    } catch (NumberFormatException ex) {
      Log.warnf("VID1a probe: could not parse double field '%s': %s", field, v.asText());
      return null;
    }
  }

  private Long parseLong(JsonNode node, String field) {
    JsonNode v = node.path(field);
    if (v.isMissingNode() || v.isNull()) return null;
    try {
      return Long.parseLong(v.asText());
    } catch (NumberFormatException ex) {
      Log.warnf("VID1a probe: could not parse long field '%s': %s", field, v.asText());
      return null;
    }
  }

  private Integer parseInteger(JsonNode node, String field) {
    JsonNode v = node.path(field);
    if (v.isMissingNode() || v.isNull()) return null;
    if (v.isInt()) return v.intValue();
    try {
      return Integer.parseInt(v.asText());
    } catch (NumberFormatException ex) {
      Log.warnf("VID1a probe: could not parse integer field '%s': %s", field, v.asText());
      return null;
    }
  }

  /**
   * Parse {@code r_frame_rate} which ffprobe returns as a fraction string
   * like {@code "30/1"} or {@code "30000/1001"}.
   *
   * @param rateStr the raw string from ffprobe, or {@code null}
   * @return frames per second, or {@code null} if unparseable
   */
  Double parseFrameRate(String rateStr) {
    if (rateStr == null || rateStr.isBlank()) return null;
    int slash = rateStr.indexOf('/');
    if (slash < 0) {
      // Some containers emit a plain decimal. Try that.
      try {
        return Double.parseDouble(rateStr);
      } catch (NumberFormatException ex) {
        Log.warnf("VID1a probe: could not parse r_frame_rate '%s'", rateStr);
        return null;
      }
    }
    try {
      double num = Double.parseDouble(rateStr.substring(0, slash).trim());
      double den = Double.parseDouble(rateStr.substring(slash + 1).trim());
      if (den == 0.0) {
        Log.warnf("VID1a probe: r_frame_rate denominator is zero: '%s'", rateStr);
        return null;
      }
      return num / den;
    } catch (NumberFormatException ex) {
      Log.warnf("VID1a probe: could not parse r_frame_rate '%s'", rateStr);
      return null;
    }
  }

  /**
   * Parse an ISO-8601 creation_time tag from ffprobe (e.g.
   * {@code "2024-03-15T10:30:00.000000Z"}) into nanoseconds since the Unix
   * epoch (UTC).
   *
   * @param raw the raw string from ffprobe tags, or {@code null}
   * @return nanoseconds since epoch, or {@code null} if absent/unparseable
   */
  Long parseCreationTime(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
      Instant instant = Instant.parse(raw);
      // epochSecond * 1_000_000_000 + nano-of-second
      return Math.addExact(
        Math.multiplyExact(instant.getEpochSecond(), 1_000_000_000L),
        instant.getNano()
      );
    } catch (DateTimeParseException | ArithmeticException ex) {
      Log.warnf("VID1a probe: could not parse creation_time '%s' — %s", raw, ex.getMessage());
      return null;
    }
  }
}
