package de.dlr.shepard.plugin.fileformat.thermography;

/**
 * One frame decoded from a {@code sequence1/f<N>.bin} stream carrying
 * Rev H DataFormat {@code 2} (uint16 grayscale, raw radiance counts) and
 * passed through the sequence-wide {@link CalibrationLut} to produce °C.
 *
 * <p>The MFFD sample fixture maps a 22–25 °C reference frame (shop-floor
 * room temperature) through the LUT verifying the round-trip end-to-end;
 * see {@code docs/byte-layout-notes.md §2.3} + §4.
 *
 * <p>{@link #rawCounts} is retained for traceability — consumers can
 * re-derive temperatures via {@link CalibrationLut#celsiusFor(int)} or
 * carry the raw values into downstream stores that prefer integer
 * payloads. Both arrays are row-major, length {@code width × height},
 * and not defensively copied (this is a value carrier on the hot path —
 * we trust the caller not to mutate).
 */
public final class RawCalibratedFrame {

    public final float[] temperatureCelsius;
    public final int[] rawCounts;
    public final RecurringHeader header;

    public RawCalibratedFrame(float[] temperatureCelsius, int[] rawCounts, RecurringHeader header) {
        if (temperatureCelsius == null || rawCounts == null || header == null) {
            throw new IllegalArgumentException(
                    "temperatureCelsius, rawCounts, header must be non-null");
        }
        if (temperatureCelsius.length != rawCounts.length) {
            throw new IllegalArgumentException(
                    "temperatureCelsius and rawCounts must have equal length; got "
                            + temperatureCelsius.length + " vs " + rawCounts.length);
        }
        int expected = header.width() * header.height();
        if (temperatureCelsius.length != expected) {
            throw new IllegalArgumentException(
                    "temperatureCelsius length " + temperatureCelsius.length
                            + " does not match width*height=" + expected);
        }
        this.temperatureCelsius = temperatureCelsius;
        this.rawCounts = rawCounts;
        this.header = header;
    }
}
