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

    /** A single channel's recording plan for the synthetic binary section. */
    record ChannelPlan(String dataType, long acqStartFiletime, int sampleCount, int valueWidth) {}

    /**
     * Build a synthetic .svdx whose binary section follows the layout
     * reverse-engineered in {@code docs/byte-layout-notes.md}: an
     * acquisition-index header + one block per channel, each block being a
     * 43-byte header (version tag + three FILETIMEs) followed by a single
     * clean 1 kHz sample run of {@code [value][u32 tick]} records.
     *
     * <p>The value written for sample {@code k} of channel {@code c} is
     * {@code c * 1000 + k} (encoded in the channel's data type), so a
     * round-trip test can assert exact recovery.
     *
     * @param plans   one entry per channel/acquisition, in document order
     * @param xmlBody the trailing manifest (must declare the same channels)
     */
    static byte[] buildWithBinary(java.util.List<ChannelPlan> plans, String xmlBody) throws IOException {
        // 1. Lay out each channel block: 43-byte header, then 16-sample
        //    segments — each a 12-byte [_][u64 FILETIME][u16 count] header
        //    plus count × (vw+4) samples — exactly as the real files do.
        //    Sample value = channel*1000 + globalIndex; within-segment ticks
        //    restart at 0; the segment FILETIME advances so the decoder's
        //    absolute tick recovers globalIndex*10000.
        final int SEG = 16;
        final long PERIOD = 10000L; // 100ns units = 1 ms (1 kHz)
        java.util.List<byte[]> blocks = new java.util.ArrayList<>();
        for (int c = 0; c < plans.size(); c++) {
            ChannelPlan p = plans.get(c);
            int rec = p.valueWidth() + 4;
            int nSegs = (p.sampleCount() + SEG - 1) / SEG;
            int blockLen = 43 + nSegs * SvdxBinaryParser.SEGMENT_HEADER_SIZE
                + p.sampleCount() * rec;
            ByteBuffer blk = ByteBuffer.allocate(blockLen).order(ByteOrder.LITTLE_ENDIAN);
            blk.put("01.00.00.40".getBytes(StandardCharsets.US_ASCII)); // 11 bytes
            blk.putLong(SvdxBinaryParser.FT_ACQ_START, p.acqStartFiletime());
            blk.putLong(SvdxBinaryParser.FT_WINDOW_START, p.acqStartFiletime() + 1_000_000L);
            blk.putLong(SvdxBinaryParser.FT_WINDOW_END, p.acqStartFiletime() + 2_000_000L);
            int o = 43;
            int g = 0;
            while (g < p.sampleCount()) {
                int segCount = Math.min(SEG, p.sampleCount() - g);
                long segFt = p.acqStartFiletime() + g * PERIOD;
                blk.putShort(o, (short) 0);                 // leading u16 (prev value; unused)
                blk.putLong(o + 2, segFt);                  // segment FILETIME base
                blk.putShort(o + 10, (short) segCount);     // sample count
                int dataOff = o + SvdxBinaryParser.SEGMENT_HEADER_SIZE;
                for (int j = 0; j < segCount; j++) {
                    int rec0 = dataOff + j * rec;
                    writeValue(blk, rec0, p.dataType(), c * 1000 + g + j);
                    blk.putInt(rec0 + p.valueWidth(), (int) (j * PERIOD)); // within-segment tick
                }
                o = dataOff + segCount * rec;
                g += segCount;
            }
            blocks.add(blk.array());
        }

        // 2. Acquisition-index header (20 bytes) + index records.
        int n = plans.size();
        // envelope(16) + header(20) + index((n-1)*20) + u32 trailer(4)
        long dataStart = 16L + 20L + 20L * (n - 1) + 4L;
        // cumulative end offsets
        long[] cum = new long[n];
        long pos = dataStart;
        for (int i = 0; i < n; i++) { pos += blocks.get(i).length; cum[i] = pos; }

        ByteArrayOutputStream binary = new ByteArrayOutputStream();
        ByteBuffer head = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN);
        head.putInt(0, n);                       // file 0x10
        head.putLong(4, dataStart);              // file 0x14
        head.putLong(12, blocks.get(0).length);  // file 0x1c size_of_record_1
        binary.write(head.array());
        ByteBuffer idx = ByteBuffer.allocate(20 * (n - 1) + 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < n - 1; i++) {
            int base = i * 20;
            idx.putInt(base, i + 1);
            idx.putLong(base + 4, cum[i]);
            idx.putLong(base + 12, blocks.get(i + 1).length);
        }
        idx.putInt(20 * (n - 1), n); // trailer
        binary.write(idx.array());
        for (byte[] b : blocks) binary.write(b);

        // 3. Assemble the full file: envelope + actual binary section + BOM + XML.
        byte[] binarySection = binary.toByteArray();
        byte[] xmlBodyBytes = xmlBody.getBytes(StandardCharsets.UTF_8);
        byte[] bom = new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
        long bomOffset = 16L + binarySection.length;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteBuffer hdr = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
        hdr.putLong(0, bomOffset);
        hdr.putLong(8, DEFAULT_VERSION_WORD);
        out.write(hdr.array());
        out.write(binarySection);
        out.write(bom);
        out.write(xmlBodyBytes);
        return out.toByteArray();
    }

    private static void writeValue(ByteBuffer bb, int off, String dataType, long v) {
        switch (dataType.trim().toUpperCase()) {
            case "BIT", "BOOL", "BYTE", "USINT", "SINT" -> bb.put(off, (byte) v);
            case "INT16", "INT", "UINT16", "UINT", "WORD" -> bb.putShort(off, (short) v);
            case "INT32", "DINT", "UINT32", "UDINT", "DWORD" -> bb.putInt(off, (int) v);
            case "REAL32", "REAL" -> bb.putFloat(off, (float) v);
            case "REAL64", "LREAL" -> bb.putDouble(off, (double) v);
            case "INT64", "LINT", "UINT64", "ULINT", "LWORD" -> bb.putLong(off, v);
            default -> throw new IllegalArgumentException("unsupported synthetic dtype " + dataType);
        }
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
