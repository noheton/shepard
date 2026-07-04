package de.dlr.shepard.plugin.fileformat.svdx;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Tier-2 decoder for the proprietary binary sample region of a TwinCAT
 * Scope {@code .svdx} file — the part {@link SvdxEnvelope} and
 * {@link SvdxManifestParser} deliberately leave opaque.
 *
 * <p>The format was reverse-engineered against the real DLR ZLP MFFD AFP
 * ultrasonic-spot-welding campaign (2023-03-20) and validated
 * end-to-end; the full byte map and the validation evidence live in
 * {@code docs/byte-layout-notes.md} §"Binary sample blocks". The load-
 * bearing facts this class encodes:
 *
 * <pre>
 * Binary section begins at file offset 16 (immediately after the
 * 16-byte envelope), with an acquisition-index header:
 *
 *   +0x00 (file 0x10)  u32  n_acquisitions          (== manifest AdsAcquisition count)
 *   +0x04 (file 0x14)  u64  data_section_start      (abs file offset of acq #1 data)
 *   +0x0c (file 0x1c)  u64  size_of_record_1        (on-disk byte size of acq #1)
 *   +0x14 (file 0x24)  ──   index: (n-1) × 20-byte records, then u32 trailer == n
 *
 * Index record (20 bytes):
 *   +0   u32  acquisition_index   (1-based, monotonic)
 *   +4   u64  cumulative_offset   (ABSOLUTE end offset of this acquisition's data)
 *   +12  u64  next_record_size    (on-disk size of acquisition i+1; look-ahead)
 *
 * Each "acquisition" is ONE CHANNEL's full recording. Acq i data spans
 * [cum_off[i-1], cum_off[i]); acq #1 spans [data_section_start, cum_off[1]).
 *
 * Per-channel block = header (ASCII "01.00.00.40" + three FILETIMEs at
 * +11/+27/+35 = acq-start / window-start / window-end) followed by a
 * sample stream of records:
 *
 *   [ value : channel DataType width ][ tick : u32, 100 ns since acq-start ]
 *
 * INT16 → 6-byte record. Sample wall-clock = FILETIME(+11) + tick·100 ns.
 * </pre>
 *
 * <p><b>Known limitation (see byte-layout-notes §"remaining"):</b> long
 * real-world blocks interleave intra-block <em>segment</em> sub-headers
 * that re-emit a FILETIME base and reset the tick counter on each
 * trigger/restart. This decoder is resync-tolerant — it emits the
 * monotonic runs it can decode and skips over the bytes it cannot align
 * — but it does not yet model the segment header explicitly, so on
 * multi-segment files it returns the union of the cleanly-decodable runs
 * rather than a guaranteed-complete sample set. Single-segment blocks
 * (the common short-capture case) decode completely.
 */
public final class SvdxBinaryParser {

    /** File offset where the binary section (acquisition-index header) begins. */
    public static final int BINARY_SECTION_OFFSET = 16;

    /** Bytes per acquisition-index record. */
    public static final int INDEX_RECORD_SIZE = 20;

    /** Header sub-offsets of the three Windows FILETIMEs within a channel block. */
    public static final int FT_ACQ_START = 11;
    public static final int FT_WINDOW_START = 27;
    public static final int FT_WINDOW_END = 35;

    /** Width (bytes) of the per-sample timestamp tick field. */
    public static final int TICK_WIDTH = 4;

    /**
     * Bytes of a fine-segment header preceding each run of samples:
     * {@code [u16 (prev segment's last value — not a marker)][u64 FILETIME]
     * [u16 sampleCount]}. The FILETIME is the segment's absolute start; the
     * samples that follow carry ticks relative to it (0, period, 2·period…).
     */
    public static final int SEGMENT_HEADER_SIZE = 12;
    private static final int SEG_FT_OFFSET = 2;
    private static final int SEG_COUNT_OFFSET = 10;

    private SvdxBinaryParser() {}

    /** One decoded (timestamp, value) sample. {@code tickNs100} is in units
     *  of 100 ns relative to the channel's acquisition-start FILETIME
     *  ({@link DecodedChannel#acqStartFiletime()}), already corrected for the
     *  per-segment FILETIME base so it is globally monotonic across segments.
     *  Absolute wall-clock = {@code acqStartFiletime + tickNs100} (FILETIME
     *  units = 100 ns since 1601-01-01). */
    public record Sample(long tickNs100, double value) {}

    /** A single acquisition-index entry, exactly as stored on disk. */
    public record IndexEntry(int index, long cumulativeEndOffset, long nextRecordSize) {}

    /** A fully decoded channel: identity from the manifest, timing from the
     *  block header, and the decoded sample run(s). */
    public record DecodedChannel(
        int acquisitionIndex,
        String symbolName,
        String dataType,
        long acqStartFiletime,
        long windowStartFiletime,
        long windowEndFiletime,
        List<Sample> samples
    ) {}

    /** Header of the binary section (the acquisition-index preamble). */
    public record BinaryHeader(int nAcquisitions, long dataSectionStart, long sizeOfRecord1) {}

    /**
     * Decode the acquisition-index preamble at {@link #BINARY_SECTION_OFFSET}.
     *
     * @param file the whole file bytes (only the first ~32 bytes are read)
     */
    public static BinaryHeader decodeHeader(byte[] file) {
        if (file == null || file.length < BINARY_SECTION_OFFSET + 16) {
            throw new IllegalArgumentException("file too small for a binary-section header");
        }
        ByteBuffer bb = ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN);
        int n = bb.getInt(BINARY_SECTION_OFFSET);          // file 0x10
        long dataStart = bb.getLong(BINARY_SECTION_OFFSET + 4);  // file 0x14
        long size1 = bb.getLong(BINARY_SECTION_OFFSET + 12);     // file 0x1c
        return new BinaryHeader(n, dataStart, size1);
    }

    /**
     * Decode the acquisition index — the {@code (n-1)} on-disk 20-byte
     * records plus the synthesised entry for acquisition #1 (whose size
     * lives in the header) and the final acquisition (whose end is the
     * start of the trailing XML manifest).
     *
     * @param file      the whole file bytes
     * @param header    the decoded {@link BinaryHeader}
     * @param xmlBomOffset  the offset of the trailing XML manifest BOM
     *                  (from {@link SvdxEnvelope#xmlBomOffset()}); used to
     *                  bound the final acquisition's data
     * @return one {@link IndexEntry} per acquisition, in order; size ==
     *         {@code header.nAcquisitions()}
     */
    public static List<IndexEntry> decodeIndex(byte[] file, BinaryHeader header, long xmlBomOffset) {
        int n = header.nAcquisitions();
        ByteBuffer bb = ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN);
        List<IndexEntry> out = new ArrayList<>(n);
        int base = BINARY_SECTION_OFFSET + 20; // file 0x24
        // On disk we have records for acquisitions 1..(n-1); acquisition n's
        // size is not stored (it runs to the XML manifest).
        for (int i = 0; i < n - 1; i++) {
            int o = base + i * INDEX_RECORD_SIZE;
            if (o + INDEX_RECORD_SIZE > file.length) break;
            int idx = bb.getInt(o);
            long cum = bb.getLong(o + 4);
            long nextSize = bb.getLong(o + 12);
            out.add(new IndexEntry(idx, cum, nextSize));
        }
        // Synthesise the final acquisition: it ends where the manifest begins.
        if (!out.isEmpty() && out.size() == n - 1) {
            out.add(new IndexEntry(n, xmlBomOffset, xmlBomOffset - out.get(out.size() - 1).cumulativeEndOffset()));
        }
        return out;
    }

    /** Absolute byte span {@code [start, end)} of acquisition {@code i}'s data. */
    public static long[] channelSpan(int i, BinaryHeader header, List<IndexEntry> index) {
        long start = (i == 0) ? header.dataSectionStart() : index.get(i - 1).cumulativeEndOffset();
        long end = index.get(i).cumulativeEndOffset();
        return new long[] {start, end};
    }

    /** Byte width of a sample value for an IEC-61131 / TwinCAT data type. */
    public static int valueWidth(String dataType) {
        if (dataType == null) return 0;
        return switch (dataType.trim().toUpperCase()) {
            case "BIT", "BOOL", "BYTE", "SINT", "USINT" -> 1;
            case "INT16", "INT", "UINT16", "UINT", "WORD" -> 2;
            case "INT32", "DINT", "UINT32", "UDINT", "DWORD", "REAL32", "REAL" -> 4;
            case "REAL64", "LREAL", "INT64", "LINT", "UINT64", "ULINT", "LWORD" -> 8;
            default -> 0; // unknown — caller skips sample decode
        };
    }

    private static double readValue(ByteBuffer bb, int off, String dataType, int width) {
        String dt = dataType.trim().toUpperCase();
        return switch (dt) {
            case "BIT", "BOOL", "BYTE", "USINT" -> bb.get(off) & 0xFF;
            case "SINT" -> bb.get(off);
            case "INT16", "INT" -> bb.getShort(off);
            case "UINT16", "UINT", "WORD" -> bb.getShort(off) & 0xFFFF;
            case "INT32", "DINT" -> bb.getInt(off);
            case "UINT32", "UDINT", "DWORD" -> bb.getInt(off) & 0xFFFFFFFFL;
            case "REAL32", "REAL" -> bb.getFloat(off);
            case "REAL64", "LREAL" -> bb.getDouble(off);
            case "INT64", "LINT" -> (double) bb.getLong(off);
            case "UINT64", "ULINT", "LWORD" -> unsignedLongToDouble(bb.getLong(off));
            default -> Double.NaN;
        };
    }

    private static double unsignedLongToDouble(long v) {
        return (v >= 0) ? (double) v : ((double) (v >>> 1)) * 2.0 + (v & 1);
    }

    /**
     * Decode one channel's block into its header timing + sample run(s).
     *
     * @param block     the channel's data bytes (the {@link #channelSpan} slice)
     * @param acqIndex  1-based acquisition index (for the result record)
     * @param symbolName  manifest symbol name for this channel (may be null)
     * @param dataType  manifest {@code <DataType>} for this channel; drives
     *                  the per-sample value width
     */
    public static DecodedChannel decodeChannel(byte[] block, int acqIndex, String symbolName, String dataType) {
        ByteBuffer bb = ByteBuffer.wrap(block).order(ByteOrder.LITTLE_ENDIAN);
        long acqStart = block.length >= FT_ACQ_START + 8 ? bb.getLong(FT_ACQ_START) : 0L;
        long winStart = block.length >= FT_WINDOW_START + 8 ? bb.getLong(FT_WINDOW_START) : 0L;
        long winEnd = block.length >= FT_WINDOW_END + 8 ? bb.getLong(FT_WINDOW_END) : 0L;

        int vw = valueWidth(dataType);
        List<Sample> samples = new ArrayList<>();
        if (vw > 0 && acqStart > 0) {
            samples = decodeSegments(bb, block.length, vw, dataType, acqStart);
        }
        return new DecodedChannel(acqIndex, symbolName, dataType, acqStart, winStart, winEnd, samples);
    }

    /** Slack before {@code acqStart} a segment FILETIME may legitimately fall. */
    private static final long FT_SLACK_BEFORE = 10_000_000L;        // 1 s in 100 ns units
    private static final long FT_WINDOW_AFTER = 24L * 3600 * 10_000_000L; // 24 h
    /** Upper bound on a segment's sample count (u16; real files use 16). */
    private static final int MAX_SEGMENT_COUNT = 4096;
    /** Max FILETIME gap (100 ns) between two chained segments — 100 s. */
    private static final long MAX_SEGMENT_GAP = 1_000_000_000L;
    /** Default per-sample spacing (100 ns) when a channel has a single segment. */
    private static final long DEFAULT_SPACING = 10_000L;

    /** A located segment: its data offset, absolute FILETIME, and sample count. */
    private record Seg(int dataOff, long filetime, int count) {}

    /**
     * Walk the channel block segment-by-segment. Each fine segment is a
     * 12-byte {@code [_][u64 FILETIME][u16 count]} header followed by
     * {@code count} fixed-size sample records (see class doc and
     * {@code byte-layout-notes.md}). The decoder:
     *
     * <ol>
     *   <li>finds the first segment whose <em>successor</em> is also a valid
     *       header (a chain of ≥2) — this rejects the coincidental
     *       FILETIME-range matches that lone wide-DataType values can fake;</li>
     *   <li>walks segments contiguously from there;</li>
     *   <li>emits each sample with an <b>absolute</b> tick relative to
     *       {@code acqStart}, timing the samples within a segment by the
     *       FILETIME-derived cadence (next segment's FILETIME − this one,
     *       divided by the count) rather than the stored per-sample tick —
     *       which is a plain {@code u32} on narrow channels but a
     *       fixed-point value on wide ones, so FILETIME spacing is the
     *       dtype-agnostic source of truth.</li>
     * </ol>
     *
     * <p>Record layout: a {@code u32} tick travels with every sample. On
     * narrow channels (INT16/BIT, value &lt; 4 bytes) the value leads and the
     * tick follows; on wide channels (≥ 4-byte value) the tick leads so the
     * value stays naturally aligned. {@link #valueOffsetInRecord} encodes this.
     */
    private static List<Sample> decodeSegments(
        ByteBuffer bb, int len, int valueWidth, String dataType, long acqStart) {
        final int recSize = valueWidth + TICK_WIDTH;
        final int valueOffset = valueOffsetInRecord(valueWidth);
        final long lo = acqStart - FT_SLACK_BEFORE;
        final long hi = acqStart + FT_WINDOW_AFTER;

        int first = findFirstSegment(bb, len, recSize, lo, hi);
        List<Sample> out = new ArrayList<>();
        if (first < 0) return out;

        List<Seg> segs = new ArrayList<>();
        int o = first;
        while (true) {
            int count = segmentCountIfValid(bb, o, len, recSize, lo, hi);
            if (count < 0) break;
            int dataOff = o + SEGMENT_HEADER_SIZE;
            segs.add(new Seg(dataOff, bb.getLong(o + SEG_FT_OFFSET), count));
            o = dataOff + count * recSize;
        }

        double prevSpacing = DEFAULT_SPACING;
        for (int k = 0; k < segs.size(); k++) {
            Seg seg = segs.get(k);
            double spacing = (k + 1 < segs.size())
                ? (double) (segs.get(k + 1).filetime() - seg.filetime()) / seg.count()
                : prevSpacing; // last segment reuses the established cadence
            long base = seg.filetime() - acqStart;
            for (int j = 0; j < seg.count(); j++) {
                long tick = base + Math.round(j * spacing);
                double val = readValue(bb, seg.dataOff() + j * recSize + valueOffset, dataType, valueWidth);
                out.add(new Sample(tick, val));
            }
            prevSpacing = spacing;
        }
        return out;
    }

    /** Offset of the value within a sample record: wide values (≥ 4 bytes)
     *  follow a leading {@code u32} tick; narrow values lead. */
    static int valueOffsetInRecord(int valueWidth) {
        return valueWidth >= 4 ? TICK_WIDTH : 0;
    }

    /** First offset whose header is valid AND followed by another valid
     *  header within {@link #MAX_SEGMENT_GAP} (a chain of ≥2), or the block
     *  end. Returns -1 if no segment chain exists. */
    private static int findFirstSegment(ByteBuffer bb, int len, int recSize, long lo, long hi) {
        for (int o = 0; o + SEGMENT_HEADER_SIZE <= len; o++) {
            int count = segmentCountIfValid(bb, o, len, recSize, lo, hi);
            if (count < 0) continue;
            int next = o + SEGMENT_HEADER_SIZE + count * recSize;
            if (next + SEGMENT_HEADER_SIZE <= len) {
                int nextCount = segmentCountIfValid(bb, next, len, recSize, lo, hi);
                if (nextCount >= 0) {
                    long gap = bb.getLong(next + SEG_FT_OFFSET) - bb.getLong(o + SEG_FT_OFFSET);
                    if (gap >= 0 && gap <= MAX_SEGMENT_GAP) return o;
                }
            } else if (next >= len - SEGMENT_HEADER_SIZE) {
                return o; // single trailing segment that runs to the block end
            }
        }
        return -1;
    }

    /** Sample count if the 12-byte window at {@code o} is a plausible segment
     *  header (FILETIME in range, count sane, body fits the block), else -1. */
    private static int segmentCountIfValid(ByteBuffer bb, int o, int len, int recSize, long lo, long hi) {
        if (o + SEGMENT_HEADER_SIZE > len) return -1;
        long ft = bb.getLong(o + SEG_FT_OFFSET);
        if (ft < lo || ft > hi) return -1;
        int count = bb.getShort(o + SEG_COUNT_OFFSET) & 0xFFFF;
        if (count < 1 || count > MAX_SEGMENT_COUNT) return -1;
        if (o + SEGMENT_HEADER_SIZE + (long) count * recSize > len) return -1;
        return count;
    }

    /**
     * One-shot convenience: decode all channels of a file given its bytes,
     * the manifest (for per-channel data types in acquisition order), and
     * the XML BOM offset (to bound the final acquisition).
     */
    public static List<DecodedChannel> decodeAll(byte[] file, SvdxManifest manifest, long xmlBomOffset) {
        BinaryHeader header = decodeHeader(file);
        List<IndexEntry> index = decodeIndex(file, header, xmlBomOffset);
        List<SvdxManifest.Channel> acqs = manifest.acquisitions();
        List<DecodedChannel> out = new ArrayList<>(index.size());
        for (int i = 0; i < index.size(); i++) {
            long[] span = channelSpan(i, header, index);
            int start = (int) span[0];
            int end = (int) Math.min(span[1], file.length);
            if (start < 0 || end <= start) continue;
            byte[] block = new byte[end - start];
            System.arraycopy(file, start, block, 0, block.length);
            String symbol = i < acqs.size() ? acqs.get(i).symbolName().orElse(null) : null;
            String dataType = i < acqs.size() ? acqs.get(i).dataType().orElse(null) : null;
            out.add(decodeChannel(block, i + 1, symbol, dataType));
        }
        return out;
    }
}
