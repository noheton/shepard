package de.dlr.shepard.plugin.fileformat.svdx;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SvdxManifestParser}.
 *
 * <p>Covers:
 * <ul>
 *   <li>{@code classifyChannelPhysics} — all seven pattern families +
 *       null + no-match</li>
 *   <li>{@code accepts} — extension matching</li>
 *   <li>{@code extractSeamId} — filename basename stripping</li>
 *   <li>Integration: full XML manifest → annotation emission including
 *       physical-quantity predicates</li>
 * </ul>
 */
class SvdxManifestParserTest {

    // ─── RecordingWriter helper ──────────────────────────────────────────────

    static final class RecordingWriter implements FileParserPlugin.AnnotationWriter {
        record Entry(String subject, String predicate, String value) {}

        final List<Entry> entries = new ArrayList<>();
        /** Last value per predicate (for single-valued assertions). */
        final Map<String, String> singleByPredicate = new LinkedHashMap<>();
        /** All values per predicate (for multi-valued ones). */
        final Map<String, List<String>> allByPredicate = new LinkedHashMap<>();

        @Override
        public void write(String subjectAppId, String predicate, String value) {
            entries.add(new Entry(subjectAppId, predicate, value));
            singleByPredicate.put(predicate, value);
            allByPredicate.computeIfAbsent(predicate, k -> new ArrayList<>()).add(value);
        }
    }

    // ─── classifyChannelPhysics ──────────────────────────────────────────────

    /** Beckhoff analog thermocouple channel → temperature. */
    @Test
    void classifyTemperatureByFullPrefix() {
        assertThat(SvdxManifestParser.classifyChannelPhysics("aTemperatureAnalogInput1"))
                .contains(SvdxAnnotations.PHYS_TEMPERATURE);
    }

    /** Channel name containing "Temp" in any case → temperature. */
    @Test
    void classifyTemperatureByContains() {
        assertThat(SvdxManifestParser.classifyChannelPhysics("TC_TempSetpoint"))
                .contains(SvdxAnnotations.PHYS_TEMPERATURE);
    }

    /** Beckhoff robot position channel → position. */
    @Test
    void classifyPositionByPrefix() {
        assertThat(SvdxManifestParser.classifyChannelPhysics("rRoboPosA"))
                .contains(SvdxAnnotations.PHYS_POSITION);
    }

    /** Channel name containing "RoboPos" → position. */
    @Test
    void classifyPositionByContains() {
        assertThat(SvdxManifestParser.classifyChannelPhysics("Axis1_RoboPosActual"))
                .contains(SvdxAnnotations.PHYS_POSITION);
    }

    /** Beckhoff torque channel → torque. */
    @Test
    void classifyTorqueByPrefix() {
        assertThat(SvdxManifestParser.classifyChannelPhysics("rTorqueAxis1"))
                .contains(SvdxAnnotations.PHYS_TORQUE);
    }

    /** Channel name containing "Torque" → torque. */
    @Test
    void classifyTorqueByContains() {
        assertThat(SvdxManifestParser.classifyChannelPhysics("JointTorque_3"))
                .contains(SvdxAnnotations.PHYS_TORQUE);
    }

    /** Beckhoff force channel → force. */
    @Test
    void classifyForceByPrefix() {
        assertThat(SvdxManifestParser.classifyChannelPhysics("rForce_TCP"))
                .contains(SvdxAnnotations.PHYS_FORCE);
    }

    /** Channel name containing "Force" → force. */
    @Test
    void classifyForceByContains() {
        assertThat(SvdxManifestParser.classifyChannelPhysics("ConsolidationForce"))
                .contains(SvdxAnnotations.PHYS_FORCE);
    }

    /** Beckhoff Branson ultrasonic channel → ultrasonic-power. */
    @Test
    void classifyUltrasonicByBransonPrefix() {
        assertThat(SvdxManifestParser.classifyChannelPhysics("aBransonAmplitude"))
                .contains(SvdxAnnotations.PHYS_ULTRASONIC_POWER);
    }

    /** Channel name containing "Branson" → ultrasonic-power. */
    @Test
    void classifyUltrasonicByBransonContains() {
        assertThat(SvdxManifestParser.classifyChannelPhysics("US_BransonPower"))
                .contains(SvdxAnnotations.PHYS_ULTRASONIC_POWER);
    }

    /** Beckhoff pressure channel → pressure. */
    @Test
    void classifyPressureByPrefix() {
        assertThat(SvdxManifestParser.classifyChannelPhysics("aPressure_bar"))
                .contains(SvdxAnnotations.PHYS_PRESSURE);
    }

    /** Channel name containing "Pressure" → pressure. */
    @Test
    void classifyPressureByContains() {
        assertThat(SvdxManifestParser.classifyChannelPhysics("HydraulicPressure"))
                .contains(SvdxAnnotations.PHYS_PRESSURE);
    }

    /** Beckhoff velocity channel → velocity. */
    @Test
    void classifyVelocityByPrefix() {
        assertThat(SvdxManifestParser.classifyChannelPhysics("rVelocity_mm_s"))
                .contains(SvdxAnnotations.PHYS_VELOCITY);
    }

    /** Channel name containing "Velocity" → velocity. */
    @Test
    void classifyVelocityByContains() {
        assertThat(SvdxManifestParser.classifyChannelPhysics("TravelVelocity"))
                .contains(SvdxAnnotations.PHYS_VELOCITY);
    }

    /** Unrecognised channel name → empty. */
    @Test
    void classifyUnknownChannelReturnsEmpty() {
        assertThat(SvdxManifestParser.classifyChannelPhysics("unknownChannel"))
                .isEmpty();
    }

    /** Null input → empty (no NPE). */
    @Test
    void classifyNullReturnsEmpty() {
        assertThat(SvdxManifestParser.classifyChannelPhysics(null))
                .isEmpty();
    }

    /** Matching is case-insensitive. */
    @Test
    void classifyIsCaseInsensitive() {
        assertThat(SvdxManifestParser.classifyChannelPhysics("ATEMPERATUREANALOGI1"))
                .contains(SvdxAnnotations.PHYS_TEMPERATURE);
        assertThat(SvdxManifestParser.classifyChannelPhysics("RROBOPOS_X"))
                .contains(SvdxAnnotations.PHYS_POSITION);
    }

    /** Empty string → empty (no match, no error). */
    @Test
    void classifyEmptyStringReturnsEmpty() {
        assertThat(SvdxManifestParser.classifyChannelPhysics(""))
                .isEmpty();
    }

    // ─── accepts() ──────────────────────────────────────────────────────────

    @Test
    void acceptsSvdxExtensionCaseInsensitively() {
        SvdxManifestParser parser = new SvdxManifestParser();
        assertThat(parser.accepts("application/octet-stream", "P08_2teBahn.svdx")).isTrue();
        assertThat(parser.accepts(null, "scope.SVDX")).isTrue();
        assertThat(parser.accepts(null, "data.SvDx")).isTrue();
    }

    @Test
    void rejectsUnrelatedFiles() {
        SvdxManifestParser parser = new SvdxManifestParser();
        assertThat(parser.accepts("application/pdf", "report.pdf")).isFalse();
        assertThat(parser.accepts(null, null)).isFalse();
        assertThat(parser.accepts(null, "data.tdms")).isFalse();
        assertThat(parser.accepts(null, "station.rdk")).isFalse();
    }

    // ─── extractSeamId() ────────────────────────────────────────────────────

    @Test
    void extractSeamIdStripsExtension() {
        assertThat(SvdxManifestParser.extractSeamId("P08_2teBahn.svdx"))
                .isEqualTo("P08_2teBahn");
    }

    @Test
    void extractSeamIdHandlesPathSeparators() {
        assertThat(SvdxManifestParser.extractSeamId("/mnt/data/TG258_spot_001.svdx"))
                .isEqualTo("TG258_spot_001");
        assertThat(SvdxManifestParser.extractSeamId("C:\\scans\\Seam42.svdx"))
                .isEqualTo("Seam42");
    }

    @Test
    void extractSeamIdHandlesNull() {
        assertThat(SvdxManifestParser.extractSeamId(null)).isNull();
    }

    // ─── degenerate parse inputs ─────────────────────────────────────────────

    @Test
    void emptyBytesEmitNothing() {
        RecordingWriter writer = new RecordingWriter();
        int emitted = new SvdxManifestParser().parse(ctx(new byte[0], "x.svdx",
                Optional.empty(), Optional.of("ref"), writer));
        assertThat(emitted).isZero();
        assertThat(writer.entries).isEmpty();
    }

    @Test
    void garbledBytesDoNotThrow() {
        byte[] garbage = "NOT XML AT ALL  ".getBytes(StandardCharsets.UTF_8);
        RecordingWriter writer = new RecordingWriter();
        // Should not throw; may emit seamId or nothing
        new SvdxManifestParser().parse(ctx(garbage, "bad.svdx",
                Optional.empty(), Optional.of("ref"), writer));
        // No assertion on content — just confirming no exception
    }

    @Test
    void emitsNothingWhenNoSubjectAvailable() {
        byte[] xml = syntheticManifest(List.of());
        RecordingWriter writer = new RecordingWriter();
        int emitted = new SvdxManifestParser().parse(ctx(xml, "x.svdx",
                Optional.empty(), Optional.empty(), writer));
        assertThat(emitted).isZero();
    }

    @Test
    void fallsBackToDataObjectSubjectWhenFileReferenceMissing() {
        byte[] xml = syntheticManifest(List.of(
                new ChannelDef("aTemperatureAnalogInput1", null, "1000000", "°C")));
        RecordingWriter writer = new RecordingWriter();
        new SvdxManifestParser().parse(ctx(xml, "x.svdx",
                Optional.of("do-fallback"), Optional.empty(), writer));
        for (RecordingWriter.Entry e : writer.entries) {
            assertThat(e.subject()).isEqualTo("do-fallback");
        }
    }

    // ─── Integration: XML manifest → annotations ────────────────────────────

    /**
     * Full happy-path: a synthetic manifest with known channels produces
     * the expected {@code svdx:*} and {@code phys:*} annotations.
     */
    @Test
    void emitsChannelNameAndPhysAnnotationsForTemperatureChannel() {
        byte[] xml = syntheticManifest(List.of(
                new ChannelDef("aTemperatureAnalogInput1", "TC Head 01", "1000000", "°C")));

        RecordingWriter writer = new RecordingWriter();
        new SvdxManifestParser().parse(ctx(xml, "Seam01.svdx",
                Optional.empty(), Optional.of("ref-111"), writer));

        assertThat(writer.allByPredicate.get(SvdxAnnotations.CHANNEL_NAME))
                .contains("aTemperatureAnalogInput1");
        assertThat(writer.allByPredicate.get(SvdxAnnotations.SYMBOL_NAME))
                .contains("TC Head 01");
        assertThat(writer.allByPredicate.get(SvdxAnnotations.UNIT))
                .contains("°C");
        assertThat(writer.singleByPredicate.get(SvdxAnnotations.SAMPLE_RATE))
                .isEqualTo("1000 Hz");
        // Physical-quantity classifier: temperature
        assertThat(writer.singleByPredicate.get(SvdxAnnotations.PHYS_TEMPERATURE))
                .isEqualTo(SvdxManifestParser.PHYS_VALUE_TRUE);
        // Seam ID from filename
        assertThat(writer.singleByPredicate.get(SvdxAnnotations.SEAM_ID))
                .isEqualTo("Seam01");
    }

    @Test
    void emitsPositionPhysAnnotationForRoboPosChannel() {
        byte[] xml = syntheticManifest(List.of(
                new ChannelDef("rRoboPos_X", null, null, "mm")));
        RecordingWriter writer = new RecordingWriter();
        new SvdxManifestParser().parse(ctx(xml, "pass.svdx",
                Optional.empty(), Optional.of("ref-222"), writer));

        assertThat(writer.singleByPredicate.get(SvdxAnnotations.PHYS_POSITION))
                .isEqualTo(SvdxManifestParser.PHYS_VALUE_TRUE);
        assertThat(writer.singleByPredicate).doesNotContainKey(SvdxAnnotations.PHYS_TEMPERATURE);
    }

    @Test
    void emitsBransonUltrasonicPhysAnnotation() {
        byte[] xml = syntheticManifest(List.of(
                new ChannelDef("aBransonAmplitude", "US Amp", null, "%")));
        RecordingWriter writer = new RecordingWriter();
        new SvdxManifestParser().parse(ctx(xml, "us.svdx",
                Optional.empty(), Optional.of("ref-333"), writer));

        assertThat(writer.singleByPredicate.get(SvdxAnnotations.PHYS_ULTRASONIC_POWER))
                .isEqualTo(SvdxManifestParser.PHYS_VALUE_TRUE);
    }

    @Test
    void multiChannelManifestEmitsAnnotationsForEachChannel() {
        byte[] xml = syntheticManifest(List.of(
                new ChannelDef("aTemperatureAnalogInput1", "TC 01", "1000000", "°C"),
                new ChannelDef("rRoboPos_X", null, null, "mm"),
                new ChannelDef("rForce_N", null, null, "N"),
                new ChannelDef("unknownStatus", null, null, null)));
        RecordingWriter writer = new RecordingWriter();
        new SvdxManifestParser().parse(ctx(xml, "P08.svdx",
                Optional.empty(), Optional.of("ref-multi"), writer));

        assertThat(writer.allByPredicate.get(SvdxAnnotations.CHANNEL_NAME))
                .containsExactlyInAnyOrder(
                        "aTemperatureAnalogInput1", "rRoboPos_X", "rForce_N", "unknownStatus");
        assertThat(writer.allByPredicate.get(SvdxAnnotations.PHYS_TEMPERATURE))
                .hasSize(1)
                .contains(SvdxManifestParser.PHYS_VALUE_TRUE);
        assertThat(writer.allByPredicate.get(SvdxAnnotations.PHYS_POSITION))
                .hasSize(1);
        assertThat(writer.allByPredicate.get(SvdxAnnotations.PHYS_FORCE))
                .hasSize(1);
        // unknownStatus has no phys predicate
        assertThat(writer.allByPredicate)
                .doesNotContainKey(SvdxAnnotations.PHYS_VELOCITY)
                .doesNotContainKey(SvdxAnnotations.PHYS_TORQUE);
    }

    @Test
    void channelWithNoPhysMatchEmitsNoPhysPredicate() {
        byte[] xml = syntheticManifest(List.of(
                new ChannelDef("bEmergencyStop", "E-Stop", null, null)));
        RecordingWriter writer = new RecordingWriter();
        new SvdxManifestParser().parse(ctx(xml, "x.svdx",
                Optional.empty(), Optional.of("ref-444"), writer));

        assertThat(writer.singleByPredicate).doesNotContainKey(SvdxAnnotations.PHYS_TEMPERATURE);
        assertThat(writer.singleByPredicate).doesNotContainKey(SvdxAnnotations.PHYS_POSITION);
        assertThat(writer.singleByPredicate).doesNotContainKey(SvdxAnnotations.PHYS_TORQUE);
        assertThat(writer.singleByPredicate).doesNotContainKey(SvdxAnnotations.PHYS_FORCE);
        assertThat(writer.singleByPredicate).doesNotContainKey(SvdxAnnotations.PHYS_ULTRASONIC_POWER);
        assertThat(writer.singleByPredicate).doesNotContainKey(SvdxAnnotations.PHYS_PRESSURE);
        assertThat(writer.singleByPredicate).doesNotContainKey(SvdxAnnotations.PHYS_VELOCITY);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    record ChannelDef(String name, String symbolName, String sampleTimeNs, String unit) {}

    /**
     * Build a minimal TwinCAT Scope XML manifest for testing.
     * Produces valid XML that the SAX parser can consume.
     */
    static byte[] syntheticManifest(List<ChannelDef> channels) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        sb.append("<TcScope>\n");
        for (ChannelDef ch : channels) {
            sb.append("  <Channel>\n");
            if (ch.name() != null) {
                sb.append("    <Name>").append(xmlEscape(ch.name())).append("</Name>\n");
            }
            if (ch.symbolName() != null) {
                sb.append("    <SymbolBased><Symbol>MAIN.")
                  .append(xmlEscape(ch.symbolName())).append("</Symbol></SymbolBased>\n");
            }
            if (ch.sampleTimeNs() != null) {
                sb.append("    <SampleTime>").append(ch.sampleTimeNs()).append("</SampleTime>\n");
            }
            if (ch.unit() != null) {
                sb.append("    <YAxis><Unit>").append(xmlEscape(ch.unit()))
                  .append("</Unit></YAxis>\n");
            }
            sb.append("  </Channel>\n");
        }
        sb.append("</TcScope>\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String xmlEscape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static FileParserPlugin.ParseContext ctx(
            byte[] bytes, String filename,
            Optional<String> doId, Optional<String> refId,
            FileParserPlugin.AnnotationWriter writer) {
        return new FileParserPlugin.ParseContext() {
            @Override public byte[] bytes() { return bytes; }
            @Override public String filename() { return filename; }
            @Override public Optional<String> parentDataObjectAppId() { return doId; }
            @Override public Optional<String> fileReferenceAppId() { return refId; }
            @Override public FileParserPlugin.AnnotationWriter annotations() { return writer; }
        };
    }
}
