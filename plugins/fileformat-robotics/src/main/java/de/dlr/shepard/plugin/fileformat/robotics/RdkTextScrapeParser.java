package de.dlr.shepard.plugin.fileformat.robotics;

import de.dlr.shepard.spi.fileparser.FileParserPlugin;
import de.dlr.shepard.spi.fileparser.FileParserPlugin.ParseContext;
import de.dlr.shepard.spi.fileparser.FileParserPlugin.SiblingFile;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Tier-1 parser for RoboDK station files ({@code .rdk}).
 *
 * <p><b>Scope (tier-1, RDK-PARSE-1).</b> Decompresses the zlib payload,
 * walks length-prefixed UTF-16LE string records, classifies each via a
 * predicate-specific regex, and emits one
 * {@code :SemanticAnnotation} per match on the parent
 * {@code FileReference} via the {@link AnnotationWriter} callback. The
 * eight predicates are listed in {@link RdkAnnotations}; predicates
 * marked multi-valued in the javadoc there fire once per distinct value.
 *
 * <p><b>Companion-file detection.</b> When the {@code ParseContext}
 * supplies a non-empty {@link ParseContext#siblingFiles()} listing, the
 * parser looks for any sibling whose filename equals
 * {@code <base>.xit} or {@code <base>.xit64} (case-insensitive), where
 * {@code <base>} is the {@code .rdk} basename minus its extension, and
 * emits {@code urn:shepard:rdk:companionSpatialAnalyzer} pointing at
 * that sibling's FileReference appId. This is the Spatial Analyzer
 * cross-link described in aidocs/integrations/110 §4.3.
 *
 * <p><b>Out of scope (tier-2, RDK-PARSE-2).</b> Full kinematic tree
 * extraction (joints, tool frames, target poses) requires the RoboDK
 * Python API or format reverse-engineering and is delivered as a
 * sidecar plugin per {@code feedback_plugins_declare_sidecars.md}.
 *
 * <p><b>Argue + persona consultation.</b> Two implementation shapes
 * were considered:
 *
 * <ul>
 *   <li><b>(a) Single-pass scrape with predicate regexes</b> — the
 *       shape shipped here.</li>
 *   <li><b>(b) Multi-pass classifier with confidence scoring</b> — slow
 *       and complex; appropriate when regex precision is insufficient.</li>
 * </ul>
 *
 * Choice (a) wins for tier-1 because (i) the eight predicate signals
 * are deterministic in the observed format — version literals,
 * dotted-quad endpoints, fixed-extension paths, marker-anchored
 * source-dir strings; (ii) empirical evaluation against the live
 * {@code MFZ.rdk} produced 72 structured strings to classify, not
 * 754K printable-ASCII runs — the regex stage has trivially small
 * input; (iii) the parser is best-effort and never blocks the upload,
 * so a missed predicate degrades gracefully.
 *
 * <p>Persona pushback:
 * <ul>
 *   <li>RDM: "regex matches without confidence scoring leak into the
 *       semantic store as ground truth." Counter: tier-1 emits the
 *       source mode and provenance via the standard SemanticAnnotation
 *       capture path (the {@code AnnotationWriter} implementation
 *       upstream records the {@code :Activity} per CLAUDE.md "handlers
 *       record their own Activity"); confidence is a tier-2 concern.</li>
 *   <li>API Scrutinizer: "eight opaque regexes baked into a Java class
 *       isn't auditable." Counter: regexes are constants on
 *       {@code RdkTextScrapeParser}, documented in javadoc, and tested
 *       to the {@code MFZ.rdk} fixture. Externalising them to YAML is
 *       a tier-2 add when a second {@code .rdk} variant lands.</li>
 *   <li>Plugin Designer: "the parser knows about a sibling format
 *       ({@code .xit}) — that's a layering smell." Counter: cross-file
 *       provenance (e.g. {@code metrology → digital twin registration})
 *       is a first-class concept in the SPI per aidocs/110 §6 open
 *       question 7; the companion lookup is bounded (one extension
 *       family) and discovered via the existing sibling-files SPI
 *       method, not via a hidden coupling.</li>
 * </ul>
 *
 * <p><b>Error policy.</b> Any failure (zlib error, missing strings)
 * results in zero or partial annotations but no thrown exception —
 * tier-1 is a best-effort enrichment hook, never the cause of an
 * upload failure.
 */
@ApplicationScoped
public final class RdkTextScrapeParser implements FileParserPlugin {

    /** Accepted extension (case-insensitive). */
    public static final String EXTENSION = ".rdk";

    // ── Predicate regexes ─────────────────────────────────────────────────
    //
    // Each regex matches a string emitted by {@link RdkStringExtractor}.
    // Anchors are full-string (start ^ to end $) so we don't accept
    // partial matches inside longer records.

    /** {@code 5.5.3}, {@code 5.6.0-beta1}, etc. */
    private static final Pattern APP_VERSION_RE = Pattern.compile("^\\d+\\.\\d+\\.\\d+([._-][A-Za-z0-9]+)?$");

    /** {@code WIN64}, {@code WIN32}, {@code MACOS}, {@code LINUX}. */
    private static final Pattern PLATFORM_RE = Pattern.compile("^(WIN64|WIN32|MACOS|MACOSX|LINUX)$");

    /** IPv4 with optional port; matches the RoboDK local API endpoint. */
    private static final Pattern API_ENDPOINT_RE =
            Pattern.compile("^(\\d{1,3}\\.){3}\\d{1,3}(:\\d{1,5})?$");

    /** {@code .dae} (Collada) — CAD references. */
    private static final Pattern CAD_REF_RE = Pattern.compile("(?i)^.+\\.dae$");

    /** {@code .stp} / {@code .step} — STEP geometry references. */
    private static final Pattern STEP_REF_RE = Pattern.compile("(?i)^.+\\.(stp|step)$");

    /**
     * Robot-controller / driver string: ends in {@code "Driver"} and
     * does not begin with the RoboDK virtual-control-panel prefix
     * {@code VCP_} (which marks UI slots, not drivers). Matches
     * {@code "R20_MFZDriver"}; rejects {@code "VCP_PRIMARY_ROBOT"}.
     */
    private static final Pattern ROBOT_CONTROLLER_RE = Pattern.compile("^(?!VCP_).+Driver$");

    /**
     * Marker preceding the program source directory in the RoboDK
     * variable table. The directory itself is the very next string in
     * the inflated record order.
     */
    private static final String PROGRAM_SOURCE_MARKER = "VCP_SOURCE_DIRECTORY";

    @Override
    public boolean accepts(String mimeType, String filename) {
        if (filename == null) return false;
        String lc = filename.toLowerCase(Locale.ROOT);
        return lc.endsWith(EXTENSION);
    }

    @Override
    public int parse(ParseContext ctx) {
        if (ctx == null || ctx.bytes() == null || ctx.bytes().length == 0) {
            return 0;
        }
        // The annotation subject preference is FileReference (the
        // uploaded artefact), falling back to the parent DataObject if
        // the caller could not resolve a FileReference appId.
        String subject = ctx.fileReferenceAppId()
                .or(ctx::parentDataObjectAppId)
                .orElse(null);
        if (subject == null) {
            return 0;
        }

        List<String> strings = RdkStringExtractor.extract(ctx.bytes());
        int emitted = 0;

        // ── Single-valued single-best — first match wins ──
        // App version: pick the first occurrence; later duplicate
        // entries (the format repeats some records inside section
        // headers) are ignored.
        String appVersion = firstMatch(strings, APP_VERSION_RE);
        if (appVersion != null) {
            ctx.annotations().write(subject, RdkAnnotations.APP_VERSION, appVersion);
            emitted++;
        }
        String platform = firstMatch(strings, PLATFORM_RE);
        if (platform != null) {
            ctx.annotations().write(subject, RdkAnnotations.PLATFORM, platform);
            emitted++;
        }
        String robotController = firstMatch(strings, ROBOT_CONTROLLER_RE);
        if (robotController != null) {
            ctx.annotations().write(subject, RdkAnnotations.ROBOT_CONTROLLER, robotController);
            emitted++;
        }
        String apiEndpoint = firstMatch(strings, API_ENDPOINT_RE);
        if (apiEndpoint != null) {
            ctx.annotations().write(subject, RdkAnnotations.API_ENDPOINT, apiEndpoint);
            emitted++;
        }

        // ── Marker-anchored: VCP_SOURCE_DIRECTORY → next string ──
        String programSource = nextAfterMarker(strings, PROGRAM_SOURCE_MARKER);
        if (programSource != null) {
            ctx.annotations().write(subject, RdkAnnotations.PROGRAM_SOURCE, programSource);
            emitted++;
        }

        // ── Multi-valued: emit each distinct match once (insertion-order). ──
        Set<String> cadRefs = distinctMatches(strings, CAD_REF_RE);
        for (String v : cadRefs) {
            ctx.annotations().write(subject, RdkAnnotations.CAD_REF, v);
            emitted++;
        }
        Set<String> stepRefs = distinctMatches(strings, STEP_REF_RE);
        for (String v : stepRefs) {
            ctx.annotations().write(subject, RdkAnnotations.STEP_REF, v);
            emitted++;
        }

        // ── Companion .xit / .xit64 sibling in the same FileContainer. ──
        String companion = findCompanionSpatialAnalyzer(ctx);
        if (companion != null) {
            ctx.annotations().write(subject, RdkAnnotations.COMPANION_SPATIAL_ANALYZER, companion);
            emitted++;
        }

        return emitted;
    }

    // ─── classifier helpers ──────────────────────────────────────────────

    private static String firstMatch(List<String> strings, Pattern re) {
        for (String s : strings) {
            if (re.matcher(s).matches()) return s;
        }
        return null;
    }

    private static String nextAfterMarker(List<String> strings, String marker) {
        for (int i = 0; i < strings.size() - 1; i++) {
            if (marker.equals(strings.get(i))) {
                return strings.get(i + 1);
            }
        }
        return null;
    }

    private static Set<String> distinctMatches(List<String> strings, Pattern re) {
        Set<String> out = new LinkedHashSet<>();
        for (String s : strings) {
            if (re.matcher(s).matches()) out.add(s);
        }
        return out;
    }

    /**
     * Return the FileReference appId of a sibling whose filename equals
     * {@code <base>.xit} or {@code <base>.xit64} (case-insensitive),
     * where {@code <base>} is the {@code .rdk} basename minus its
     * extension. Returns {@code null} if no such sibling is present or
     * the parser has no sibling listing.
     */
    static String findCompanionSpatialAnalyzer(ParseContext ctx) {
        String rdkName = ctx.filename();
        if (rdkName == null) return null;
        String base = stripExtension(basename(rdkName));
        if (base.isEmpty()) return null;
        String wantXit = base.toLowerCase(Locale.ROOT) + ".xit";
        String wantXit64 = base.toLowerCase(Locale.ROOT) + ".xit64";
        for (SiblingFile sib : ctx.siblingFiles()) {
            if (sib == null || sib.filename() == null) continue;
            String name = basename(sib.filename()).toLowerCase(Locale.ROOT);
            if (name.equals(wantXit) || name.equals(wantXit64)) {
                return sib.fileReferenceAppId();
            }
        }
        return null;
    }

    private static String basename(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** Visible for test / re-parse callers. */
    public static Optional<List<String>> extractStrings(byte[] fileBytes) {
        List<String> list = RdkStringExtractor.extract(fileBytes);
        return list.isEmpty() ? Optional.empty() : Optional.of(list);
    }
}
