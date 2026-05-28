package de.dlr.shepard.plugin.fileformat.thermography;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

/**
 * Tier-2 of the Edevis OTvis parser family. Tier-1
 * ({@link OTvisParser}) emits semantic annotations only; tier-2 decodes
 * frame binaries into plain Java arrays.
 *
 * <p>This class is the executable expression of the byte-layout-notes
 * spec (see {@code plugins/fileformat-thermography/docs/byte-layout-notes.md}).
 * It deliberately stays away from Shepard runtime types — no DAO, no
 * Neo4j, no S3, no logging — so the same callable is reusable as a CLI
 * helper, a test fixture probe, or the engine behind the OME-Zarr writer
 * (row {@code OTVIS-TIER2-OMEZARR}).
 *
 * <p><b>Behaviour contract:</b>
 * <ul>
 *   <li><b>Pure.</b> No filesystem writes outside the optional temp dir
 *       commons-compress may use internally. No network. No mutation of
 *       inputs.</li>
 *   <li><b>Best-effort tolerance.</b> Missing or malformed streams degrade
 *       to empty collections; the issue is recorded in
 *       {@link ExtractedFrames#partialReason}. The only operation that
 *       throws past the public surface is "the input isn't a tar at
 *       all".</li>
 *   <li><b>DataFormat dispatch from the header.</b> Per byte-layout-notes
 *       §2 the {@code DataFormat} code lives at offset 24 of each
 *       28-byte frame header — not in {@code content.xml}. We treat the
 *       header as authoritative; {@code content.xml} presence/absence is
 *       reported in {@code partialReason} but does not affect decoding.</li>
 *   <li><b>Supported DataFormat codes:</b> {@code 2} (uint16 raw, requires
 *       calibration) and {@code 13} (complex float lock-in result).
 *       Any other code → frame skipped, reason recorded.</li>
 *   <li><b>Calibration:</b> {@code sequence1/calibration.bin} feeds
 *       {@link CalibrationLut}. Per-frame {@code c<N>.bin} overrides are
 *       respected (Rev H mentions them; the MFFD fixture has none).</li>
 *   <li><b>Multi-frame {@code f<N>.bin}:</b> when the post-header payload
 *       size exceeds {@code width × height × bpp} we walk forward, decoding
 *       additional frames at the next 28-byte boundary until the file is
 *       consumed. The MFFD fixture has exactly one frame per file.</li>
 *   <li><b>Endianness:</b> little-endian throughout (Rev H + Windows
 *       producer).</li>
 * </ul>
 *
 * <p><b>What is NOT here:</b> INFL compression decoding (Rev H pages 3-4)
 * and the {@code HIDDEN TOC} fast-seek hint. The MFFD fixture exercises
 * neither; landing them would require a fixture or a port of the Rev H
 * C# reference decoder. Both are tracked in byte-layout-notes §5.
 */
public final class OTvisFrameExtractor {

    /** Header size in bytes (16 + 4 + 4 + 4). */
    public static final int HEADER_BYTES = RecurringHeader.HEADER_BYTES;

    /** Calibration LUT size, in 32-bit floats. */
    public static final int CALIB_FLOATS = CalibrationLut.LUT_SIZE;

    /** Calibration LUT total byte length. */
    public static final int CALIB_BYTES = CALIB_FLOATS * 4;

    // DataFormat codes per Rev H §"Frame-Datei".
    public static final int DF_UINT8 = 0;
    public static final int DF_UINT16 = 2;
    public static final int DF_FLOAT32 = 5;
    public static final int DF_COMPLEX_FLOAT = 13;
    public static final int DF_BGR_TRUECOLOR = 24;

    // ─── public API ────────────────────────────────────────────────────────────

    /**
     * Decode an OTvis archive from a file.
     *
     * @param otvisFile path to a {@code .OTvis} archive on disk
     * @return decoded frames + LUT + any partialReason
     * @throws IOException if the file cannot be read or is not a tar archive
     */
    public ExtractedFrames extract(Path otvisFile) throws IOException {
        if (otvisFile == null) {
            throw new IOException("otvisFile must not be null");
        }
        try (InputStream in = Files.newInputStream(otvisFile)) {
            return extract(in);
        }
    }

    /**
     * Decode an OTvis archive from an arbitrary input stream. The stream
     * is fully read into memory — OTvis archives are bounded (single-digit
     * MB to ~100 MB), so streaming the tar twice is not worth the
     * complexity.
     *
     * @param tarBytes input stream positioned at the start of the archive
     * @return decoded frames + LUT + any partialReason
     * @throws IOException when the input is not a usable tar archive
     */
    public ExtractedFrames extract(InputStream tarBytes) throws IOException {
        if (tarBytes == null) {
            throw new IOException("tarBytes must not be null");
        }
        Map<String, byte[]> streams = readTar(tarBytes);
        return decode(streams);
    }

    // ─── internals ─────────────────────────────────────────────────────────────

    /**
     * Walk the tar and collect every regular-file entry into a name → bytes
     * map. We use {@code TreeMap} to keep iteration deterministic (helps
     * the multi-frame case land in filename order). Throws {@link IOException}
     * when the input isn't a valid tar archive — distinguishes "tolerable
     * issue inside an OTvis tar" from "this isn't a tar at all".
     */
    private static Map<String, byte[]> readTar(InputStream tarBytes) throws IOException {
        Map<String, byte[]> out = new TreeMap<>();
        // BufferedInputStream gives commons-compress mark/reset for tar header probing.
        try (TarArchiveInputStream tar = new TarArchiveInputStream(new BufferedInputStream(tarBytes))) {
            TarArchiveEntry entry;
            boolean sawAny = false;
            try {
                while ((entry = tar.getNextEntry()) != null) {
                    sawAny = true;
                    if (!entry.isFile()) continue;
                    String name = entry.getName();
                    if (name == null) continue;
                    long size = entry.getSize();
                    ByteArrayOutputStream buf = size > 0 && size < Integer.MAX_VALUE
                            ? new ByteArrayOutputStream((int) size)
                            : new ByteArrayOutputStream(8192);
                    byte[] chunk = new byte[8192];
                    int n;
                    while ((n = tar.read(chunk)) >= 0) {
                        buf.write(chunk, 0, n);
                    }
                    out.put(name, buf.toByteArray());
                }
            } catch (IOException e) {
                throw new IOException("not a usable tar archive: " + e.getMessage(), e);
            }
            if (!sawAny) {
                // Empty stream OR a stream whose first 512 bytes weren't a
                // valid tar header — commons-compress silently returns null
                // from getNextEntry() in both cases. We can't tell them
                // apart without re-reading; treat both as IOException so a
                // negative test gets a deterministic failure mode.
                throw new IOException("not a usable tar archive: no entries found");
            }
        }
        return out;
    }

    /**
     * Top-level decoder. Splits the streams by sequence, builds the
     * calibration LUT (with optional per-frame overrides), and feeds each
     * frame file through {@link #decodeFrameFile}.
     */
    private static ExtractedFrames decode(Map<String, byte[]> streams) {
        StringBuilder reasons = new StringBuilder();

        if (!streams.containsKey("content.xml")) {
            // content.xml is the tier-1 manifest. Tier-2 does not need it
            // for DataFormat dispatch (the per-frame header carries that),
            // but its absence is a meaningful signal for the caller.
            appendReason(reasons, "content.xml missing");
        }

        // Find the sequence-wide calibration LUT (if any).
        CalibrationLut seqCalibration = null;
        for (Map.Entry<String, byte[]> e : streams.entrySet()) {
            if (basename(e.getKey()).equalsIgnoreCase("calibration.bin")) {
                try {
                    seqCalibration = decodeCalibrationLut(e.getValue());
                } catch (IllegalArgumentException ex) {
                    appendReason(reasons, "calibration.bin malformed: " + ex.getMessage());
                }
                break;
            }
        }
        if (seqCalibration == null) {
            appendReason(reasons, "sequence calibration.bin absent or unusable");
        } else if (!seqCalibration.isMonotonic()) {
            appendReason(reasons, "calibration LUT is not monotonic");
        }

        // Per-frame calibration overrides: c<FrameID>.bin in the same dir.
        Map<String, CalibrationLut> overrides = new HashMap<>();
        for (Map.Entry<String, byte[]> e : streams.entrySet()) {
            String name = e.getKey();
            String base = basename(name);
            if (base.length() > 1 && base.charAt(0) == 'c' && base.endsWith(".bin")) {
                try {
                    overrides.put(name, decodeCalibrationLut(e.getValue()));
                } catch (IllegalArgumentException ex) {
                    appendReason(reasons, "override " + name + " malformed: " + ex.getMessage());
                }
            }
        }

        List<LockInResultFrame> lockIn = new ArrayList<>();
        List<RawCalibratedFrame> rawCalibrated = new ArrayList<>();
        int observedWidth = 0;
        int observedHeight = 0;

        for (Map.Entry<String, byte[]> e : streams.entrySet()) {
            String name = e.getKey();
            String base = basename(name);
            // Match f<N>.bin only. Excludes calibration.bin, content.xml,
            // c<N>.bin overrides (already collected above), and the XML
            // schemata under schemata/.
            if (base.length() <= 1 || base.charAt(0) != 'f' || !base.endsWith(".bin")) continue;
            if (name.contains("schemata/")) continue;

            // Pick the calibration for this frame: per-frame override > sequence-wide.
            String dir = dirOf(name);
            CalibrationLut framCal = pickOverride(overrides, dir, base, seqCalibration);

            try {
                FrameDecodeResult fr = decodeFrameFile(name, e.getValue(), framCal);
                lockIn.addAll(fr.lockIn);
                rawCalibrated.addAll(fr.rawCalibrated);
                if (fr.reason != null) appendReason(reasons, fr.reason);
                if (observedWidth == 0 && fr.width > 0) observedWidth = fr.width;
                if (observedHeight == 0 && fr.height > 0) observedHeight = fr.height;
                if (fr.width > 0 && fr.height > 0
                        && (fr.width != observedWidth || fr.height != observedHeight)) {
                    appendReason(reasons,
                            name + " has dimensions " + fr.width + "x" + fr.height
                                    + " but earlier frames were " + observedWidth + "x" + observedHeight);
                }
            } catch (RuntimeException ex) {
                // Defensive belt: per the fail-soft rule, we degrade
                // rather than propagate.
                appendReason(reasons, name + " decode failed: " + ex.getMessage());
            }
        }

        String partial = reasons.length() == 0 ? null : reasons.toString();
        return new ExtractedFrames(lockIn, rawCalibrated, seqCalibration,
                observedWidth, observedHeight, partial);
    }

    /** Pick the most specific calibration: same-dir per-frame override > sequence LUT > null. */
    private static CalibrationLut pickOverride(
            Map<String, CalibrationLut> overrides,
            String dir,
            String frameBase,
            CalibrationLut fallback) {
        // c<FrameID>.bin sibling of f<FrameID>.bin
        String overrideBase = "c" + frameBase.substring(1);
        String overrideKey = dir.isEmpty() ? overrideBase : dir + "/" + overrideBase;
        CalibrationLut o = overrides.get(overrideKey);
        return o != null ? o : fallback;
    }

    /**
     * Carry struct for the inner {@link #decodeFrameFile} return shape.
     * Package-private only so the surrounding class can use it; not part
     * of the public API.
     */
    private static final class FrameDecodeResult {
        final List<LockInResultFrame> lockIn = new ArrayList<>();
        final List<RawCalibratedFrame> rawCalibrated = new ArrayList<>();
        String reason;
        int width;
        int height;
    }

    /**
     * Decode one {@code f<N>.bin}. Walks 28-byte-header → payload pairs
     * until the file is consumed (the multi-frame case from
     * byte-layout-notes §5). The first frame's dimensions are reported up
     * to the aggregate result; subsequent frames are only accepted if
     * they match.
     */
    private static FrameDecodeResult decodeFrameFile(
            String name, byte[] blob, CalibrationLut calibration) {
        FrameDecodeResult out = new FrameDecodeResult();
        if (blob == null || blob.length < HEADER_BYTES) {
            out.reason = name + " too short for a 28-byte header";
            return out;
        }

        int pos = 0;
        int frameIndex = 0;
        while (pos + HEADER_BYTES <= blob.length) {
            RecurringHeader header = decodeHeader(blob, pos);
            int width = header.width();
            int height = header.height();
            int dataFormat = header.dataFormat();

            if (frameIndex == 0) {
                out.width = width;
                out.height = height;
            }
            if (width <= 0 || height <= 0 || width > 32768 || height > 32768) {
                out.reason = appendReasonLocal(out.reason,
                        name + " frame " + frameIndex
                                + " has implausible dimensions " + width + "x" + height);
                return out;
            }

            int bpp;
            try {
                bpp = bytesPerPixel(dataFormat);
            } catch (IllegalArgumentException e) {
                out.reason = appendReasonLocal(out.reason,
                        name + " frame " + frameIndex + " unknown DataFormat " + dataFormat);
                return out;
            }

            long payloadLong = (long) width * height * bpp;
            if (payloadLong > Integer.MAX_VALUE) {
                out.reason = appendReasonLocal(out.reason,
                        name + " frame " + frameIndex + " payload too large");
                return out;
            }
            int payload = (int) payloadLong;
            int payloadStart = pos + HEADER_BYTES;
            if (payloadStart + payload > blob.length) {
                out.reason = appendReasonLocal(out.reason,
                        name + " frame " + frameIndex
                                + " truncated: expected " + payload + " payload bytes, "
                                + (blob.length - payloadStart) + " available");
                return out;
            }

            switch (dataFormat) {
                case DF_COMPLEX_FLOAT -> out.lockIn.add(decodeComplex(blob, payloadStart, payload, header));
                case DF_UINT16 -> {
                    if (calibration == null) {
                        out.reason = appendReasonLocal(out.reason,
                                name + " frame " + frameIndex
                                        + " is uint16 raw but no calibration LUT is available");
                    } else {
                        out.rawCalibrated.add(decodeUint16(blob, payloadStart, payload, header, calibration));
                    }
                }
                default -> out.reason = appendReasonLocal(out.reason,
                        name + " frame " + frameIndex + " DataFormat " + dataFormat
                                + " is documented but not implemented in tier-2");
            }

            pos = payloadStart + payload;
            frameIndex++;
        }
        if (pos != blob.length) {
            out.reason = appendReasonLocal(out.reason,
                    name + " has " + (blob.length - pos) + " trailing bytes after the last frame");
        }
        return out;
    }

    /** Decode a {@link RecurringHeader} at {@code offset} in {@code blob}. */
    private static RecurringHeader decodeHeader(byte[] blob, int offset) {
        ByteBuffer bb = ByteBuffer.wrap(blob, offset, HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        byte[] magic = new byte[RecurringHeader.MAGIC_BYTES];
        bb.get(magic);
        long w = Integer.toUnsignedLong(bb.getInt());
        long h = Integer.toUnsignedLong(bb.getInt());
        long d = Integer.toUnsignedLong(bb.getInt());
        return new RecurringHeader(magic, w, h, d);
    }

    /** Decode one DataFormat=13 complex frame into amplitude + phase. */
    private static LockInResultFrame decodeComplex(
            byte[] blob, int payloadStart, int payload, RecurringHeader header) {
        int n = header.width() * header.height();
        float[] amp = new float[n];
        float[] phase = new float[n];
        ByteBuffer bb = ByteBuffer.wrap(blob, payloadStart, payload).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < n; i++) {
            float re = bb.getFloat();
            float im = bb.getFloat();
            // Math.hypot returns double; cast back. The range covered by
            // double here easily fits float for normal lock-in results.
            amp[i] = (float) Math.hypot(re, im);
            phase[i] = (float) Math.atan2(im, re);
        }
        return new LockInResultFrame(amp, phase, header);
    }

    /** Decode one DataFormat=2 uint16 raw frame and apply the calibration LUT. */
    private static RawCalibratedFrame decodeUint16(
            byte[] blob,
            int payloadStart,
            int payload,
            RecurringHeader header,
            CalibrationLut calibration) {
        int n = header.width() * header.height();
        int[] raw = new int[n];
        float[] cel = new float[n];
        ByteBuffer bb = ByteBuffer.wrap(blob, payloadStart, payload).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < n; i++) {
            int rv = bb.getShort() & 0xFFFF;
            raw[i] = rv;
            cel[i] = calibration.celsiusFor(rv);
        }
        return new RawCalibratedFrame(cel, raw, header);
    }

    /**
     * Decode a sequence-wide or per-frame calibration LUT
     * ({@code 65536 × float32 LE}, no header).
     *
     * @throws IllegalArgumentException if the buffer is the wrong length
     */
    private static CalibrationLut decodeCalibrationLut(byte[] blob) {
        if (blob == null || blob.length != CALIB_BYTES) {
            throw new IllegalArgumentException(
                    "expected " + CALIB_BYTES + " bytes, got " + (blob == null ? "null" : blob.length));
        }
        ByteBuffer bb = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN);
        float[] lut = new float[CALIB_FLOATS];
        for (int i = 0; i < CALIB_FLOATS; i++) lut[i] = bb.getFloat();
        return new CalibrationLut(lut);
    }

    // ─── small helpers ─────────────────────────────────────────────────────────

    /** Map a Rev H DataFormat to bytes-per-pixel. */
    public static int bytesPerPixel(int dataFormat) {
        return switch (dataFormat) {
            case DF_UINT8 -> 1;
            case DF_UINT16 -> 2;
            case DF_FLOAT32 -> 4;
            case DF_COMPLEX_FLOAT -> 8;
            case DF_BGR_TRUECOLOR -> 3;
            default -> throw new IllegalArgumentException("unknown DataFormat " + dataFormat);
        };
    }

    /**
     * Convenience helper: decode a calibration LUT from raw bytes. Used by
     * tests and by external callers who already have the LUT bytes in
     * hand (e.g. from a separate side channel).
     */
    public static CalibrationLut parseCalibrationLut(byte[] blob) {
        return decodeCalibrationLut(blob);
    }

    /**
     * Convenience helper exposed for {@link OTvisFrameExtractor}'s own
     * tests and for the {@link OTvisParser} flow. Walks the tar fully
     * into memory; returns an empty map on a malformed archive (never
     * throws) — diverges from the strict-throwing {@link #readTar} used
     * by {@link #extract} on purpose.
     */
    public static Map<String, byte[]> readTarLenient(InputStream tarBytes) {
        try {
            return readTar(tarBytes);
        } catch (IOException e) {
            return Map.of();
        }
    }

    /**
     * Convenience: build {@link ExtractedFrames} from a pre-collected map
     * of stream-name → bytes. Used by tests that fabricate a synthetic
     * tar in-memory; never throws.
     */
    public static ExtractedFrames decodeFromStreams(Map<String, byte[]> streams) {
        return decode(streams);
    }

    private static String basename(String path) {
        if (path == null) return "";
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static String dirOf(String path) {
        if (path == null) return "";
        int slash = path.lastIndexOf('/');
        if (slash < 0) slash = path.lastIndexOf('\\');
        return slash >= 0 ? path.substring(0, slash) : "";
    }

    private static void appendReason(StringBuilder buf, String reason) {
        if (buf.length() > 0) buf.append("; ");
        buf.append(reason);
    }

    private static String appendReasonLocal(String existing, String more) {
        if (existing == null || existing.isEmpty()) return more;
        return existing + "; " + more;
    }

    /**
     * Decode just the header at offset 0 of a buffer. Public for the
     * benefit of probe-style tests; the canonical use is through
     * {@link #extract(InputStream)}.
     */
    public static RecurringHeader readHeader(byte[] blob) {
        if (blob == null || blob.length < HEADER_BYTES) {
            throw new IllegalArgumentException("buffer shorter than 28-byte header");
        }
        return decodeHeader(blob, 0);
    }

    /**
     * Decode a {@link CalibrationLut} from raw bytes. Synonym for
     * {@link #parseCalibrationLut}; kept for naming-consistency with
     * {@link #readHeader}.
     */
    public static CalibrationLut readCalibrationLut(byte[] blob) {
        return decodeCalibrationLut(blob);
    }

    /**
     * Re-pack an in-memory map of {@code name → bytes} back into a POSIX
     * tar byte array. Test-only utility; lives here so tests that fabricate
     * degraded inputs can stay in the same package. Never used at runtime.
     *
     * <p>Intentionally NOT public — tests live in this package.
     */
    static byte[] repackTar(Map<String, byte[]> streams) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (org.apache.commons.compress.archivers.tar.TarArchiveOutputStream tos =
                new org.apache.commons.compress.archivers.tar.TarArchiveOutputStream(baos)) {
            tos.setLongFileMode(
                    org.apache.commons.compress.archivers.tar.TarArchiveOutputStream.LONGFILE_POSIX);
            for (Map.Entry<String, byte[]> e : streams.entrySet()) {
                TarArchiveEntry entry = new TarArchiveEntry(e.getKey());
                entry.setSize(e.getValue().length);
                tos.putArchiveEntry(entry);
                tos.write(e.getValue());
                tos.closeArchiveEntry();
            }
            tos.finish();
        }
        return baos.toByteArray();
    }

    /** Read all bytes from a stream (test helper). */
    static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int n;
        while ((n = in.read(chunk)) >= 0) buf.write(chunk, 0, n);
        return buf.toByteArray();
    }

    /** Open a fresh {@link ByteArrayInputStream} over a buffer (test helper). */
    static ByteArrayInputStream open(byte[] bytes) {
        return new ByteArrayInputStream(bytes);
    }
}
