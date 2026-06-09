package de.dlr.shepard.plugin.fileformat.thermography;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class StringerWeldingDirParserTest {

    // ── Happy paths ──────────────────────────────────────────────────────────

    @Test
    void parsesSimpleFirstPass() {
        Optional<StringerWeldingDirParser.StringerPosition> r =
                StringerWeldingDirParser.parse("P01_1teBahn");
        assertThat(r).isPresent();
        assertThat(r.get().positionId()).isEqualTo("P01");
        assertThat(r.get().hasPrime()).isFalse();
        assertThat(r.get().hasSide()).isFalse();
        assertThat(r.get().weldPass()).isEqualTo(1);
        assertThat(r.get().isDefect()).isFalse();
    }

    @Test
    void parsesSimpleSecondPass() {
        Optional<StringerWeldingDirParser.StringerPosition> r =
                StringerWeldingDirParser.parse("P03_2teBahn");
        assertThat(r).isPresent();
        assertThat(r.get().positionId()).isEqualTo("P03");
        assertThat(r.get().weldPass()).isEqualTo(2);
        assertThat(r.get().isDefect()).isFalse();
    }

    @Test
    void parsesPrimeVariant() {
        Optional<StringerWeldingDirParser.StringerPosition> r =
                StringerWeldingDirParser.parse("P02Strich_1teBahn");
        assertThat(r).isPresent();
        assertThat(r.get().positionId()).isEqualTo("P02");
        assertThat(r.get().hasPrime()).isTrue();
        assertThat(r.get().hasSide()).isFalse();
        assertThat(r.get().weldPass()).isEqualTo(1);
    }

    @Test
    void parsesSideVariant() {
        Optional<StringerWeldingDirParser.StringerPosition> r =
                StringerWeldingDirParser.parse("P04_S_2teBahn");
        assertThat(r).isPresent();
        assertThat(r.get().positionId()).isEqualTo("P04");
        assertThat(r.get().hasPrime()).isFalse();
        assertThat(r.get().hasSide()).isTrue();
        assertThat(r.get().weldPass()).isEqualTo(2);
    }

    @Test
    void parsesPrimeAndSideCombined() {
        Optional<StringerWeldingDirParser.StringerPosition> r =
                StringerWeldingDirParser.parse("P12Strich_S_2teBahn");
        assertThat(r).isPresent();
        assertThat(r.get().positionId()).isEqualTo("P12");
        assertThat(r.get().hasPrime()).isTrue();
        assertThat(r.get().hasSide()).isTrue();
        assertThat(r.get().weldPass()).isEqualTo(2);
        assertThat(r.get().isDefect()).isFalse();
    }

    @Test
    void parsesDefectSuffix() {
        Optional<StringerWeldingDirParser.StringerPosition> r =
                StringerWeldingDirParser.parse("P02_2teBahn_Fehler");
        assertThat(r).isPresent();
        assertThat(r.get().positionId()).isEqualTo("P02");
        assertThat(r.get().isDefect()).isTrue();
    }

    @Test
    void parsesFullyCombined() {
        // P12 + prime + side + 2nd pass + defect — all flags set
        Optional<StringerWeldingDirParser.StringerPosition> r =
                StringerWeldingDirParser.parse("P12Strich_S_2teBahn_Fehler");
        assertThat(r).isPresent();
        assertThat(r.get().positionId()).isEqualTo("P12");
        assertThat(r.get().hasPrime()).isTrue();
        assertThat(r.get().hasSide()).isTrue();
        assertThat(r.get().weldPass()).isEqualTo(2);
        assertThat(r.get().isDefect()).isTrue();
    }

    @Test
    void stripsLeadingPathSegments() {
        Optional<StringerWeldingDirParser.StringerPosition> r =
                StringerWeldingDirParser.parse("/mnt/pve/unas/dataset/P05Strich_1teBahn");
        assertThat(r).isPresent();
        assertThat(r.get().positionId()).isEqualTo("P05");
        assertThat(r.get().hasPrime()).isTrue();
    }

    @Test
    void stripsWindowsStyleBackslashPaths() {
        Optional<StringerWeldingDirParser.StringerPosition> r =
                StringerWeldingDirParser.parse("C:\\data\\P03_S_1teBahn_Fehler");
        assertThat(r).isPresent();
        assertThat(r.get().positionId()).isEqualTo("P03");
        assertThat(r.get().hasSide()).isTrue();
        assertThat(r.get().isDefect()).isTrue();
    }

    @Test
    void handlesMultiDigitPosition() {
        Optional<StringerWeldingDirParser.StringerPosition> r =
                StringerWeldingDirParser.parse("P100_1teBahn");
        assertThat(r).isPresent();
        assertThat(r.get().positionId()).isEqualTo("P100");
    }

    // ── Negative / no-match paths ────────────────────────────────────────────

    @Test
    void returnsEmptyForNullOrBlank() {
        assertThat(StringerWeldingDirParser.parse(null)).isEmpty();
        assertThat(StringerWeldingDirParser.parse("")).isEmpty();
    }

    @Test
    void returnsEmptyForMissingPassComponent() {
        // No _NteBahn → no match
        assertThat(StringerWeldingDirParser.parse("P02Strich_S")).isEmpty();
        assertThat(StringerWeldingDirParser.parse("P02_Fehler")).isEmpty();
    }

    @Test
    void returnsEmptyForInvalidPassNumber() {
        // Only 1teBahn and 2teBahn are valid
        assertThat(StringerWeldingDirParser.parse("P02_3teBahn")).isEmpty();
        assertThat(StringerWeldingDirParser.parse("P02_0teBahn")).isEmpty();
    }

    @Test
    void returnsEmptyForMissingPPrefix() {
        assertThat(StringerWeldingDirParser.parse("02_1teBahn")).isEmpty();
        assertThat(StringerWeldingDirParser.parse("_1teBahn")).isEmpty();
    }

    @Test
    void returnsEmptyForWrongSeparator() {
        // Must use underscore between tokens
        assertThat(StringerWeldingDirParser.parse("P02-1teBahn")).isEmpty();
        assertThat(StringerWeldingDirParser.parse("P02 1teBahn")).isEmpty();
    }

    @Test
    void returnsEmptyForTrailingJunk() {
        // No extra tokens after _Fehler
        assertThat(StringerWeldingDirParser.parse("P02_1teBahn_extra")).isEmpty();
    }
}
