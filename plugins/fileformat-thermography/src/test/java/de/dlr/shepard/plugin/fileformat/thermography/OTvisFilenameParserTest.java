package de.dlr.shepard.plugin.fileformat.thermography;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class OTvisFilenameParserTest {

    @Test
    void parsesCanonicalMFFDFilename() {
        Optional<OTvisFilenameParser.GridPosition> g =
                OTvisFilenameParser.parse("S4_M13_L18_F4.OTvis");
        assertThat(g).isPresent();
        assertThat(g.get().section()).isEqualTo("S4");
        assertThat(g.get().module()).isEqualTo("M13");
        assertThat(g.get().layer()).isEqualTo("L18");
        assertThat(g.get().frame()).isEqualTo("F4");
    }

    @Test
    void stripsLeadingPathSegments() {
        Optional<OTvisFilenameParser.GridPosition> g =
                OTvisFilenameParser.parse("/uploads/mffd/2024/S10_M02_L7_F12.OTvis");
        assertThat(g).isPresent();
        assertThat(g.get().section()).isEqualTo("S10");
        assertThat(g.get().module()).isEqualTo("M02");
        assertThat(g.get().layer()).isEqualTo("L7");
        assertThat(g.get().frame()).isEqualTo("F12");
    }

    @Test
    void acceptsLowercaseExtensionAndUpperCasesPrefixLetter() {
        Optional<OTvisFilenameParser.GridPosition> g =
                OTvisFilenameParser.parse("s1_m2_l3_f4.otvis");
        assertThat(g).isPresent();
        assertThat(g.get().section()).isEqualTo("S1");
        assertThat(g.get().module()).isEqualTo("M2");
        assertThat(g.get().layer()).isEqualTo("L3");
        assertThat(g.get().frame()).isEqualTo("F4");
    }

    @Test
    void acceptsWindowsStyleBackslashPaths() {
        Optional<OTvisFilenameParser.GridPosition> g =
                OTvisFilenameParser.parse("C:\\Edevis\\Export\\S2_M2_L2_F2.OTvis");
        assertThat(g).isPresent();
        assertThat(g.get().section()).isEqualTo("S2");
    }

    @Test
    void returnsEmptyForNoMatch() {
        assertThat(OTvisFilenameParser.parse("measurement.OTvis")).isEmpty();
        assertThat(OTvisFilenameParser.parse("S4_M13_L18.OTvis")).isEmpty();  // missing F
        assertThat(OTvisFilenameParser.parse("S4-M13-L18-F4.OTvis")).isEmpty();  // wrong separator
    }

    @Test
    void returnsEmptyForWrongExtension() {
        assertThat(OTvisFilenameParser.parse("S4_M13_L18_F4.tar")).isEmpty();
        assertThat(OTvisFilenameParser.parse("S4_M13_L18_F4.diproj")).isEmpty();
    }

    @Test
    void returnsEmptyForNullOrBlank() {
        assertThat(OTvisFilenameParser.parse(null)).isEmpty();
        assertThat(OTvisFilenameParser.parse("")).isEmpty();
    }

    @Test
    void handlesLargeGridNumbers() {
        Optional<OTvisFilenameParser.GridPosition> g =
                OTvisFilenameParser.parse("S100_M999_L1234_F5678.OTvis");
        assertThat(g).isPresent();
        assertThat(g.get().section()).isEqualTo("S100");
        assertThat(g.get().module()).isEqualTo("M999");
        assertThat(g.get().layer()).isEqualTo("L1234");
        assertThat(g.get().frame()).isEqualTo("F5678");
    }
}
