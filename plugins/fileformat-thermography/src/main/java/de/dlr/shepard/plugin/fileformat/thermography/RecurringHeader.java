package de.dlr.shepard.plugin.fileformat.thermography;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * The 28-byte fixed header that prefixes every frame payload in an
 * Edevis {@code f<N>.bin} or {@code c<N>.bin} file (Rev H §"Frame-Datei").
 *
 * <p>Layout, little-endian throughout:
 * <pre>
 *   offset 0  : 16 ASCII bytes — magic identifier (sample: "DIFFJPBG00000001")
 *   offset 16 : uint32 LE      — Width
 *   offset 20 : uint32 LE      — Height
 *   offset 24 : uint32 LE      — DataFormat (see {@link OTvisFrameExtractor})
 * </pre>
 *
 * <p>The Rev H spec gives these fields generic names; we follow the
 * task's API surface: {@link #magic()}, {@link #field1()}, {@link #field2()},
 * {@link #field3()}. Convenience getters {@link #width()},
 * {@link #height()} and {@link #dataFormat()} alias the three integer
 * fields so consumers don't have to remember the order.
 */
public final class RecurringHeader {

    /** Header size in bytes (16 + 4 + 4 + 4). */
    public static final int HEADER_BYTES = 28;

    /** Magic length in bytes. */
    public static final int MAGIC_BYTES = 16;

    public final byte[] magic;
    public final long field1;
    public final long field2;
    public final long field3;

    /**
     * @param magic  16-byte ASCII identifier
     * @param field1 {@code uint32 LE} — image width in pixels
     * @param field2 {@code uint32 LE} — image height in pixels
     * @param field3 {@code uint32 LE} — DataFormat code per Rev H
     */
    public RecurringHeader(byte[] magic, long field1, long field2, long field3) {
        if (magic == null || magic.length != MAGIC_BYTES) {
            throw new IllegalArgumentException(
                    "magic must be exactly " + MAGIC_BYTES + " bytes");
        }
        this.magic = magic.clone();
        this.field1 = field1;
        this.field2 = field2;
        this.field3 = field3;
    }

    /** @return defensive copy of the magic identifier. */
    public byte[] magic() {
        return magic.clone();
    }

    /** @return the 16-byte magic decoded as US-ASCII (e.g. "DIFFJPBG00000001"). */
    public String magicAscii() {
        return new String(magic, StandardCharsets.US_ASCII);
    }

    /** @return image width in pixels (alias for {@link #field1}). */
    public int width() {
        return (int) field1;
    }

    /** @return image height in pixels (alias for {@link #field2}). */
    public int height() {
        return (int) field2;
    }

    /** @return DataFormat code per Rev H (alias for {@link #field3}). */
    public int dataFormat() {
        return (int) field3;
    }

    @Override
    public String toString() {
        return "RecurringHeader[magic=" + magicAscii()
                + ", width=" + width()
                + ", height=" + height()
                + ", dataFormat=" + dataFormat()
                + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RecurringHeader other)) return false;
        return field1 == other.field1
                && field2 == other.field2
                && field3 == other.field3
                && Arrays.equals(magic, other.magic);
    }

    @Override
    public int hashCode() {
        int h = Arrays.hashCode(magic);
        h = 31 * h + Long.hashCode(field1);
        h = 31 * h + Long.hashCode(field2);
        h = 31 * h + Long.hashCode(field3);
        return h;
    }
}
