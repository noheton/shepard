package de.dlr.shepard.plugin.fileformat.svdx;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Build a synthetic .svdx byte payload by gluing:
 *
 * <ol>
 *   <li>16-byte envelope header (uint64 LE pointer-to-BOM,
 *       uint64 LE format-version stamp matching the
 *       {@code 0x__ 0x96 0x0c 0x00 …} observed pattern);</li>
 *   <li>filler bytes for the (opaque) binary sample region;</li>
 *   <li>3-byte UTF-8 BOM ({@code EF BB BF});</li>
 *   <li>caller-supplied XML body.</li>
 * </ol>
 *
 * <p>This is sufficient to exercise envelope decoding, manifest
 * extraction, and annotation emission without touching the real
 * (1 GB-class) campaign files.
 */
final class SyntheticSvdxBuilder {

    private SyntheticSvdxBuilder() {}

    /**
     * Matches the real-file byte layout at envelope offsets 8..15:
     * {@code 71 96 0c 00 00 00 00 00}, i.e. uint64 LE = 0x0000_0000_000c_9671.
     */
    static final long DEFAULT_VERSION_WORD = 0x00000000000c9671L;

    static byte[] build(int binarySectionBytes, String xmlBody) throws IOException {
        return build(binarySectionBytes, xmlBody, DEFAULT_VERSION_WORD);
    }

    static byte[] build(int binarySectionBytes, String xmlBody, long versionWord) throws IOException {
        if (binarySectionBytes < 0) {
            throw new IllegalArgumentException("binarySectionBytes < 0");
        }
        byte[] xmlBodyBytes = xmlBody.getBytes(StandardCharsets.UTF_8);
        byte[] bom = new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
        long bomOffset = 16L + binarySectionBytes;

        ByteArrayOutputStream out = new ByteArrayOutputStream(16 + binarySectionBytes + 3 + xmlBodyBytes.length);
        ByteBuffer hdr = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
        hdr.putLong(0, bomOffset);
        hdr.putLong(8, versionWord);
        out.write(hdr.array());
        // Opaque "binary samples" — zero-filled is fine.
        out.write(new byte[binarySectionBytes]);
        out.write(bom);
        out.write(xmlBodyBytes);
        return out.toByteArray();
    }

    /**
     * A minimal-but-realistic ScopeProject XML body patterned after
     * the real MFFD AFP files. The structure mirrors the empirically
     * observed shape — Channels under a chart's SubMember, with
     * AdsAcquisitions under the DataPool's SubMember.
     */
    static String exampleXmlTwoChannels() {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <ScopeProject AssemblyName="TwinCAT.Measurement.Scope.API.Model">
              <AutoSaveMode>SVDX</AutoSaveMode>
              <Guid>61ededc3-4d5a-4502-823a-263c661a692f</Guid>
              <MainServer>127.0.0.1.1.1</MainServer>
              <Name>Scope Project</Name>
              <RecordTime>6000000000</RecordTime>
              <SubMember>
                <DataPool AssemblyName="TwinCAT.Measurement.Scope.API.Model">
                  <Guid>ec7a812f-62f8-497d-9a0d-92f60425dd87</Guid>
                  <Name>DataPool</Name>
                  <SubMember>
                    <AdsAcquisition AssemblyName="TwinCAT.Measurement.Scope.API.Model">
                      <AmsNetId>169.254.165.182.1.1</AmsNetId>
                      <DataType>INT16</DataType>
                      <Name>aTemperatureAnalogIntput1</Name>
                      <SymbolName>GVL_IO_US_Endeffektor.aTemperatureAnalogIntput1</SymbolName>
                      <TargetPort>851</TargetPort>
                    </AdsAcquisition>
                    <AdsAcquisition AssemblyName="TwinCAT.Measurement.Scope.API.Model">
                      <AmsNetId>169.254.165.182.1.1</AmsNetId>
                      <DataType>REAL32</DataType>
                      <Name>rRoboPosA</Name>
                      <SymbolName>RobotData.rRoboPosA</SymbolName>
                      <TargetPort>851</TargetPort>
                    </AdsAcquisition>
                  </SubMember>
                </DataPool>
                <Chart AssemblyName="TwinCAT.Measurement.Scope.API.Model">
                  <SubMember>
                    <Axis AssemblyName="TwinCAT.Measurement.Scope.API.Model">
                      <SubMember>
                        <Channel AssemblyName="TwinCAT.Measurement.Scope.API.Model">
                          <Name>aTemperatureAnalogIntput1</Name>
                          <Guid>08bc9c09-4266-490b-b324-ce533dc179ee</Guid>
                        </Channel>
                        <Channel AssemblyName="TwinCAT.Measurement.Scope.API.Model">
                          <Name>rRoboPosA</Name>
                          <Guid>08bc9c09-4266-490b-b324-aaaaaaaaaaaa</Guid>
                        </Channel>
                      </SubMember>
                    </Axis>
                  </SubMember>
                </Chart>
              </SubMember>
            </ScopeProject>
            """;
    }

    static String exampleXmlEmpty() {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <ScopeProject AssemblyName="TwinCAT.Measurement.Scope.API.Model">
              <Name>Empty Project</Name>
              <Guid>00000000-0000-0000-0000-000000000001</Guid>
              <AutoSaveMode>SVDX</AutoSaveMode>
            </ScopeProject>
            """;
    }
}
