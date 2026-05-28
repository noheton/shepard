package de.dlr.shepard.plugin.fileformat.thermography;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts the MFFD grid position from an Edevis OTvis filename.
 *
 * <p>Files in the MFFD upper-shell thermography campaign follow the
 * canonical pattern {@code S<section>_M<module>_L<layer>_F<frame>.OTvis},
 * for example {@code S4_M13_L18_F4.OTvis}. The four integers locate the
 * measurement on the (Section, Module, Layer, Frame) physical grid of
 * the carbon-fibre shell, which lets a later analysis step join
 * thermography phase-images to the AFP layup timeseries at the same
 * (S, M) tile.
 *
 * <p>The pattern is greedy: leading path segments are ignored, the
 * extension can be upper or lower case, and the integers are read
 * literally (no zero-stripping — {@code S04} stays {@code "S04"} so the
 * round-trip back into the annotation value matches the filename).
 *
 * <p>If the filename does not match the pattern, {@link #parse(String)}
 * returns {@link Optional#empty()} and the caller skips the four
 * {@code urn:shepard:mffd:*} annotations — non-MFFD OTvis files still
 * get the {@code urn:shepard:thermography:*} acquisition annotations.
 */
public final class OTvisFilenameParser {

    /**
     * Result record: the four grid components as the literal strings
     * that appeared in the filename ({@code "S4"}, {@code "M13"} etc.).
     */
    public record GridPosition(String section, String module, String layer, String frame) {}

    // The pattern matches anywhere in the input, but we anchor it to the
    // basename via a separate stripping step so leading directories don't
    // accidentally produce false positives. Capture groups intentionally
    // include the S/M/L/F prefix letter for round-trip fidelity.
    private static final Pattern PATTERN = Pattern.compile(
            "(?i)(S\\d+)_(M\\d+)_(L\\d+)_(F\\d+)\\.OTvis$");

    private OTvisFilenameParser() {
        // utility class
    }

    /**
     * Parse the grid position out of an OTvis filename.
     *
     * @param filename raw filename or path; only the basename is considered
     * @return {@link GridPosition} when the pattern matches, {@link Optional#empty()} otherwise
     */
    public static Optional<GridPosition> parse(String filename) {
        if (filename == null || filename.isEmpty()) {
            return Optional.empty();
        }
        // Strip directory segments — pattern is anchored to end of basename.
        String basename = filename;
        int slash = Math.max(basename.lastIndexOf('/'), basename.lastIndexOf('\\'));
        if (slash >= 0 && slash + 1 < basename.length()) {
            basename = basename.substring(slash + 1);
        }
        Matcher m = PATTERN.matcher(basename);
        if (!m.find()) {
            return Optional.empty();
        }
        // Normalise prefix letter to upper case so the annotation value
        // is canonical regardless of filename casing.
        return Optional.of(new GridPosition(
                m.group(1).toUpperCase(),
                m.group(2).toUpperCase(),
                m.group(3).toUpperCase(),
                m.group(4).toUpperCase()));
    }
}
