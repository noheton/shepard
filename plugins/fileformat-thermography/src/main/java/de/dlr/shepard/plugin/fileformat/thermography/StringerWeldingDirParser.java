package de.dlr.shepard.plugin.fileformat.thermography;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts MFFD stringer-welding metadata from a run-directory name.
 *
 * <p>Run directories in the MFFD Stringer_schweissungen corpus follow:
 * <pre>
 *   P&lt;pos&gt;[Strich][_S]_&lt;pass&gt;teBahn[_Fehler]
 * </pre>
 * where:
 * <ul>
 *   <li>{@code P&lt;pos&gt;} — stringer position index (one or more digits, e.g. {@code P02})</li>
 *   <li>{@code Strich} — optional prime variant (German for dash/prime), present when the
 *       stringer position has a secondary variant (e.g. frame side-prime)</li>
 *   <li>{@code _S} — optional side suffix, indicating the second side of a two-sided weld</li>
 *   <li>{@code _1teBahn} or {@code _2teBahn} — first or second weld pass</li>
 *   <li>{@code _Fehler} — optional defect flag (German for error); marks a defect run
 *       that will be mapped to an NCR record via {@code urn:shepard:status:defect-run}</li>
 * </ul>
 *
 * <p>Representative examples from the 759k-entry archive:
 * <pre>
 *   P01_1teBahn
 *   P02Strich_1teBahn
 *   P02Strich_S_2teBahn
 *   P02_2teBahn_Fehler
 *   P12Strich_S_2teBahn_Fehler
 * </pre>
 *
 * <p>If the input does not match the pattern, {@link #parse(String)} returns
 * {@link Optional#empty()} and the caller skips the stringer annotations.
 *
 * <p>Mirrors {@link OTvisFilenameParser} in structure: pure utility, no framework
 * dependencies, usable from any importer or annotation service.
 */
public final class StringerWeldingDirParser {

    /**
     * Parsed result from a stringer-welding directory name.
     *
     * <ul>
     *   <li>{@code positionId} — the literal P-prefixed token, e.g. {@code "P02"}</li>
     *   <li>{@code hasPrime} — {@code true} when {@code Strich} was present</li>
     *   <li>{@code hasSide} — {@code true} when the {@code _S} side token was present</li>
     *   <li>{@code weldPass} — {@code 1} or {@code 2}</li>
     *   <li>{@code isDefect} — {@code true} when {@code _Fehler} was present</li>
     * </ul>
     */
    public record StringerPosition(
            String positionId,
            boolean hasPrime,
            boolean hasSide,
            int weldPass,
            boolean isDefect) {}

    // Anchored to the full basename. Captures:
    //   group 1: digit portion of position (e.g. "02")
    //   group 2: "Strich" or null
    //   group 3: "_S" or null
    //   group 4: "1" or "2"
    //   group 5: "_Fehler" or null
    private static final Pattern PATTERN = Pattern.compile(
            "^P(\\d+)(Strich)?(_S)?_(1|2)teBahn(_Fehler)?$");

    private StringerWeldingDirParser() {
        // utility class
    }

    /**
     * Parse a stringer-welding run directory name.
     *
     * @param name raw directory name or path; only the trailing segment is considered
     * @return {@link StringerPosition} when the pattern matches, {@link Optional#empty()} otherwise
     */
    public static Optional<StringerPosition> parse(String name) {
        if (name == null || name.isEmpty()) {
            return Optional.empty();
        }
        // Strip any leading path segments so callers can pass full paths.
        String basename = name;
        int slash = Math.max(basename.lastIndexOf('/'), basename.lastIndexOf('\\'));
        if (slash >= 0 && slash + 1 < basename.length()) {
            basename = basename.substring(slash + 1);
        }
        Matcher m = PATTERN.matcher(basename);
        if (!m.matches()) {
            return Optional.empty();
        }
        return Optional.of(new StringerPosition(
                "P" + m.group(1),
                m.group(2) != null,
                m.group(3) != null,
                Integer.parseInt(m.group(4)),
                m.group(5) != null));
    }
}
