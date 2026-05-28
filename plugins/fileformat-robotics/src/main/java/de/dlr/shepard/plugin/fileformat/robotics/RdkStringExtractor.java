package de.dlr.shepard.plugin.fileformat.robotics;

import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Decompress a RoboDK {@code .rdk} station file and walk its
 * length-prefixed UTF-16LE string records.
 *
 * <p><b>File format</b> (empirically confirmed against {@code MFZ.rdk},
 * 12.1 MB → 52.8 MB inflated):
 *
 * <ul>
 *   <li>Bytes 0–3: 4-byte custom magic ({@code 03 25 10 A5} on
 *       {@code MFZ.rdk}; treated opaquely — we skip them).</li>
 *   <li>Bytes 4..end: zlib (Deflate) stream.</li>
 *   <li>Decompressed payload: little-endian binary with embedded text
 *       records. Each text record is
 *       {@code [uint32 LE byte-length][UTF-16LE bytes]} — the length is
 *       in <b>bytes</b>, not characters, and must be even.</li>
 * </ul>
 *
 * <p><b>Walk algorithm.</b> Linear scan: at each position, read the
 * candidate uint32 length; if it is even, within
 * {@code [2 .. MAX_RECORD_BYTES]}, and the candidate {@code length}
 * bytes decode as printable UTF-16LE (each codepoint in
 * {@code [0x20, 0x7E]}), emit a record and advance past it; otherwise
 * advance one byte. This is the same pattern {@code strings(1)} uses,
 * but constrained to the format's actual record shape — hits jump 754K
 * (raw printable-ASCII runs) down to ~72 meaningful records on
 * {@code MFZ.rdk}, none of them spurious.
 *
 * <p>The walker is best-effort: zlib errors return an empty list, never
 * throw.
 */
final class RdkStringExtractor {

    /** Hard cap on record length to avoid false matches against random
     *  binary blobs (texture / CAD data). 2 KiB covers every observed
     *  string in {@code MFZ.rdk} including the long
     *  {@code D:/git/RoboticsAPI_common/...} CAD paths. */
    static final int MAX_RECORD_BYTES = 2048;

    /** Number of header bytes before the zlib stream (the RoboDK
     *  4-byte custom magic). */
    static final int HEADER_BYTES = 4;

    /** Initial inflate output buffer size (1 MiB). The buffer grows on
     *  demand; we don't know the inflated size in advance, but the
     *  amortised cost of doubling is negligible against I/O. */
    private static final int INFLATE_CHUNK = 1 << 20;

    private RdkStringExtractor() { /* utility */ }

    /**
     * Inflate the payload and walk its string records.
     *
     * @param fileBytes the complete {@code .rdk} bytes (header + zlib stream)
     * @return list of decoded strings in file order; empty on any error
     */
    static List<String> extract(byte[] fileBytes) {
        if (fileBytes == null || fileBytes.length <= HEADER_BYTES) {
            return List.of();
        }
        byte[] inflated = inflate(fileBytes, HEADER_BYTES);
        if (inflated.length == 0) {
            return List.of();
        }
        return walkStrings(inflated);
    }

    /**
     * Decompress a zlib stream located at {@code offset} into
     * {@code src}. Returns an empty array on any decompression error
     * (corrupt file, non-zlib payload).
     */
    private static byte[] inflate(byte[] src, int offset) {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(src, offset, src.length - offset);
            byte[] buffer = new byte[INFLATE_CHUNK];
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(INFLATE_CHUNK);
            while (!inflater.finished()) {
                int n = inflater.inflate(buffer);
                if (n == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) {
                        break; // truncated / dictionary-required: stop
                    }
                }
                if (n > 0) {
                    out.write(buffer, 0, n);
                }
            }
            return out.toByteArray();
        } catch (DataFormatException ignored) {
            return new byte[0];
        } finally {
            inflater.end();
        }
    }

    /**
     * Walk the inflated payload looking for
     * {@code [uint32 LE byte-length][UTF-16LE bytes]} records.
     */
    private static List<String> walkStrings(byte[] data) {
        List<String> out = new ArrayList<>(128);
        int n = data.length;
        int i = 0;
        while (i + 4 < n) {
            int length =
                    (data[i] & 0xFF)
                            | (data[i + 1] & 0xFF) << 8
                            | (data[i + 2] & 0xFF) << 16
                            | (data[i + 3] & 0xFF) << 24;
            // Constraints: positive, even, in range, fits in remaining bytes.
            if (length >= 2 && length <= MAX_RECORD_BYTES && (length & 1) == 0
                    && i + 4 + length <= n) {
                String s = tryDecodePrintableUtf16Le(data, i + 4, length);
                if (s != null) {
                    out.add(s);
                    i += 4 + length;
                    continue;
                }
            }
            i++;
        }
        return out;
    }

    /**
     * Decode the slice {@code data[off..off+length]} as UTF-16LE and
     * return it only if every codepoint is printable ASCII (in
     * {@code [0x20, 0x7E]}). Otherwise return {@code null}.
     *
     * <p>The printable-only constraint rules out random binary blobs
     * (texture / pose-matrix data) that happen to have a plausible
     * length prefix. The same constraint discards records containing
     * embedded NULs (which {@code MFZ.rdk}'s string-table records
     * never have — they are zero-terminator-free length-prefix strings).
     */
    private static String tryDecodePrintableUtf16Le(byte[] data, int off, int length) {
        // Bound check already done by caller.
        StringBuilder sb = new StringBuilder(length >>> 1);
        for (int k = 0; k < length; k += 2) {
            int lo = data[off + k] & 0xFF;
            int hi = data[off + k + 1] & 0xFF;
            int cp = (hi << 8) | lo;
            if (cp < 0x20 || cp > 0x7E) {
                return null;
            }
            sb.append((char) cp);
        }
        return sb.toString();
    }
}
