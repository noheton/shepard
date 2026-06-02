package de.dlr.shepard.common.util;

import jakarta.ws.rs.core.StreamingOutput;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * MFFD-VIDEOREF-SCALE-1 — shared single-range parsing + streaming helpers
 * used by all `/v2/` endpoints that ship raw bytes (FR1b files, VID1a
 * video, future ImageBundle frames).
 *
 * <p>Single-range only (RFC 7233 §3.1 simple form). Multi-range
 * comma-separated specifiers and suffix-range ({@code bytes=-N}) are
 * declined — they account for essentially zero traffic from modern
 * HTML5 video / curl / Postman clients, and supporting them would
 * complicate the `MultipartByteRange` encoding path with no operator
 * payoff.
 *
 * <p>Why a shared util now (and not earlier): the original
 * {@code FileReferenceV2Rest.parseRange} carried "FR1b" comments
 * because it was the first range-capable v2 endpoint. The pattern
 * was always going to be reused — VID1a's {@code /download} needs it
 * for HTML5 {@code <video>} scrubbing on long MP4s, and the future
 * thermography / pointcloud download paths will too. The duplication
 * was a low-cost call at one site; at two it earns its own home.
 */
public final class HttpRangeUtil {

  private HttpRangeUtil() {}

  /**
   * Parse a single {@code "bytes=START-END"} range header against a
   * known total content length.
   *
   * <p>Returns {@code null} for any unsupported / unsatisfiable shape:
   * multi-range, suffix-range, malformed numbers, start &lt; 0, start
   * ≥ total, end &lt; start.
   *
   * <p>If {@code END} is omitted ({@code "bytes=START-"}), the end is
   * clamped to {@code total - 1}. If {@code END} exceeds {@code total - 1},
   * it is also clamped — the request is satisfied with the bytes that
   * actually exist (RFC 7233 §2.1 "Range" semantics).
   *
   * @param header raw {@code Range} header value, e.g. {@code "bytes=0-1023"}.
   * @param total  total byte length of the resource (must be positive
   *               for any range to be satisfiable).
   * @return {@code [start, end]} (inclusive) or {@code null} if
   *   unparseable / unsatisfiable.
   */
  public static long[] parseRange(String header, long total) {
    if (header == null) return null;
    if (total <= 0) return null;
    String trimmed = header.trim();
    if (!trimmed.startsWith("bytes=")) return null;
    String spec = trimmed.substring("bytes=".length());
    // Multi-range (comma-separated) — refuse.
    if (spec.contains(",")) return null;
    int dash = spec.indexOf('-');
    if (dash < 0) return null;
    String startStr = spec.substring(0, dash).trim();
    String endStr = spec.substring(dash + 1).trim();
    if (startStr.isEmpty()) {
      // Suffix-range "bytes=-N" — refuse.
      return null;
    }
    long start;
    long end;
    try {
      start = Long.parseLong(startStr);
      end = endStr.isEmpty() ? total - 1 : Long.parseLong(endStr);
    } catch (NumberFormatException nfe) {
      return null;
    }
    if (start < 0 || start >= total) return null;
    if (end >= total) end = total - 1;
    if (end < start) return null;
    return new long[] { start, end };
  }

  /**
   * Wrap an {@link InputStream} as a {@link StreamingOutput} that skips
   * to {@code start} and emits exactly {@code length} bytes (or fewer
   * if the source ends early). Closes the input stream when done.
   *
   * <p>Skip uses {@link InputStream#skip} in a loop; many implementations
   * (GridFS, S3) honour the contract well, but for any that don't the
   * loop bails after the first zero-progress call to avoid an infinite
   * spin.
   *
   * @param stream source bytes (consumed and closed by the returned
   *               {@link StreamingOutput})
   * @param start  byte offset to start emitting from (inclusive)
   * @param length number of bytes to emit
   * @return a {@link StreamingOutput} suitable for a 206 Partial Content
   *   response entity
   */
  public static StreamingOutput sliceStream(InputStream stream, long start, long length) {
    return (OutputStream out) -> {
      try (InputStream in = stream) {
        long skipped = 0;
        while (skipped < start) {
          long s = in.skip(start - skipped);
          if (s <= 0) break;
          skipped += s;
        }
        byte[] buf = new byte[8192];
        long remaining = length;
        while (remaining > 0) {
          int toRead = (int) Math.min(buf.length, remaining);
          int n = in.read(buf, 0, toRead);
          if (n < 0) break;
          out.write(buf, 0, n);
          remaining -= n;
        }
      }
    };
  }
}
