package de.dlr.shepard.plugin.fileformat.svdx;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SvdxEnvelopeTest {

    @Test
    void decodesValidHeader() throws Exception {
        byte[] payload = SyntheticSvdxBuilder.build(
            1024, SyntheticSvdxBuilder.exampleXmlEmpty());
        byte[] header = new byte[16];
        System.arraycopy(payload, 0, header, 0, 16);

        Optional<SvdxEnvelope> env = SvdxEnvelope.tryDecode(header, payload.length);

        assertThat(env).isPresent();
        assertThat(env.get().xmlBomOffset()).isEqualTo(16L + 1024L);
        assertThat(env.get().xmlBodyOffset()).isEqualTo(16L + 1024L + 3L);
        assertThat(env.get().formatVersionWord()).isEqualTo(SyntheticSvdxBuilder.DEFAULT_VERSION_WORD);
        assertThat(env.get().formatVersionHex()).startsWith("0x").hasSize(18);
    }

    @Test
    void rejectsShortHeader() {
        assertThat(SvdxEnvelope.tryDecode(new byte[10], 10_000L)).isEmpty();
        assertThat(SvdxEnvelope.tryDecode(null, 10_000L)).isEmpty();
    }

    @Test
    void rejectsBelowMinFileSize() throws Exception {
        byte[] hdr = new byte[16];
        // even with a valid marker, file < MIN_SIZE_BYTES rejects.
        ByteBuffer.wrap(hdr).order(ByteOrder.LITTLE_ENDIAN)
            .putLong(0, 16L).putLong(8, 0x000000000c967100L);
        assertThat(SvdxEnvelope.tryDecode(hdr, 10L)).isEmpty();
    }

    @Test
    void rejectsWrongFormatVersionMarker() {
        // Build a header where the high-3-byte marker is wrong.
        byte[] hdr = new byte[16];
        ByteBuffer.wrap(hdr).order(ByteOrder.LITTLE_ENDIAN)
            .putLong(0, 16L + 1024L)
            .putLong(8, 0x000000DEADBEEFCAFEL);

        assertThat(SvdxEnvelope.tryDecode(hdr, 16L + 1024L + 100L)).isEmpty();
    }

    @Test
    void rejectsXmlOffsetPastEof() {
        byte[] hdr = new byte[16];
        ByteBuffer.wrap(hdr).order(ByteOrder.LITTLE_ENDIAN)
            .putLong(0, 999_999_999L)
            .putLong(8, 0x000000000c967100L);
        assertThat(SvdxEnvelope.tryDecode(hdr, 5_000L)).isEmpty();
    }

    @Test
    void acceptsObservedBuildVariants() {
        // Three minor-build bytes observed in the campaign: 0x71, 0x73, 0x6d.
        // Layout at offsets 8..15: lowByte, 0x96, 0x0c, 0x00, 0x00, 0x00, 0x00, 0x00.
        for (long lowByte : new long[] { 0x71L, 0x73L, 0x6dL }) {
            byte[] hdr = new byte[16];
            ByteBuffer bb = ByteBuffer.wrap(hdr).order(ByteOrder.LITTLE_ENDIAN);
            bb.putLong(0, 16L + 256L);
            long word = lowByte | (0x96L << 8) | (0x0cL << 16);
            bb.putLong(8, word);
            Optional<SvdxEnvelope> env = SvdxEnvelope.tryDecode(hdr, 16L + 256L + 200L);
            assertThat(env).as("build byte 0x%02x must decode".formatted(lowByte)).isPresent();
        }
    }
}
