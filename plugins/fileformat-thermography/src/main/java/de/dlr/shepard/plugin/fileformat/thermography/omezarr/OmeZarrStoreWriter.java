package de.dlr.shepard.plugin.fileformat.thermography.omezarr;

import de.dlr.shepard.plugin.fileformat.thermography.CalibrationLut;
import de.dlr.shepard.plugin.fileformat.thermography.ExtractedFrames;
import de.dlr.shepard.plugin.fileformat.thermography.LockInResultFrame;
import de.dlr.shepard.plugin.fileformat.thermography.RawCalibratedFrame;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes an {@link ExtractedFrames} bundle to an OME-Zarr v3 store at a
 * caller-supplied {@link Path} root.
 *
 * <p><b>Why hand-rolled rather than JZarr.</b> JZarr (latest release
 * {@code 0.3.5} at the BC nexus, no {@code 0.4.x} exists) only emits Zarr
 * <i>v2</i> stores ({@code .zarray}/{@code .zgroup}/{@code .zattrs}). The
 * task brief and {@code aidocs/integrations/115 §5d} require Zarr v3
 * ({@code zarr.json} descriptors). Rather than ship a Zarr v2 store and
 * call it OME-Zarr v3, the writer hand-rolls the minimal v3 store that
 * the {@code zarrita.js} loader in {@code ThermographyCanvas.vue} reads.
 *
 * <p><b>Spec subset implemented:</b>
 * <ul>
 *   <li>Zarr v3 array spec — {@code zarr.json} with {@code zarr_format=3},
 *       {@code node_type=array}, regular {@code chunk_grid}, default
 *       {@code chunk_key_encoding} ({@code c/i/j/k}), {@code bytes} codec
 *       with little-endian configuration, {@code dimension_names}.</li>
 *   <li>Zarr v3 group spec — {@code zarr.json} with {@code zarr_format=3},
 *       {@code node_type=group}, OME-NGFF v0.5-style {@code attributes}
 *       (the minimal {@code multiscales}/{@code coordinateTransformations}
 *       block is not required for native-resolution playback and is
 *       deferred to OTVIS-VIEW-PYRAMID — see {@code aidocs/integrations/115
 *       §5g} for the multiscale story).</li>
 *   <li>Data type: {@code float32} only. The tier-2 pipeline normalises
 *       everything (amplitude, phase, calibrated temperature, raw counts)
 *       to {@code float32} before writing; the LUT is also {@code float32}.</li>
 * </ul>
 *
 * <p><b>What is NOT implemented:</b>
 * <ul>
 *   <li>Compression codecs (the chunks are raw little-endian
 *       {@code float32}). Adding {@code blosc} or {@code zstd} is
 *       backlog-deferred until store size pressure is real.</li>
 *   <li>Sharding ({@code sharding_indexed} codec). Not needed at MFFD
 *       fixture scale.</li>
 *   <li>OME-NGFF {@code multiscales} pyramid. The widget reads native
 *       resolution; {@code OTVIS-VIEW-PYRAMID} is the future row.</li>
 * </ul>
 *
 * <p>The writer is a pure function over the caller-supplied
 * {@code Path}: no implicit working-directory behaviour, no network, no
 * temp-dir scratching outside the supplied root. Errors propagate as
 * {@link IOException}; the caller (typically
 * {@link de.dlr.shepard.plugin.fileformat.thermography.OTvisTier2Pipeline})
 * is responsible for the fail-soft envelope.
 */
public final class OmeZarrStoreWriter {

    /**
     * Writes {@code frames} to an OME-Zarr v3 store rooted under
     * {@code rootPath/thermography/{fileReferenceAppId}/}.
     *
     * <p>Store layout per {@code aidocs/ops/vis-s2-garage-omezarr-storage-policy.md}:
     * <pre>
     * thermography/{fileReferenceAppId}/
     *   zarr.json                       (root group)
     *   amplitude/zarr.json             (array: T x H x W or absent)
     *   amplitude/c/&lt;t&gt;/&lt;y&gt;/&lt;x&gt;       (chunks)
     *   phase/zarr.json                 (array: T x H x W or absent)
     *   phase/c/&lt;t&gt;/&lt;y&gt;/&lt;x&gt;           (chunks)
     *   raw/zarr.json                   (array: T x H x W or absent)
     *   raw/c/&lt;t&gt;/&lt;y&gt;/&lt;x&gt;             (chunks)
     *   calibration/zarr.json           (array: 65536 or absent)
     *   calibration/c/0                 (single chunk)
     * </pre>
     *
     * <p>Each lock-in/raw array is chunked one frame per chunk on the
     * {@code t} axis: a single chunk holds {@code 1 x H x W}. The
     * calibration LUT is a single chunk of {@code 65536} entries. Missing
     * sub-arrays (e.g. no {@code rawCalibrated} frames) are skipped
     * entirely; the root group still validates as OME-Zarr v3.
     *
     * @param frames        the extracted frames bundle; never {@code null}
     * @param rootPath      directory under which the
     *                      {@code thermography/} prefix is created
     * @param fileReferenceAppId  appId of the source FileReference, used
     *                            as the in-store directory key per the
     *                            VIS-S2 policy
     * @return the canonical store URL — {@code file:///...} for a local
     *         path
     * @throws IOException if any filesystem write fails or the input is
     *                     null
     */
    public String write(ExtractedFrames frames, Path rootPath, String fileReferenceAppId)
            throws IOException {
        if (frames == null) {
            throw new IOException("frames must not be null");
        }
        if (rootPath == null) {
            throw new IOException("rootPath must not be null");
        }
        if (fileReferenceAppId == null || fileReferenceAppId.isBlank()) {
            throw new IOException("fileReferenceAppId must not be null or blank");
        }

        Path storeRoot = rootPath.resolve("thermography").resolve(fileReferenceAppId);
        Files.createDirectories(storeRoot);

        // Root group descriptor. Attributes carry an "ome" key with the
        // bundle's overall shape summary so a reader can dispatch without
        // walking every sub-array first.
        Map<String, Object> rootAttrs = new LinkedHashMap<>();
        Map<String, Object> omeAttrs = new LinkedHashMap<>();
        omeAttrs.put("kind", "thermography-omezarr");
        omeAttrs.put("width", frames.width);
        omeAttrs.put("height", frames.height);
        omeAttrs.put("lockInFrameCount", frames.lockInResult.size());
        omeAttrs.put("rawFrameCount", frames.rawCalibrated.size());
        omeAttrs.put("hasCalibrationLut", frames.calibrationLut != null);
        if (frames.partialReason != null) {
            omeAttrs.put("partialReason", frames.partialReason);
        }
        rootAttrs.put("ome", omeAttrs);
        writeGroupJson(storeRoot, rootAttrs);

        // Lock-in result: amplitude + phase split into two sibling arrays.
        // Shape is (T, H, W) so a downstream pyramid pass can naturally
        // align the t axis with the raw array.
        if (!frames.lockInResult.isEmpty()) {
            writeLockInArrays(storeRoot, frames.lockInResult, frames.width, frames.height);
        }

        if (!frames.rawCalibrated.isEmpty()) {
            writeRawArray(storeRoot, frames.rawCalibrated, frames.width, frames.height);
        }

        if (frames.calibrationLut != null) {
            writeCalibrationArray(storeRoot, frames.calibrationLut);
        }

        return storeRoot.toUri().toString();
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private void writeLockInArrays(
            Path storeRoot, List<LockInResultFrame> lockIn, int width, int height)
            throws IOException {
        int t = lockIn.size();
        long[] shape = new long[] {t, height, width};
        long[] chunkShape = new long[] {1, height, width};
        List<String> dims = List.of("t", "y", "x");

        Path amplitudeDir = storeRoot.resolve("amplitude");
        Files.createDirectories(amplitudeDir);
        writeArrayJson(amplitudeDir, shape, chunkShape, dims);

        Path phaseDir = storeRoot.resolve("phase");
        Files.createDirectories(phaseDir);
        writeArrayJson(phaseDir, shape, chunkShape, dims);

        for (int i = 0; i < t; i++) {
            LockInResultFrame frame = lockIn.get(i);
            // Chunk key: c/<t>/0/0 (single chunk per y/x because the chunk
            // shape covers the full image).
            Path ampChunk = amplitudeDir.resolve("c").resolve(Integer.toString(i))
                    .resolve("0").resolve("0");
            Path phaseChunk = phaseDir.resolve("c").resolve(Integer.toString(i))
                    .resolve("0").resolve("0");
            Files.createDirectories(ampChunk.getParent());
            Files.createDirectories(phaseChunk.getParent());
            writeFloat32Chunk(ampChunk, frame.amplitude);
            writeFloat32Chunk(phaseChunk, frame.phase);
        }
    }

    private void writeRawArray(
            Path storeRoot, List<RawCalibratedFrame> raw, int width, int height)
            throws IOException {
        int t = raw.size();
        long[] shape = new long[] {t, height, width};
        long[] chunkShape = new long[] {1, height, width};
        List<String> dims = List.of("t", "y", "x");

        Path rawDir = storeRoot.resolve("raw");
        Files.createDirectories(rawDir);
        writeArrayJson(rawDir, shape, chunkShape, dims);

        for (int i = 0; i < t; i++) {
            RawCalibratedFrame frame = raw.get(i);
            Path chunk = rawDir.resolve("c").resolve(Integer.toString(i))
                    .resolve("0").resolve("0");
            Files.createDirectories(chunk.getParent());
            writeFloat32Chunk(chunk, frame.temperatureCelsius);
        }
    }

    private void writeCalibrationArray(Path storeRoot, CalibrationLut lut) throws IOException {
        long[] shape = new long[] {CalibrationLut.LUT_SIZE};
        long[] chunkShape = new long[] {CalibrationLut.LUT_SIZE};
        List<String> dims = List.of("idx");

        Path dir = storeRoot.resolve("calibration");
        Files.createDirectories(dir);
        writeArrayJson(dir, shape, chunkShape, dims);

        Path chunk = dir.resolve("c").resolve("0");
        Files.createDirectories(chunk.getParent());
        writeFloat32Chunk(chunk, lut.toArray());
    }

    private void writeArrayJson(
            Path arrayDir, long[] shape, long[] chunkShape, List<String> dimensionNames)
            throws IOException {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("zarr_format", 3);
        doc.put("node_type", "array");
        doc.put("shape", longArrayAsList(shape));
        doc.put("data_type", "float32");

        Map<String, Object> chunkGrid = new LinkedHashMap<>();
        chunkGrid.put("name", "regular");
        Map<String, Object> chunkGridConfig = new LinkedHashMap<>();
        chunkGridConfig.put("chunk_shape", longArrayAsList(chunkShape));
        chunkGrid.put("configuration", chunkGridConfig);
        doc.put("chunk_grid", chunkGrid);

        Map<String, Object> chunkKeyEncoding = new LinkedHashMap<>();
        chunkKeyEncoding.put("name", "default");
        Map<String, Object> chunkKeyEncodingConfig = new LinkedHashMap<>();
        chunkKeyEncodingConfig.put("separator", "/");
        chunkKeyEncoding.put("configuration", chunkKeyEncodingConfig);
        doc.put("chunk_key_encoding", chunkKeyEncoding);

        doc.put("fill_value", 0);

        // bytes codec with little-endian configuration — the minimal v3
        // codec set that reads raw little-endian float32 from chunk
        // files. No compression layer.
        Map<String, Object> bytesCodec = new LinkedHashMap<>();
        bytesCodec.put("name", "bytes");
        Map<String, Object> bytesCodecConfig = new LinkedHashMap<>();
        bytesCodecConfig.put("endian", "little");
        bytesCodec.put("configuration", bytesCodecConfig);
        doc.put("codecs", List.of(bytesCodec));

        doc.put("attributes", new LinkedHashMap<String, Object>());
        doc.put("dimension_names", List.copyOf(dimensionNames));

        Files.writeString(arrayDir.resolve("zarr.json"), MiniJson.write(doc));
    }

    private void writeGroupJson(Path groupDir, Map<String, Object> attributes) throws IOException {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("zarr_format", 3);
        doc.put("node_type", "group");
        doc.put("attributes", attributes);
        Files.writeString(groupDir.resolve("zarr.json"), MiniJson.write(doc));
    }

    private void writeFloat32Chunk(Path chunkPath, float[] values) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(values.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float v : values) {
            buf.putFloat(v);
        }
        try (OutputStream out = Files.newOutputStream(chunkPath)) {
            out.write(buf.array());
        }
    }

    private static List<Long> longArrayAsList(long[] arr) {
        List<Long> out = new ArrayList<>(arr.length);
        for (long v : arr) {
            out.add(v);
        }
        return out;
    }

    /**
     * Minimal JSON serializer producing UTF-8 output compatible with the
     * Zarr v3 reader subset {@code zarrita.js} consumes. Public surface is
     * one method so the writer's spec is byte-deterministic — important
     * for test assertions that re-read the {@code zarr.json} via
     * {@link java.util.regex.Pattern} matchers rather than a JSON
     * dependency.
     */
    static final class MiniJson {

        private MiniJson() {}

        static String write(Object value) {
            StringBuilder sb = new StringBuilder();
            writeValue(sb, value);
            return sb.toString();
        }

        @SuppressWarnings("unchecked")
        private static void writeValue(StringBuilder sb, Object value) {
            if (value == null) {
                sb.append("null");
            } else if (value instanceof Boolean b) {
                sb.append(b ? "true" : "false");
            } else if (value instanceof Number n) {
                // Force integer Number subtypes (Integer, Long) to emit
                // without a decimal — Zarr v3 readers reject "3.0" where
                // they want 3 (zarr_format, shape entries, chunk_shape).
                if (n instanceof Long || n instanceof Integer || n instanceof Short
                        || n instanceof Byte) {
                    sb.append(n.toString());
                } else {
                    sb.append(n.toString());
                }
            } else if (value instanceof CharSequence s) {
                writeString(sb, s.toString());
            } else if (value instanceof Map<?, ?> m) {
                writeObject(sb, (Map<String, Object>) m);
            } else if (value instanceof List<?> l) {
                writeArray(sb, l);
            } else {
                // Defensive: anything else gets coerced to its string form.
                writeString(sb, value.toString());
            }
        }

        private static void writeObject(StringBuilder sb, Map<String, Object> obj) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> e : obj.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                writeString(sb, e.getKey());
                sb.append(':');
                writeValue(sb, e.getValue());
                first = false;
            }
            sb.append('}');
        }

        private static void writeArray(StringBuilder sb, List<?> list) {
            sb.append('[');
            boolean first = true;
            for (Object o : list) {
                if (!first) {
                    sb.append(',');
                }
                writeValue(sb, o);
                first = false;
            }
            sb.append(']');
        }

        private static void writeString(StringBuilder sb, String s) {
            sb.append('"');
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"':
                        sb.append("\\\"");
                        break;
                    case '\\':
                        sb.append("\\\\");
                        break;
                    case '\b':
                        sb.append("\\b");
                        break;
                    case '\f':
                        sb.append("\\f");
                        break;
                    case '\n':
                        sb.append("\\n");
                        break;
                    case '\r':
                        sb.append("\\r");
                        break;
                    case '\t':
                        sb.append("\\t");
                        break;
                    default:
                        if (c < 0x20) {
                            sb.append(String.format("\\u%04x", (int) c));
                        } else {
                            sb.append(c);
                        }
                }
            }
            sb.append('"');
        }
    }

    /** UTF-8 encoding helper for tests that want raw bytes. */
    static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
