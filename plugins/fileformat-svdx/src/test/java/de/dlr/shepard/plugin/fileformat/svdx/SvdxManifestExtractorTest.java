package de.dlr.shepard.plugin.fileformat.svdx;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SvdxManifestExtractorTest {

    @Test
    void parsesTwoChannelExample() throws Exception {
        SvdxManifest m = SvdxManifestExtractor.parseXml(
            new ByteArrayInputStream(SyntheticSvdxBuilder.exampleXmlTwoChannels()
                .getBytes(StandardCharsets.UTF_8)));

        // 2 rendered <Channel> chart channels + 2 AdsAcquisition data sources.
        assertThat(m.channelCount()).isEqualTo(2);
        assertThat(m.acquisitionCount()).isEqualTo(2);
        assertThat(m.assemblyName()).contains("TwinCAT.Measurement.Scope.API.Model");
        assertThat(m.projectGuid()).contains("61ededc3-4d5a-4502-823a-263c661a692f");
        assertThat(m.projectName()).contains("Scope Project");
        assertThat(m.dataPoolGuid()).contains("ec7a812f-62f8-497d-9a0d-92f60425dd87");
        assertThat(m.mainServer()).contains("127.0.0.1.1.1");
        assertThat(m.recordTimeNs()).contains("6000000000");
        assertThat(m.autoSaveMode()).contains("SVDX");
        assertThat(m.amsNetIds()).containsExactly("169.254.165.182.1.1");
        assertThat(m.ports()).containsExactly("851");
        assertThat(m.dataTypes()).containsExactlyInAnyOrder("INT16", "REAL32");

        // Channel layer (chart): only Name is carried.
        assertThat(m.channels().get(0).name()).contains("aTemperatureAnalogIntput1");
        assertThat(m.channels().get(1).name()).contains("rRoboPosA");

        // Acquisition layer (ADS data sources): SymbolName + DataType.
        SvdxManifest.Channel acq0 = m.acquisitions().get(0);
        assertThat(acq0.name()).contains("aTemperatureAnalogIntput1");
        assertThat(acq0.symbolName()).contains("GVL_IO_US_Endeffektor.aTemperatureAnalogIntput1");
        assertThat(acq0.dataType()).contains("INT16");

        SvdxManifest.Channel acq1 = m.acquisitions().get(1);
        assertThat(acq1.name()).contains("rRoboPosA");
        assertThat(acq1.symbolName()).contains("RobotData.rRoboPosA");
        assertThat(acq1.dataType()).contains("REAL32");
    }

    @Test
    void parsesEmptyProject() throws Exception {
        SvdxManifest m = SvdxManifestExtractor.parseXml(
            new ByteArrayInputStream(SyntheticSvdxBuilder.exampleXmlEmpty()
                .getBytes(StandardCharsets.UTF_8)));

        assertThat(m.channelCount()).isZero();
        assertThat(m.projectName()).contains("Empty Project");
        assertThat(m.projectGuid()).contains("00000000-0000-0000-0000-000000000001");
        assertThat(m.amsNetIds()).isEmpty();
        assertThat(m.ports()).isEmpty();
        assertThat(m.dataTypes()).isEmpty();
        assertThat(m.channels()).isEmpty();
    }

    @Test
    void deduplicatesAmsNetIdAcrossAcquisitions() throws Exception {
        String xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <ScopeProject AssemblyName="X">
              <Name>Test</Name>
              <SubMember>
                <AdsAcquisition><Name>A</Name><AmsNetId>10.0.0.1.1.1</AmsNetId><TargetPort>851</TargetPort><DataType>INT16</DataType></AdsAcquisition>
                <AdsAcquisition><Name>B</Name><AmsNetId>10.0.0.1.1.1</AmsNetId><TargetPort>851</TargetPort><DataType>INT16</DataType></AdsAcquisition>
                <AdsAcquisition><Name>C</Name><AmsNetId>10.0.0.2.1.1</AmsNetId><TargetPort>852</TargetPort><DataType>REAL32</DataType></AdsAcquisition>
              </SubMember>
            </ScopeProject>
            """;
        SvdxManifest m = SvdxManifestExtractor.parseXml(
            new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        assertThat(m.amsNetIds()).containsExactly("10.0.0.1.1.1", "10.0.0.2.1.1");
        assertThat(m.ports()).containsExactly("851", "852");
        assertThat(m.dataTypes()).containsExactly("INT16", "REAL32");
        assertThat(m.acquisitionCount()).isEqualTo(3);
        assertThat(m.channelCount()).isZero();
    }

    @Test
    void rejectsXxe() {
        // Confirm external entities are disabled: malicious DOCTYPE
        // resolution would attempt a file:/// fetch; with SUPPORT_DTD
        // disabled the parser throws on the DOCTYPE token. Either
        // throw-or-skip behaviour is acceptable as long as no fetch
        // happens — we just assert the parse does not silently
        // succeed with an injected element.
        String xxe = """
            <?xml version="1.0"?>
            <!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <ScopeProject><Name>&xxe;</Name></ScopeProject>
            """;
        try {
            SvdxManifest m = SvdxManifestExtractor.parseXml(
                new ByteArrayInputStream(xxe.getBytes(StandardCharsets.UTF_8)));
            // If parse did succeed, the Name must NOT contain shell
            // tokens like "root:" that file:///etc/passwd would have.
            assertThat(m.projectName().orElse("")).doesNotContain("root:");
        } catch (Exception expected) {
            // Acceptable — DOCTYPE forbidden by XMLInputFactory config.
        }
    }
}
