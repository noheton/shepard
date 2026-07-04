package de.dlr.shepard.plugin.fileformat.robotics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import de.dlr.shepard.spi.fileparser.FileParserPlugin;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Live-fixture acceptance test for RDK-PARSE-1.
 *
 * <p>Parses the real {@code MFZ.rdk} from the MFFD showcase raw-data
 * tree at
 * {@code /opt/shepard/examples/mffd-showcase/raw-data/mffd-data/cell/MFZ.rdk}
 * and asserts the emitted predicates reproduce the table in
 * {@code aidocs/integrations/110 §4.3} (with the namespace correction
 * documented in {@link RdkAnnotations} — {@code urn:shepard:rdk:*}
 * supersedes the {@code chameo:*} / {@code m4i:*} predicates sketched
 * in the §4.3 concept-stage doc).
 *
 * <p>The fixture is 12.1 MB and therefore NOT checked into git. The
 * test self-skips when the file is unavailable; CI can mount the
 * raw-data tree to enable it (or it runs whenever the MFFD showcase
 * data is already present on a developer workstation).
 */
class RdkTextScrapeParserMFZFixtureTest {

    private static final Path MFZ_RDK = Path.of(
            "/opt/shepard/examples/mffd-showcase/raw-data/mffd-data/cell/MFZ.rdk");

    @Test
    void mfzRdkProducesExpectedAnnotationTable() throws IOException {
        assumeTrue(Files.exists(MFZ_RDK),
                "MFZ.rdk fixture not present at " + MFZ_RDK
                        + " — install MFFD showcase raw-data tree to enable this test");

        byte[] bytes = Files.readAllBytes(MFZ_RDK);
        RdkTextScrapeParserTest.RecordingWriter writer = new RdkTextScrapeParserTest.RecordingWriter();

        // Live container 626776 holds the .rdk; the parent .xit at the
        // same level is the companion. We model the sibling as
        // present so the companion predicate fires.
        List<FileParserPlugin.SiblingFile> siblings = List.of(
                new FileParserPlugin.SiblingFile("MFZ.xit", "xit-appid-fixture-1234"));

        new RdkTextScrapeParser().parse(RdkTextScrapeParserTest.ctx(bytes, "MFZ.rdk",
                Optional.of("do-appid-mffd-cell"),
                Optional.of("rdk-fileref-626776"),
                siblings, writer));

        // Single-valued predicates — reproduce §4.3 table values verbatim.
        assertThat(writer.singleByPredicate)
                .containsEntry(RdkAnnotations.APP_VERSION, "5.5.3")
                .containsEntry(RdkAnnotations.PLATFORM, "WIN64")
                .containsEntry(RdkAnnotations.PROGRAM_SOURCE, "D:/MFFD/RoboDK/Ply 1-15")
                .containsEntry(RdkAnnotations.API_ENDPOINT, "127.0.0.1")
                .containsEntry(RdkAnnotations.ROBOT_CONTROLLER, "R20_MFZDriver")
                .containsEntry(RdkAnnotations.COMPANION_SPATIAL_ANALYZER, "xit-appid-fixture-1234");

        // Multi-valued predicates — exact set + order from the file.
        assertThat(writer.allByPredicate.get(RdkAnnotations.CAD_REF))
                .containsExactly(
                        "D:/git/RoboticsAPI_common/de.dlr.bt.au.robotcell.mfz/resources/models/MFZ_Grundkonstruktion.dae",
                        "D:/git/RoboticsAPI_common/de.dlr.bt.au.robotcell.mfz/resources/models/MFZ_Mittelschiene.dae",
                        "D:/git/RoboticsAPI_common/de.dlr.bt.au.robotcell.mfz/resources/models/MFZ_Mittelschiene_Schlitten1.dae");
        assertThat(writer.allByPredicate.get(RdkAnnotations.STEP_REF))
                .containsExactly(
                        "D:/MFFD/CAD/MTLH_MultiTape Schneideinheit.CATProduct.stp",
                        "D:/MFFD/CAD/Vermessung_GRU.CATProduct.stp");

        // All annotations should be anchored on the FileReference.
        for (RdkTextScrapeParserTest.RecordingWriter.Entry e : writer.entries) {
            assertThat(e.subject()).isEqualTo("rdk-fileref-626776");
        }
    }
}
