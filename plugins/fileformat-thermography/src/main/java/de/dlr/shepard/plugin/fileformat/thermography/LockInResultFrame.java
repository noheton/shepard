package de.dlr.shepard.plugin.fileformat.thermography;

/**
 * One frame decoded from a {@code sequence0/f<N>.bin} stream carrying
 * Rev H DataFormat {@code 13} (complex float, {@code {float Real; float Imag}}).
 *
 * <p>The Edevis lock-in pipeline reduces the temporal stack to a single
 * complex image where each pixel encodes amplitude and phase relative to
 * the excitation signal. We derive both at decode time so consumers
 * never have to remember the order of {@code re}/{@code im} or whether
 * the producer stores {@code phase} or {@code arg}:
 *
 * <pre>
 *   amplitude[i] = hypot(re[i], im[i])
 *   phase[i]     = atan2(im[i], re[i])   // strictly in (-π, +π]
 * </pre>
 *
 * <p>Both arrays are row-major, length {@code width × height}, and
 * immutable from the consumer's perspective. Phase is always in radians;
 * the spec does not promise wrapping behaviour beyond what
 * {@link Math#atan2(double, double)} guarantees.
 */
public final class LockInResultFrame {

    public final float[] amplitude;
    public final float[] phase;
    public final RecurringHeader header;

    public LockInResultFrame(float[] amplitude, float[] phase, RecurringHeader header) {
        if (amplitude == null || phase == null || header == null) {
            throw new IllegalArgumentException("amplitude, phase, header must be non-null");
        }
        if (amplitude.length != phase.length) {
            throw new IllegalArgumentException(
                    "amplitude and phase must have equal length; got "
                            + amplitude.length + " vs " + phase.length);
        }
        int expected = header.width() * header.height();
        if (amplitude.length != expected) {
            throw new IllegalArgumentException(
                    "amplitude length " + amplitude.length
                            + " does not match width*height=" + expected);
        }
        this.amplitude = amplitude;
        this.phase = phase;
        this.header = header;
    }
}
