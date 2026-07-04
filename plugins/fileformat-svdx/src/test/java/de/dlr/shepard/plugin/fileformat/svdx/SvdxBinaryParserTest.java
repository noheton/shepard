package de.dlr.shepard.plugin.fileformat.svdx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.ByteArrayInputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

class SvdxBinaryParserTest {

    /** 2023-03-20 18:04:05.350 UTC — the acq-start FILETIME observed in the real file. */
    private static final long ACQ_START_FT = 0x01d95b565c084060L;

    private static byte[] buildTwoChannel(int int16Samples, int real32Samples) throws Exception {
        return SyntheticSvdxBuilder.buildWithBinary(
            List.of(
                new SyntheticSvdxBuilder.ChannelPlan("INT16", ACQ_START_FT, int16Samples, 2),
                new SyntheticSvdxBuilder.ChannelPlan("REAL32", ACQ_START_FT, real32Samples, 4)),
            SyntheticSvdxBuilder.exampleXmlTwoChannels());
    }

    @Test
    void decodesHeaderAndIndex() throws Exception {
        byte[] file = buildTwoChannel(50, 50);
        SvdxBinaryParser.BinaryHeader h = SvdxBinaryParser.decodeHeader(file);
        assertThat(h.nAcquisitions()).isEqualTo(2);

        SvdxEnvelope env = SvdxEnvelope.tryDecode(file, file.length).orElseThrow();
        List<SvdxBinaryParser.IndexEntry> index =
            SvdxBinaryParser.decodeIndex(file, h, env.xmlBomOffset());
        assertThat(index).hasSize(2);
        assertThat(index.get(0).index()).isEqualTo(1);
        // acq #1 (INT16, 50 samples) = 43-byte header + 4 segments × 12-byte
        // headers + 50 × 6 sample bytes = 43 + 48 + 300 = 391 bytes.
        assertThat(index.get(0).cumulativeEndOffset() - h.dataSectionStart()).isEqualTo(391);
        // The final acquisition ends exactly at the XML BOM offset.
        assertThat(index.get(1).cumulativeEndOffset()).isEqualTo(env.xmlBomOffset());
    }

    @Test
    void roundTripsInt16ChannelValuesAndTicks() throws Exception {
        byte[] file = buildTwoChannel(100, 10);
        SvdxManifest m = extractManifest(file);
        SvdxEnvelope env = SvdxEnvelope.tryDecode(file, file.length).orElseThrow();

        List<SvdxBinaryParser.DecodedChannel> chans =
            SvdxBinaryParser.decodeAll(file, m, env.xmlBomOffset());
        assertThat(chans).hasSize(2);

        SvdxBinaryParser.DecodedChannel ch0 = chans.get(0);
        assertThat(ch0.dataType()).isEqualTo("INT16");
        assertThat(ch0.symbolName()).isEqualTo("GVL_IO_US_Endeffektor.aTemperatureAnalogIntput1");
        assertThat(ch0.acqStartFiletime()).isEqualTo(ACQ_START_FT);
        assertThat(ch0.samples()).hasSize(100);
        // Builder writes value = channel*1000 + k; channel 0 → value == k.
        assertThat(ch0.samples().get(0).value()).isEqualTo(0.0);
        assertThat(ch0.samples().get(42).value()).isEqualTo(42.0);
        // Ticks step by 10000 (100 ns units) → 1 kHz.
        assertThat(ch0.samples().get(1).tickNs100() - ch0.samples().get(0).tickNs100())
            .isEqualTo(10000);
        assertThat(ch0.samples().get(99).tickNs100()).isEqualTo(99L * 10000);
        // Globally monotonic ACROSS segment boundaries: sample 20 lives in the
        // 2nd 16-sample segment, yet its tick is not reset — it is the
        // continuation 20·10000, proving per-segment FILETIME bases are applied.
        assertThat(ch0.samples().get(20).tickNs100()).isEqualTo(20L * 10000);
        assertThat(ch0.samples().get(20).value()).isEqualTo(20.0);
        for (int i = 1; i < ch0.samples().size(); i++) {
            assertThat(ch0.samples().get(i).tickNs100())
                .isGreaterThan(ch0.samples().get(i - 1).tickNs100());
        }
    }

    @Test
    void roundTripsReal32Channel() throws Exception {
        byte[] file = buildTwoChannel(10, 64);
        SvdxManifest m = extractManifest(file);
        SvdxEnvelope env = SvdxEnvelope.tryDecode(file, file.length).orElseThrow();

        SvdxBinaryParser.DecodedChannel ch1 =
            SvdxBinaryParser.decodeAll(file, m, env.xmlBomOffset()).get(1);
        assertThat(ch1.dataType()).isEqualTo("REAL32");
        assertThat(ch1.samples()).hasSize(64);
        // channel 1 → value = 1000 + k.
        assertThat(ch1.samples().get(0).value()).isCloseTo(1000.0, within(1e-6));
        assertThat(ch1.samples().get(63).value()).isCloseTo(1063.0, within(1e-6));
    }

    @Test
    void valueWidthMapsCommonTwincatTypes() {
        assertThat(SvdxBinaryParser.valueWidth("INT16")).isEqualTo(2);
        assertThat(SvdxBinaryParser.valueWidth("REAL32")).isEqualTo(4);
        assertThat(SvdxBinaryParser.valueWidth("REAL64")).isEqualTo(8);
        assertThat(SvdxBinaryParser.valueWidth("INT32")).isEqualTo(4);
        assertThat(SvdxBinaryParser.valueWidth("BIT")).isEqualTo(1);
        assertThat(SvdxBinaryParser.valueWidth("UINT64")).isEqualTo(8);
        assertThat(SvdxBinaryParser.valueWidth("MYSTERY")).isZero();
    }

    private static SvdxManifest extractManifest(byte[] file) throws Exception {
        SvdxEnvelope env = SvdxEnvelope.tryDecode(file, file.length).orElseThrow();
        int bodyStart = (int) env.xmlBodyOffset();
        byte[] xml = new byte[file.length - bodyStart];
        System.arraycopy(file, bodyStart, xml, 0, xml.length);
        return SvdxManifestExtractor.parseXml(new ByteArrayInputStream(xml));
    }
}
