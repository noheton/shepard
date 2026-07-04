package de.dlr.shepard.plugin.fileformat.svdx;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;

/**
 * Decoder for the 16-byte SVDX file envelope header.
 *
 * <p>Across the 21 real {@code .svdx} files from the DLR ZLP MFFD AFP
 * ultrasonic-spot-welding campaign on 2023-03-20, the header has a
 * stable shape:
 *
 * <pre>
 *   offset 0  : uint64 LE — pointer = {@code (XML_BOM_offset - 3)}, i.e. the
 *               byte index of the UTF-8 BOM that precedes the trailing
 *               XML manifest. The XML body itself begins three bytes
 *               later at the literal "&lt;?xml" sequence. Verified across
 *               every file in the campaign (see byte-layout-notes.md
 *               §"Header decoding").
 *   offset 8  : uint64 LE — format-version stamp. The four high bytes
 *               are always 0x00000000; the low four bytes carry the
 *               TwinCAT Scope build identifier
 *               (0x0c9671, 0x0c9673, 0x0c966d observed in the campaign;
 *               byte 8 is the build-minor delta, e.g. 0x71/0x73/0x6d).
 * </pre>
 *
 * <p>This class is the smallest stable surface we can rely on without
 * solving the binary sample layout. It powers:
 *
 * <ul>
 *   <li>the {@code accepts()} cheap-reject path on the
 *       {@link FileParserPlugin} contract,</li>
 *   <li>the locator for the trailing XML manifest, which downstream
 *       parsing reads with StAX,</li>
 *   <li>the {@code urn:shepard:svdx:formatVersion} annotation.</li>
 * </ul>
 *
 * <p>If the magic does not match — bytes 8-15 do NOT have the
 * {@code 0x_???? 0x0c 0x00 0x00 0x00 0x00 0x00} pattern — the file is
 * either a different scope-tool version or not an SVDX at all and the
 * caller should skip parsing entirely.
 */
public final class SvdxEnvelope {

    /**
     * The constant high three bytes of the format-version word at
     * envelope offset 9..11 — {@code 0x96 0x0c 0x00} in little-endian.
     * This is the most reliable magic marker we observed.
     */
    public static final byte[] FORMAT_VERSION_HIGH_MARKER = new byte[] {
        (byte) 0x96, (byte) 0x0c, (byte) 0x00
    };

    /** Minimum file size for a parseable .svdx (header + tiny manifest). */
    public static final int MIN_SIZE_BYTES = 16 + 64;

    private final long xmlBomOffset;
    private final long formatVersionWord;

    private SvdxEnvelope(long xmlBomOffset, long formatVersionWord) {
        this.xmlBomOffset = xmlBomOffset;
        this.formatVersionWord = formatVersionWord;
    }

    /**
     * Byte offset of the 3-byte UTF-8 BOM that prefaces the trailing
     * XML manifest. The XML body begins at {@code xmlBomOffset() + 3}.
     */
    public long xmlBomOffset() {
        return xmlBomOffset;
    }

    /**
     * Byte offset of the literal {@code <?xml} string that begins the
     * trailing manifest. Equal to {@code xmlBomOffset() + 3}.
     */
    public long xmlBodyOffset() {
        return xmlBomOffset + 3L;
    }

    /**
     * The raw 8-byte little-endian format-version stamp at envelope
     * offset 8. Useful as the {@code urn:shepard:svdx:formatVersion}
     * annotation value (lowercased hex, big-endian display).
     */
    public long formatVersionWord() {
        return formatVersionWord;
    }

    /** Hex display of {@link #formatVersionWord()} suitable for annotations. */
    public String formatVersionHex() {
        return String.format("0x%016x", formatVersionWord);
    }

    /**
     * Attempt to decode the envelope from the first 16 bytes of an
     * SVDX file. Returns {@link Optional#empty()} when the magic
     * marker is absent — the caller should treat that as "not an SVDX
     * we recognise" and skip parsing.
     *
     * @param header  the first 16 bytes of the file (caller must
     *                supply at least 16; longer arrays are tolerated
     *                — only the first 16 are read)
     * @param totalFileSize  the file's total byte length, used to
     *                sanity-check that the decoded XML offset lies
     *                within the file
     */
    public static Optional<SvdxEnvelope> tryDecode(byte[] header, long totalFileSize) {
        if (header == null || header.length < 16) {
            return Optional.empty();
        }
        if (totalFileSize < MIN_SIZE_BYTES) {
            return Optional.empty();
        }
        // Check the three-byte high marker at offsets 9..11.
        if (header[9] != FORMAT_VERSION_HIGH_MARKER[0]
            || header[10] != FORMAT_VERSION_HIGH_MARKER[1]
            || header[11] != FORMAT_VERSION_HIGH_MARKER[2]) {
            return Optional.empty();
        }
        // High four bytes of the format-version word must be zero in
        // every observed sample.
        for (int i = 12; i < 16; i++) {
            if (header[i] != 0) {
                return Optional.empty();
            }
        }
        ByteBuffer bb = ByteBuffer.wrap(header, 0, 16).order(ByteOrder.LITTLE_ENDIAN);
        long bomOffset = bb.getLong(0);
        long versionWord = bb.getLong(8);
        // Sanity: BOM offset must be strictly within the file, with at
        // least the literal "<?xml" header still to come.
        if (bomOffset < 16 || bomOffset + 8 >= totalFileSize) {
            return Optional.empty();
        }
        return Optional.of(new SvdxEnvelope(bomOffset, versionWord));
    }
}
