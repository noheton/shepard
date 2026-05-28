package de.dlr.shepard.plugin.fileformat.thermography;

/**
 * Edevis OTvis sequence-wide calibration look-up table.
 *
 * <p>Per Rev H §"Datei-Aufbau" the calibration file is a bare array of
 * exactly 65,536 little-endian {@code float32} values mapping raw
 * {@code uint16} pixel counts to °C. No header, no padding. See
 * {@code docs/byte-layout-notes.md §4} for the confirmed layout against
 * the MFFD fixture (which anchors to −273.15 °C at index 0 and reaches
 * ~382 °C at index 65,535, step ~0.01 °C / count).
 *
 * <p>Value objects produced by {@link OTvisFrameExtractor} expose this
 * LUT alongside the calibrated frames so consumers can re-derive
 * temperatures from {@link RawCalibratedFrame#rawCounts} without having
 * to re-walk the tar.
 */
public final class CalibrationLut {

    /** Number of entries in the LUT (every possible {@code uint16}). */
    public static final int LUT_SIZE = 65_536;

    private final float[] lut;

    /**
     * Wraps a 65,536-entry °C LUT. The array is defensively copied so
     * downstream code cannot mutate the cached LUT a frame extractor
     * holds onto.
     *
     * @param lut LUT contents; must have exactly {@value #LUT_SIZE} entries
     * @throws IllegalArgumentException if the length is wrong
     */
    public CalibrationLut(float[] lut) {
        if (lut == null || lut.length != LUT_SIZE) {
            throw new IllegalArgumentException(
                    "CalibrationLut requires exactly " + LUT_SIZE + " entries, got "
                            + (lut == null ? "null" : lut.length));
        }
        this.lut = lut.clone();
    }

    /**
     * Map a raw {@code uint16} pixel count to its °C reading.
     *
     * @param rawCount raw value in {@code [0, 65535]}; values outside the
     *                 range are masked to 16 bits (this is what producers
     *                 do — the sensor cannot emit anything else)
     * @return calibrated temperature in °C
     */
    public float celsiusFor(int rawCount) {
        return lut[rawCount & 0xFFFF];
    }

    /**
     * @return {@code true} when the LUT is non-decreasing across its full
     *         length, which is the documented Rev H invariant.
     *         {@link OTvisFrameExtractor} validates this on load and
     *         records a {@code partialReason} if it is violated.
     */
    public boolean isMonotonic() {
        for (int i = 1; i < lut.length; i++) {
            if (lut[i] < lut[i - 1]) return false;
        }
        return true;
    }

    /** @return the minimum °C in the LUT (typically ≈ −273.15). */
    public float min() {
        float m = Float.POSITIVE_INFINITY;
        for (float v : lut) if (v < m) m = v;
        return m;
    }

    /** @return the maximum °C in the LUT (typically ≈ +382 for MFFD captures). */
    public float max() {
        float m = Float.NEGATIVE_INFINITY;
        for (float v : lut) if (v > m) m = v;
        return m;
    }

    /**
     * @return a copy of the underlying LUT. Returned array is safe to
     *         mutate; the canonical copy held by this object is not
     *         modified.
     */
    public float[] toArray() {
        return lut.clone();
    }
}
