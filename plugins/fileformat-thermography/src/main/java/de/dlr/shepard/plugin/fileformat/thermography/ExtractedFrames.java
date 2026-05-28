package de.dlr.shepard.plugin.fileformat.thermography;

import java.util.Collections;
import java.util.List;

/**
 * Immutable result bundle returned by {@link OTvisFrameExtractor#extract}.
 *
 * <p>Per the fail-soft contract documented in {@link OTvisFrameExtractor}
 * the extractor never throws past its public surface for a recoverable
 * issue (missing stream, malformed sub-payload, unknown DataFormat). It
 * records the issue on {@link #partialReason} instead and leaves the
 * relevant collection empty / {@code null}. A non-null
 * {@code partialReason} means "we tolerated something". A {@code null}
 * {@code partialReason} means "everything in the tar was understood".
 *
 * <p>{@link #width} and {@link #height} are sourced from the first
 * successfully decoded frame (sequence0 preferred, falling back to
 * sequence1). When no frame decodes, both are {@code 0}.
 */
public final class ExtractedFrames {

    public final List<LockInResultFrame> lockInResult;
    public final List<RawCalibratedFrame> rawCalibrated;
    public final CalibrationLut calibrationLut;
    public final int width;
    public final int height;

    /**
     * {@code null} when extraction was lossless, otherwise a
     * {@code "; "}-separated list of human-readable reasons we tolerated
     * something. The spec field is singular; multiple issues concatenate
     * into one string so the API stays a single {@code String} per the
     * task brief.
     */
    public final String partialReason;

    ExtractedFrames(
            List<LockInResultFrame> lockInResult,
            List<RawCalibratedFrame> rawCalibrated,
            CalibrationLut calibrationLut,
            int width,
            int height,
            String partialReason) {
        this.lockInResult = lockInResult == null
                ? List.of()
                : Collections.unmodifiableList(lockInResult);
        this.rawCalibrated = rawCalibrated == null
                ? List.of()
                : Collections.unmodifiableList(rawCalibrated);
        this.calibrationLut = calibrationLut;
        this.width = width;
        this.height = height;
        this.partialReason = partialReason;
    }
}
