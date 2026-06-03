package de.dlr.shepard.plugin.fileformat.svdx;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Tier-1 parser for Beckhoff TwinCAT Scope files ({@code .svdx}).
 *
 * <p><b>Scope (tier-1, MFFD-PLUGIN-SVDX-1 + MFFD-PLUGIN-SVDX-SEMANTIC-1).</b>
 * Reads the XML channel-manifest prefix that Beckhoff TwinCAT Scope v2
 * embeds before the binary data payload. Emits two annotation families on
 * the parent {@code FileReference}:
 *
 * <ul>
 *   <li><b>{@code urn:shepard:svdx:*}</b> — raw manifest metadata:
 *       channel name, symbolic name, sample rate, unit, process type,
 *       seam id (from filename).</li>
 *   <li><b>{@code urn:shepard:phys:*}</b> — physical-quantity tags inferred
 *       from Beckhoff channel-name conventions via
 *       {@link #classifyChannelPhysics(String)} (MFFD-PLUGIN-SVDX-SEMANTIC-1).
 *       Value {@code "true"} signals presence.</li>
 * </ul>
 *
 * <p><b>Format background.</b> A {@code .svdx} file begins with a UTF-8
 * XML document (the "scope manifest") immediately followed by a binary
 * payload containing the sampled data. The XML section ends at the first
 * {@code </TcScope>} closing tag. This parser extracts only the XML
 * portion via a SAX scan that stops as soon as the manifest is consumed
 * (or the binary boundary is reached).
 *
 * <p><b>Beckhoff naming conventions (MFFD-PLUGIN-SVDX-SEMANTIC-1).</b>
 * TwinCAT PLC variable names follow a systematic convention:
 * <ul>
 *   <li>Prefix character encodes data type: {@code a} = analog input,
 *       {@code r} = real (float), {@code b} = boolean, {@code n} = integer.</li>
 *   <li>The name root encodes the physical quantity:
 *       {@code Temperature/Temp} → temperature, {@code RoboPos} → position,
 *       {@code Torque} → torque, {@code Force} → force,
 *       {@code Branson} → ultrasonic actuator, {@code Pressure} → pressure,
 *       {@code Velocity} → velocity.</li>
 * </ul>
 * The classifier {@link #classifyChannelPhysics(String)} implements this
 * mapping and is package-visible for direct unit testing.
 *
 * <p><b>Out of scope (tier-2, MFFD-PARSER-SVDX1).</b> Binary payload
 * ingest into TimescaleDB requires the full SVDX binary decoder (gated on
 * MFFD-PARSER-01 + MFFD-NDRIVE-01). This parser is best-effort: any
 * failure yields zero or partial annotations and never throws.
 *
 * <p><b>Seam ID derivation.</b> The filename basename (without extension)
 * is used as the seam identifier; e.g. {@code "P08_2teBahn.svdx"} →
 * {@code seamId = "P08_2teBahn"}.
 *
 * <p><b>Error policy.</b> Any parse failure (malformed XML, binary
 * boundary before end tag, null context) results in zero or partial
 * annotations but no thrown exception — tier-1 is a best-effort
 * enrichment hook, never the cause of an upload failure.
 */
public final class SvdxManifestParser implements FileParserPlugin {

    /** Accepted extension (case-insensitive). */
    public static final String EXTENSION = ".svdx";

    /** Literal value emitted for physical-quantity predicates (presence = asserted). */
    public static final String PHYS_VALUE_TRUE = "true";

    private static final Logger LOG =
            Logger.getLogger(SvdxManifestParser.class.getName());

    @Override
    public boolean accepts(String mimeType, String filename) {
        if (filename == null) return false;
        return filename.toLowerCase(Locale.ROOT).endsWith(EXTENSION);
    }

    @Override
    public int parse(ParseContext ctx) {
        if (ctx == null || ctx.bytes() == null || ctx.bytes().length == 0) {
            return 0;
        }
        String subject = ctx.fileReferenceAppId()
                .or(ctx::parentDataObjectAppId)
                .orElse(null);
        if (subject == null) {
            return 0;
        }

        // ── Seam ID from filename ──
        int emitted = 0;
        String seamId = extractSeamId(ctx.filename());
        if (seamId != null && !seamId.isEmpty()) {
            ctx.annotations().write(subject, SvdxAnnotations.SEAM_ID, seamId);
            emitted++;
        }

        // ── Parse XML manifest prefix ──
        List<ChannelEntry> channels;
        try {
            channels = parseManifest(ctx.bytes());
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                    "SvdxManifestParser: failed to parse manifest from {0}: {1}",
                    new Object[]{ctx.filename(), e.getMessage()});
            return emitted;
        }

        // ── Emit per-channel annotations ──
        for (ChannelEntry ch : channels) {
            if (ch.name() != null && !ch.name().isBlank()) {
                ctx.annotations().write(subject, SvdxAnnotations.CHANNEL_NAME, ch.name());
                emitted++;
            }
            if (ch.symbolName() != null && !ch.symbolName().isBlank()) {
                ctx.annotations().write(subject, SvdxAnnotations.SYMBOL_NAME, ch.symbolName());
                emitted++;
            }
            if (ch.unit() != null && !ch.unit().isBlank()) {
                ctx.annotations().write(subject, SvdxAnnotations.UNIT, ch.unit());
                emitted++;
            }

            // ── Physical-quantity classifier (MFFD-PLUGIN-SVDX-SEMANTIC-1) ──
            Optional<String> physPredicate = classifyChannelPhysics(ch.name());
            if (physPredicate.isPresent()) {
                ctx.annotations().write(subject, physPredicate.get(), PHYS_VALUE_TRUE);
                emitted++;
            }
        }

        // ── Sample rate (file-level, from first channel or manifest root) ──
        channels.stream()
                .filter(ch -> ch.sampleRate() != null && !ch.sampleRate().isBlank())
                .findFirst()
                .ifPresent(ch -> {
                    ctx.annotations().write(subject, SvdxAnnotations.SAMPLE_RATE, ch.sampleRate());
                });
        if (channels.stream().anyMatch(ch -> ch.sampleRate() != null && !ch.sampleRate().isBlank())) {
            emitted++;
        }

        return emitted;
    }

    // ─── Physical-quantity classifier ──────────────────────────────────────

    /**
     * Classify a Beckhoff TwinCAT channel name to a
     * {@code urn:shepard:phys:*} predicate using the naming conventions
     * observed in MFFD CRW and spot-welding scope files.
     *
     * <p>The matching is case-insensitive. Patterns (in priority order):
     *
     * <ol>
     *   <li>Starts with {@code aTemperatureAnalog} or contains {@code Temp}
     *       → {@link SvdxAnnotations#PHYS_TEMPERATURE}</li>
     *   <li>Starts with {@code rRoboPos} or contains {@code RoboPos}
     *       → {@link SvdxAnnotations#PHYS_POSITION}</li>
     *   <li>Contains {@code Torque} or starts with {@code rTorque}
     *       → {@link SvdxAnnotations#PHYS_TORQUE}</li>
     *   <li>Contains {@code Force} or starts with {@code rForce}
     *       → {@link SvdxAnnotations#PHYS_FORCE}</li>
     *   <li>Starts with {@code aBranson} or contains {@code Branson}
     *       → {@link SvdxAnnotations#PHYS_ULTRASONIC_POWER}</li>
     *   <li>Contains {@code Pressure} or starts with {@code aPressure}
     *       → {@link SvdxAnnotations#PHYS_PRESSURE}</li>
     *   <li>Contains {@code Velocity} or starts with {@code rVelocity}
     *       → {@link SvdxAnnotations#PHYS_VELOCITY}</li>
     * </ol>
     *
     * @param channelName the raw Beckhoff channel name; may be {@code null}
     * @return the first matching {@code urn:shepard:phys:*} predicate IRI,
     *         or {@link Optional#empty()} when no pattern matches or the
     *         input is {@code null}
     */
    static Optional<String> classifyChannelPhysics(String channelName) {
        if (channelName == null) {
            return Optional.empty();
        }
        // Normalise once — all comparisons are case-insensitive
        String lower = channelName.toLowerCase(Locale.ROOT);

        // 1. Temperature: aTemperatureAnalog* or contains "temp"
        if (lower.startsWith("atemperatureanalog") || lower.contains("temp")) {
            return Optional.of(SvdxAnnotations.PHYS_TEMPERATURE);
        }
        // 2. Position: rRoboPos* or contains "robopos"
        if (lower.startsWith("rrobopos") || lower.contains("robopos")) {
            return Optional.of(SvdxAnnotations.PHYS_POSITION);
        }
        // 3. Torque: rTorque* or contains "torque"
        if (lower.startsWith("rtorque") || lower.contains("torque")) {
            return Optional.of(SvdxAnnotations.PHYS_TORQUE);
        }
        // 4. Force: rForce* or contains "force"
        if (lower.startsWith("rforce") || lower.contains("force")) {
            return Optional.of(SvdxAnnotations.PHYS_FORCE);
        }
        // 5. Ultrasonic: aBranson* or contains "branson"
        if (lower.startsWith("abranson") || lower.contains("branson")) {
            return Optional.of(SvdxAnnotations.PHYS_ULTRASONIC_POWER);
        }
        // 6. Pressure: aPressure* or contains "pressure"
        if (lower.startsWith("apressure") || lower.contains("pressure")) {
            return Optional.of(SvdxAnnotations.PHYS_PRESSURE);
        }
        // 7. Velocity: rVelocity* or contains "velocity"
        if (lower.startsWith("rvelocity") || lower.contains("velocity")) {
            return Optional.of(SvdxAnnotations.PHYS_VELOCITY);
        }

        return Optional.empty();
    }

    // ─── Manifest XML parser ────────────────────────────────────────────────

    /**
     * Parse the XML manifest prefix out of the raw {@code .svdx} bytes.
     *
     * <p>The manifest is a UTF-8 XML document that Beckhoff TwinCAT Scope v2
     * embeds at the start of the file before the binary payload. We scan for
     * the closing {@code </TcScope>} tag to bound the XML region, then parse
     * that region with a SAX handler. If no XML boundary is found, we
     * attempt to parse the whole buffer (handles pure-XML test fixtures).
     *
     * @return list of channel entries extracted from the manifest;
     *         empty list if none found
     */
    static List<ChannelEntry> parseManifest(byte[] bytes) {
        // ── Find the XML boundary ──
        // Scan for "</TcScope>" in the byte array (UTF-8 encoded).
        byte[] endTag = "</TcScope>".getBytes(StandardCharsets.UTF_8);
        int xmlEnd = indexOf(bytes, endTag);
        int xmlLength = (xmlEnd >= 0) ? xmlEnd + endTag.length : bytes.length;

        // ── SAX parse of the XML region ──
        SvdxSaxHandler handler = new SvdxSaxHandler();
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            // Hardened against XXE — no external entity resolution
            factory.setFeature(
                    "http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature(
                    "http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature(
                    "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            SAXParser saxParser = factory.newSAXParser();
            saxParser.parse(
                    new ByteArrayInputStream(bytes, 0, xmlLength), handler);
        } catch (SAXException e) {
            // Partial parse OK — the binary tail will cause a SAX error; we
            // accept partial results already collected by the handler.
            LOG.log(Level.FINE,
                    "SvdxManifestParser: SAX stopped early (binary boundary or malformed XML): {0}",
                    e.getMessage());
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                    "SvdxManifestParser: XML manifest parse failed: {0}",
                    e.getMessage());
        }
        return handler.channels();
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    /**
     * Extract the seam/pass identifier from the filename: the basename
     * without its {@code .svdx} extension.
     * {@code "P08_2teBahn.svdx"} → {@code "P08_2teBahn"}.
     */
    static String extractSeamId(String filename) {
        if (filename == null) return null;
        String base = basename(filename);
        if (base.toLowerCase(Locale.ROOT).endsWith(EXTENSION)) {
            return base.substring(0, base.length() - EXTENSION.length());
        }
        return base;
    }

    private static String basename(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    /**
     * Find the first occurrence of {@code needle} in {@code haystack},
     * returning the byte offset or {@code -1} if not found.
     */
    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    // ─── SAX handler ────────────────────────────────────────────────────────

    /**
     * SAX handler that extracts channel entries from a TwinCAT Scope
     * manifest. The expected element hierarchy (simplified):
     *
     * <pre>{@code
     * <TcScope>
     *   <SampleTime>1000000</SampleTime>   <!-- ns → Hz derivable -->
     *   <Channel>
     *     <Name>aTemperatureAnalogInput1</Name>
     *     <SymbolBased>
     *       <Symbol>MAIN.aTemperatureAnalogInput1</Symbol>
     *     </SymbolBased>
     *     <YAxis>
     *       <Unit>°C</Unit>
     *     </YAxis>
     *   </Channel>
     *   ...
     * </TcScope>
     * }</pre>
     *
     * <p>The handler is defensive: unknown element names and missing
     * optional fields are silently skipped.
     */
    private static final class SvdxSaxHandler extends DefaultHandler {

        private final List<ChannelEntry> channels = new ArrayList<>();
        private final StringBuilder text = new StringBuilder();

        // State for the channel being built
        private String currentName;
        private String currentSymbol;
        private String currentUnit;
        private String currentSampleTime; // nanoseconds string
        private boolean inChannel;
        private boolean inSymbolBased;
        private boolean inYAxis;

        List<ChannelEntry> channels() { return List.copyOf(channels); }

        @Override
        public void startElement(String uri, String localName,
                                 String qName, Attributes attributes) {
            text.setLength(0);
            switch (qName) {
                case "Channel" -> {
                    inChannel = true;
                    currentName = null;
                    currentSymbol = null;
                    currentUnit = null;
                }
                case "SymbolBased" -> inSymbolBased = true;
                case "YAxis" -> inYAxis = true;
                default -> { /* ignore */ }
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            String value = text.toString().trim();
            switch (qName) {
                case "Name" -> {
                    if (inChannel && !inSymbolBased && !inYAxis) {
                        currentName = value;
                    }
                }
                case "Symbol" -> {
                    if (inChannel && inSymbolBased) {
                        // Full PLC symbol path; strip the leading "MAIN." prefix
                        // to recover the short channel name when Name is absent.
                        if (currentSymbol == null) {
                            currentSymbol = stripPlcPrefix(value);
                        }
                    }
                }
                case "Unit" -> {
                    if (inChannel && inYAxis) {
                        currentUnit = value;
                    }
                }
                case "SampleTime" -> currentSampleTime = value; // ns
                case "SymbolBased" -> inSymbolBased = false;
                case "YAxis" -> inYAxis = false;
                case "Channel" -> {
                    inChannel = false;
                    // Resolve effective name: prefer <Name>, fall back to
                    // symbol short name derived from <Symbol>.
                    String effectiveName = (currentName != null && !currentName.isBlank())
                            ? currentName : currentSymbol;
                    channels.add(new ChannelEntry(
                            effectiveName,
                            currentSymbol,
                            sampleRateHz(currentSampleTime),
                            currentUnit));
                    currentName = null;
                    currentSymbol = null;
                    currentUnit = null;
                }
                default -> { /* ignore */ }
            }
            text.setLength(0);
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            text.append(ch, start, length);
        }

        /** Convert a PLC symbol path {@code "MAIN.rRoboPos_X"} → {@code "rRoboPos_X"}. */
        private static String stripPlcPrefix(String symbol) {
            int dot = symbol.lastIndexOf('.');
            return dot >= 0 ? symbol.substring(dot + 1) : symbol;
        }

        /**
         * Convert a TwinCAT sample time in nanoseconds to a human-readable
         * Hz string, e.g. {@code "1000000"} → {@code "1000 Hz"}.
         * Returns {@code null} if the sample time is absent or unparseable.
         */
        private static String sampleRateHz(String sampleTimeNs) {
            if (sampleTimeNs == null || sampleTimeNs.isBlank()) return null;
            try {
                long ns = Long.parseLong(sampleTimeNs.trim());
                if (ns <= 0) return null;
                long hz = 1_000_000_000L / ns;
                return hz + " Hz";
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    // ─── ChannelEntry record ─────────────────────────────────────────────────

    /**
     * Immutable snapshot of a single channel entry from the SVDX manifest.
     *
     * @param name       raw channel name (Beckhoff PLC variable name),
     *                   e.g. {@code "aTemperatureAnalogInput1"}
     * @param symbolName human-readable alias or PLC short symbol,
     *                   e.g. {@code "TC Head 01"} or {@code "rRoboPos_X"}
     * @param sampleRate sample rate as a human-readable string,
     *                   e.g. {@code "1000 Hz"}; {@code null} if absent
     * @param unit       engineering unit, e.g. {@code "°C"}; {@code null} if absent
     */
    record ChannelEntry(String name, String symbolName,
                        String sampleRate, String unit) {}
}
