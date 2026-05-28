package de.dlr.shepard.plugin.fileformat.thermography;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Parses the {@code content.xml} stream inside an Edevis OTvis tar
 * archive and emits one entry per recognised metadata field, keyed by
 * the canonical {@code urn:shepard:thermography:*} predicate IRI.
 *
 * <p>The OTvis {@code content.xml} is UTF-16 LE encoded XML; the input
 * stream is decoded as such before parsing. Several fields carry unit
 * suffixes in the source markup ({@code "30Hz"}, {@code "0.007s"},
 * {@code "70.00 %"}); the extractor strips those so the annotation
 * values are numeric-ready.
 *
 * <p>All fields are best-effort: a missing element silently produces no
 * annotation entry rather than failing the parse. Tier-1 promises the
 * caller "as much metadata as we could find" — never an exception on
 * partial files.
 */
public final class OTvisMetadataExtractor {

    /** The {@code DateTimeFormatter} accepted by Edevis CreationDate ({@code "02/07/2023 06:55:41.414"}). */
    private static final DateTimeFormatter CREATION_DATE_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss.SSS", Locale.ROOT);

    private OTvisMetadataExtractor() {
        // utility class
    }

    /**
     * Parse the UTF-16 LE XML bytes and return a predicate → value map.
     * Insertion order matches the XML element order so a downstream
     * caller can iterate deterministically.
     *
     * @param contentXmlBytes raw bytes of the {@code content.xml} stream
     * @return map of {@code urn:shepard:thermography:*} IRI to string value
     */
    public static Map<String, String> extract(byte[] contentXmlBytes) {
        Map<String, String> out = new LinkedHashMap<>();
        if (contentXmlBytes == null || contentXmlBytes.length == 0) {
            return out;
        }
        Document doc;
        try {
            doc = parseUtf16(contentXmlBytes);
        } catch (Exception e) {
            // Tier-1 promise: parse failures are not fatal. Caller will
            // get an empty map and continue with filename-derived
            // annotations only.
            return out;
        }
        Element fileInfo = firstChild(doc.getDocumentElement(), OTvisContentSchema.ELEM_FILE_INFO);
        if (fileInfo == null) {
            return out;
        }

        putRaw(out, fileInfo, OTvisContentSchema.ELEM_CAMPAIGN,
                ThermographyAnnotations.CAMPAIGN);
        putRaw(out, fileInfo, OTvisContentSchema.ELEM_MODULE_NAME,
                ThermographyAnnotations.MODULE_NAME);
        putRaw(out, fileInfo, OTvisContentSchema.ELEM_CREATING_VERSION,
                ThermographyAnnotations.CREATING_VERSION);
        putRecordingType(out, fileInfo);
        putExcitationSignalType(out, fileInfo);
        putExcitationDevice(out, fileInfo);

        putStrippedNumber(out, fileInfo, OTvisContentSchema.ELEM_FRAME_RATE,
                ThermographyAnnotations.FRAME_RATE_HZ, "Hz");
        putStrippedNumber(out, fileInfo, OTvisContentSchema.ELEM_INTEGRATION_TIME,
                ThermographyAnnotations.INTEGRATION_TIME_S, "s");
        putStrippedNumber(out, fileInfo, OTvisContentSchema.ELEM_EXCITATION_FREQUENCY,
                ThermographyAnnotations.EXCITATION_FREQUENCY_HZ, "Hz");
        putStrippedNumber(out, fileInfo, OTvisContentSchema.ELEM_EXCITATION_AMPLITUDE,
                ThermographyAnnotations.EXCITATION_AMPLITUDE_PCT, "%");

        putRaw(out, fileInfo, OTvisContentSchema.ELEM_CONDITION_PERIODS,
                ThermographyAnnotations.CONDITIONING_PERIODS);
        putRaw(out, fileInfo, OTvisContentSchema.ELEM_ACQUISITION_PERIODS,
                ThermographyAnnotations.ACQUISITION_PERIODS);

        putResolution(out, fileInfo);
        putCreationDate(out, fileInfo);

        return out;
    }

    // ─── XML helpers ─────────────────────────────────────────────────────────

    private static Document parseUtf16(byte[] bytes) throws ParserConfigurationException, SAXException, java.io.IOException {
        // The bytes are UTF-16 LE; the XML declaration may or may not be
        // present after the BOM. Decode to a String first so XML parser
        // sees pure UTF-16 (the BOM, if present at offset 0–1, is the
        // 0xFF 0xFE pair that StandardCharsets.UTF_16LE strips on
        // construction in this manner via String(bytes, charset)).
        // We feed the resulting String back through the parser as UTF-8
        // to avoid encoding-detection edge cases.
        String text;
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
            text = new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
        } else {
            text = new String(bytes, StandardCharsets.UTF_16LE);
        }
        // Strip the XML declaration's encoding attribute if it lies about
        // the encoding (some Edevis files declare utf-8 inside utf-16 bytes).
        // We re-feed the body as plain UTF-8 with no declaration so the
        // parser uses our actual encoding.
        if (text.startsWith("<?xml")) {
            int end = text.indexOf("?>");
            if (end > 0) {
                text = text.substring(end + 2);
            }
        }

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        // XML hardening — disable external entity resolution.
        try {
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            dbf.setXIncludeAware(false);
            dbf.setExpandEntityReferences(false);
        } catch (ParserConfigurationException ignored) {
            // best-effort; carry on with whatever the parser supports
        }
        DocumentBuilder db = dbf.newDocumentBuilder();
        // Suppress default SAX ErrorHandler stderr printing — tier-1
        // promise is silent best-effort. Caller catches and continues.
        db.setErrorHandler(new org.xml.sax.helpers.DefaultHandler());
        try (InputStream in = new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))) {
            return db.parse(in);
        }
    }

    private static Element firstChild(Element parent, String name) {
        if (parent == null) return null;
        NodeList kids = parent.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node n = kids.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && name.equals(n.getNodeName())) {
                return (Element) n;
            }
        }
        return null;
    }

    private static Optional<String> textOf(Element parent, String tagName) {
        Element e = firstChild(parent, tagName);
        if (e == null) return Optional.empty();
        String t = e.getTextContent();
        if (t == null) return Optional.empty();
        String trimmed = t.trim();
        if (trimmed.isEmpty()) return Optional.empty();
        return Optional.of(trimmed);
    }

    // ─── per-field extractors ────────────────────────────────────────────────

    private static void putRaw(Map<String, String> out, Element fileInfo, String tag, String predicate) {
        textOf(fileInfo, tag).ifPresent(v -> out.put(predicate, v));
    }

    /**
     * Strip a trailing unit literal (case-insensitive) and any surrounding
     * whitespace. {@code "30Hz"} → {@code "30"}; {@code "70.00 %"} → {@code "70.00"};
     * {@code "0.015Hz"} → {@code "0.015"}.
     */
    private static void putStrippedNumber(Map<String, String> out, Element fileInfo, String tag,
                                          String predicate, String unit) {
        textOf(fileInfo, tag).ifPresent(raw -> {
            String v = raw.trim();
            if (unit != null && !unit.isEmpty()
                    && v.length() >= unit.length()
                    && v.regionMatches(true, v.length() - unit.length(), unit, 0, unit.length())) {
                v = v.substring(0, v.length() - unit.length()).trim();
            }
            if (!v.isEmpty()) {
                out.put(predicate, v);
            }
        });
    }

    /** {@code Evaluation} → {@code "evaluation"}; passes others through lower-cased. */
    private static void putRecordingType(Map<String, String> out, Element fileInfo) {
        textOf(fileInfo, OTvisContentSchema.ELEM_RECORDING_TYPE).ifPresent(raw ->
                out.put(ThermographyAnnotations.RECORDING_TYPE, raw.toLowerCase(Locale.ROOT)));
    }

    /** {@code Sine}/{@code Square} → lower-cased canonical form. */
    private static void putExcitationSignalType(Map<String, String> out, Element fileInfo) {
        textOf(fileInfo, OTvisContentSchema.ELEM_EXCITATION_SIGNAL_TYPE).ifPresent(raw ->
                out.put(ThermographyAnnotations.EXCITATION_SIGNAL_TYPE, raw.toLowerCase(Locale.ROOT)));
    }

    /**
     * {@code "Halogen lamps"} → {@code "halogen"}; {@code "Flash"} → {@code "flash"};
     * {@code "Ultrasound"} → {@code "ultrasound"}. The canonical vocabulary is
     * the four values listed in aidocs/integrations/114 §4.
     */
    private static void putExcitationDevice(Map<String, String> out, Element fileInfo) {
        textOf(fileInfo, OTvisContentSchema.ELEM_EXCITATION_DEVICE_SELECTION).ifPresent(raw -> {
            String lc = raw.toLowerCase(Locale.ROOT);
            String canonical;
            if (lc.contains("halogen")) {
                canonical = "halogen";
            } else if (lc.contains("flash")) {
                canonical = "flash";
            } else if (lc.contains("ultrasound") || lc.contains("ultra-sound") || lc.contains("ultrasonic")) {
                canonical = "ultrasound";
            } else if (lc.contains("passive") || lc.isEmpty()) {
                canonical = "passive";
            } else {
                // Unknown vendor wording — emit the original lower-cased
                // value rather than dropping the field; the consumer can
                // still index on it.
                canonical = lc;
            }
            out.put(ThermographyAnnotations.EXCITATION_DEVICE, canonical);
        });
    }

    /**
     * {@code "0,0,1024,768"} → {@code "1024x768"}. The Edevis Window field
     * encodes a viewport rectangle; we project its width/height into the
     * canonical {@code WxH} resolution shape.
     */
    private static void putResolution(Map<String, String> out, Element fileInfo) {
        textOf(fileInfo, OTvisContentSchema.ELEM_WINDOW).ifPresent(raw -> {
            String[] parts = raw.split(",");
            if (parts.length != 4) return;
            try {
                int width = Integer.parseInt(parts[2].trim());
                int height = Integer.parseInt(parts[3].trim());
                if (width > 0 && height > 0) {
                    out.put(ThermographyAnnotations.RESOLUTION, width + "x" + height);
                }
            } catch (NumberFormatException ignored) {
                // skip — non-numeric window contents
            }
        });
    }

    /**
     * {@code "02/07/2023 06:55:41.414"} → ISO-8601 {@code "2023-07-02T06:55:41.414Z"}.
     * The Edevis CreationDate is rendered in the producer's locale as
     * day-first; we normalise to UTC ISO-8601 so cross-instance queries
     * are deterministic.
     */
    private static void putCreationDate(Map<String, String> out, Element fileInfo) {
        textOf(fileInfo, OTvisContentSchema.ELEM_CREATION_DATE).ifPresent(raw -> {
            try {
                LocalDateTime ldt = LocalDateTime.parse(raw.trim(), CREATION_DATE_FMT);
                out.put(ThermographyAnnotations.CREATED_AT, ldt.toInstant(ZoneOffset.UTC).toString());
            } catch (DateTimeParseException ignored) {
                // Unknown date format — drop the annotation rather than
                // emit a garbage value. A future parser revision can
                // accept more locales.
            }
        });
    }
}
