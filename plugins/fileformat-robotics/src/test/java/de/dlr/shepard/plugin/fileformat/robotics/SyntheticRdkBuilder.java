package de.dlr.shepard.plugin.fileformat.robotics;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Build a minimal {@code .rdk}-shaped fixture for unit tests: 4-byte
 * custom magic + zlib(deflate) of a sequence of length-prefixed
 * UTF-16LE string records.
 *
 * <p>The output is decoded by {@link RdkStringExtractor} as if it were
 * a real station file. Records are emitted in the order supplied.
 */
final class SyntheticRdkBuilder {

    private SyntheticRdkBuilder() { /* utility */ }

    /** The 4-byte custom magic observed on {@code MFZ.rdk}. */
    static final byte[] MAGIC = new byte[] { 0x03, 0x25, 0x10, (byte) 0xA5 };

    /**
     * Encode each string as {@code [uint32 LE byte-length][UTF-16LE bytes]},
     * concatenate, then prepend the 4-byte magic and append zlib-deflated
     * record stream.
     */
    static byte[] build(List<String> strings) {
        ByteArrayOutputStream uncompressed = new ByteArrayOutputStream();
        for (String s : strings) {
            byte[] payload = s.getBytes(StandardCharsets.UTF_16LE);
            ByteBuffer hdr = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
            hdr.putInt(payload.length);
            uncompressed.write(hdr.array(), 0, 4);
            uncompressed.write(payload, 0, payload.length);
        }
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try {
            compressed.write(MAGIC, 0, MAGIC.length);
            Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION);
            try (DeflaterOutputStream dos = new DeflaterOutputStream(compressed, deflater)) {
                dos.write(uncompressed.toByteArray());
            } finally {
                deflater.end();
            }
        } catch (IOException impossible) {
            throw new RuntimeException(impossible);
        }
        return compressed.toByteArray();
    }
}
