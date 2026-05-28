package de.dlr.shepard.plugin.fileformat.robotics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Sibling-file detection tests for
 * {@link RdkTextScrapeParser#findCompanionSpatialAnalyzer}.
 */
class RdkTextScrapeParserCompanionTest {

    @Test
    void emitsCompanionWhenSameBaseXitSiblingPresent() {
        byte[] bytes = SyntheticRdkBuilder.build(List.of("5.5.3"));
        RdkTextScrapeParserTest.RecordingWriter writer = new RdkTextScrapeParserTest.RecordingWriter();
        List<FileParserPlugin.SiblingFile> siblings = List.of(
                new FileParserPlugin.SiblingFile("MFZ.xit", "xit-appid-1234"),
                new FileParserPlugin.SiblingFile("notes.txt", "notes-appid-9999")
        );
        new RdkTextScrapeParser().parse(RdkTextScrapeParserTest.ctx(bytes, "MFZ.rdk",
                Optional.empty(), Optional.of("rdk-ref"), siblings, writer));
        assertThat(writer.singleByPredicate)
                .containsEntry(RdkAnnotations.COMPANION_SPATIAL_ANALYZER, "xit-appid-1234");
    }

    @Test
    void emitsCompanionForXit64SiblingToo() {
        byte[] bytes = SyntheticRdkBuilder.build(List.of("5.5.3"));
        RdkTextScrapeParserTest.RecordingWriter writer = new RdkTextScrapeParserTest.RecordingWriter();
        List<FileParserPlugin.SiblingFile> siblings = List.of(
                new FileParserPlugin.SiblingFile("LBR_Auswertung.xit64", "xit64-appid-5555")
        );
        new RdkTextScrapeParser().parse(RdkTextScrapeParserTest.ctx(bytes, "LBR_Auswertung.rdk",
                Optional.empty(), Optional.of("rdk-ref"), siblings, writer));
        assertThat(writer.singleByPredicate)
                .containsEntry(RdkAnnotations.COMPANION_SPATIAL_ANALYZER, "xit64-appid-5555");
    }

    @Test
    void doesNotEmitCompanionWhenSiblingHasDifferentBase() {
        byte[] bytes = SyntheticRdkBuilder.build(List.of("5.5.3"));
        RdkTextScrapeParserTest.RecordingWriter writer = new RdkTextScrapeParserTest.RecordingWriter();
        List<FileParserPlugin.SiblingFile> siblings = List.of(
                new FileParserPlugin.SiblingFile("Foo.xit", "xit-appid-foo")
        );
        new RdkTextScrapeParser().parse(RdkTextScrapeParserTest.ctx(bytes, "MFZ.rdk",
                Optional.empty(), Optional.of("rdk-ref"), siblings, writer));
        assertThat(writer.singleByPredicate).doesNotContainKey(RdkAnnotations.COMPANION_SPATIAL_ANALYZER);
    }

    @Test
    void doesNotEmitCompanionWhenNoSiblings() {
        byte[] bytes = SyntheticRdkBuilder.build(List.of("5.5.3"));
        RdkTextScrapeParserTest.RecordingWriter writer = new RdkTextScrapeParserTest.RecordingWriter();
        new RdkTextScrapeParser().parse(RdkTextScrapeParserTest.ctx(bytes, "MFZ.rdk",
                Optional.empty(), Optional.of("rdk-ref"), List.of(), writer));
        assertThat(writer.singleByPredicate).doesNotContainKey(RdkAnnotations.COMPANION_SPATIAL_ANALYZER);
    }

    @Test
    void caseInsensitiveSiblingMatch() {
        byte[] bytes = SyntheticRdkBuilder.build(List.of("5.5.3"));
        RdkTextScrapeParserTest.RecordingWriter writer = new RdkTextScrapeParserTest.RecordingWriter();
        List<FileParserPlugin.SiblingFile> siblings = List.of(
                new FileParserPlugin.SiblingFile("mfz.XIT", "xit-appid-lower")
        );
        new RdkTextScrapeParser().parse(RdkTextScrapeParserTest.ctx(bytes, "MFZ.RDK",
                Optional.empty(), Optional.of("rdk-ref"), siblings, writer));
        assertThat(writer.singleByPredicate)
                .containsEntry(RdkAnnotations.COMPANION_SPATIAL_ANALYZER, "xit-appid-lower");
    }
}
