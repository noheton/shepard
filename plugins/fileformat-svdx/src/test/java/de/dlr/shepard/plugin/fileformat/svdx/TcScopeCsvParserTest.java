package de.dlr.shepard.plugin.fileformat.svdx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TcScopeCsvParser}. Fixtures are hand-built TSV
 * blobs that mirror the shape of a TwinCAT Scope Export Tool emission
 * (verified against the three real {@code .csv} files in the MFFD AFP
 * ultrasonic-spot-welding corpus at
 * {@code /mnt/pve/unas/dump/dataset/Punktschweißungen/} on 2026-06-02).
 *
 * <p>Note on the data section: the parser expects {@code <idx>\t<value>}
 * column pairs in each data row, which is the exact wire shape of the
 * Scope-Tool output. Tests assemble these by row to mirror that layout
 * verbatim. See {@code byte-layout-notes.md} for the format reference.
 */
class TcScopeCsvParserTest {

  /** 2-channel CSV with a known FILETIME. Real "Starttime of export" from the corpus. */
  private static final String HAPPY_2CH = String.join("\n",
      "Name\tScope Project_AutoSave_19_03_26\t",
      "File\tD:\\autosave\\Scope Project_AutoSave_19_03_26.csv\t",
      "Starttime of export\t133238090039920000\tMontag, 20. März 2023\t19:03:23.992",
      "Endtime of export\t133238090060450000\tMontag, 20. März 2023\t19:03:26.045",
      "",
      "Name\tchA\tName\tchB",
      "SymbolComment\t\tSymbolComment\t Thermocouple1",
      "Data-Type\tINT16\tData-Type\tREAL32",
      "SampleTime[ms]\t1\tSampleTime[ms]\t1",
      "SymbolName\tGVL.chA\tSymbolName\tGVL.chB",
      "NetID\t169.254.165.182.1.1\tNetID\t169.254.165.182.1.1",
      "Port\t851\tPort\t851",
      "",
      "0\t23201\t0\t77,567",
      "1\t23202\t1\t77,568",
      "2\t23203\t2\t77,569"
  );

  @Test
  void parsesHappyTwoChannelCsv() {
    TcScopeCsvParser.ParsedScopeCsv p = TcScopeCsvParser.parse(
        new ByteArrayInputStream(HAPPY_2CH.getBytes(StandardCharsets.UTF_8)));

    assertThat(p.projectName()).contains("Scope Project_AutoSave_19_03_26");
    assertThat(p.sourceFilePath()).isPresent();
    assertThat(p.startTimeFileTime()).isEqualTo(133238090039920000L);
    assertThat(p.channels()).hasSize(2);
    assertThat(p.channelCount()).isEqualTo(2);

    TcScopeCsvParser.Channel a = p.channels().get(0);
    assertThat(a.name()).isEqualTo("chA");
    assertThat(a.symbolName()).isEqualTo("GVL.chA");
    assertThat(a.dataType()).isEqualTo("INT16");
    assertThat(a.netId()).isEqualTo("169.254.165.182.1.1");
    assertThat(a.port()).isEqualTo("851");
    assertThat(a.sampleTimeMs()).isEqualTo(1);
    assertThat(a.values()).containsExactly(23201L, 23202L, 23203L);

    TcScopeCsvParser.Channel b = p.channels().get(1);
    assertThat(b.dataType()).isEqualTo("REAL32");
    assertThat(b.values()).containsExactly(77.567, 77.568, 77.569);

    assertThat(p.maxRowCount()).isEqualTo(3);
  }

  @Test
  void fileTimeRoundsToCorrectUnixNanoseconds() {
    // 133238090039920000 → 2023-03-20T18:03:23.992Z = 1679335403992 ms = 1679335403992000000 ns
    long unixNs = TcScopeCsvParser.fileTimeToUnixNanos(133238090039920000L);
    assertThat(unixNs).isEqualTo(1_679_335_403_992_000_000L);
  }

  @Test
  void rejectsFileTimeBeforeUnixEpoch() {
    assertThatThrownBy(() -> TcScopeCsvParser.fileTimeToUnixNanos(0L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void absoluteTimestampPerSampleIndex() {
    TcScopeCsvParser.ParsedScopeCsv p = TcScopeCsvParser.parse(
        new ByteArrayInputStream(HAPPY_2CH.getBytes(StandardCharsets.UTF_8)));
    long t0 = p.sampleTimestampNs(0, 1);
    long t1 = p.sampleTimestampNs(1, 1);
    assertThat(t1 - t0).isEqualTo(1_000_000L); // 1ms = 1e6 ns
  }

  @Test
  void parsesBitChannelAsBoolean() {
    String csv = String.join("\n",
        "Name\tx\t",
        "Starttime of export\t133238090039920000\t",
        "Endtime of export\t133238090060450000\t",
        "",
        "Name\tflag",
        "Data-Type\tBIT",
        "SampleTime[ms]\t1",
        "SymbolName\tGVL.flag",
        "NetID\t169.254.165.182.1.1",
        "Port\t851",
        "",
        "0\t0",
        "1\t1",
        "2\t1"
    );
    var p = TcScopeCsvParser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
    assertThat(p.channels()).hasSize(1);
    assertThat(p.channels().get(0).values()).containsExactly(false, true, true);
  }

  @Test
  void parsesUint64Channel() {
    String csv = String.join("\n",
        "Name\tx\t",
        "Starttime of export\t133238090039920000\t",
        "Endtime of export\t133238090060450000\t",
        "",
        "Name\tts",
        "Data-Type\tUINT64",
        "SampleTime[ms]\t1",
        "SymbolName\tGVL.ts",
        "NetID\t169.254.165.182.1.1",
        "Port\t851",
        "",
        "0\t1234567890",
        "1\t1234567891"
    );
    var p = TcScopeCsvParser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
    assertThat(p.channels().get(0).values()).containsExactly(1234567890L, 1234567891L);
  }

  @Test
  void emptyCsvReturnsNoChannels() {
    String csv = String.join("\n",
        "Name\tjust-header\t",
        "Starttime of export\t133238090039920000\t",
        "Endtime of export\t133238090040000000\t",
        "",
        "Name\tonly",
        "Data-Type\tINT16",
        "SampleTime[ms]\t1",
        "SymbolName\tGVL.only",
        "NetID\t169.254.165.182.1.1",
        "Port\t851",
        ""
    );
    var p = TcScopeCsvParser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
    assertThat(p.channels()).hasSize(1);
    assertThat(p.channels().get(0).values()).isEmpty();
    assertThat(p.maxRowCount()).isZero();
  }

  @Test
  void missingStartTimeRejected() {
    String csv = "Name\tnostart\t\n\nName\tx\nData-Type\tINT16\nSampleTime[ms]\t1\nSymbolName\tg.x\nNetID\t1.1.1.1.1.1\nPort\t851\n\n0\t1\n";
    assertThatThrownBy(() -> TcScopeCsvParser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8))))
        .isInstanceOf(TcScopeCsvParser.CsvParseException.class)
        .hasMessageContaining("Starttime");
  }

  @Test
  void malformedNumericFallsBackToString() {
    Object v = TcScopeCsvParser.parseValue("not-a-number", "REAL32");
    assertThat(v).isEqualTo("not-a-number");
  }

  @Test
  void unknownDataTypeFallsBackToNumericThenString() {
    assertThat(TcScopeCsvParser.parseValue("42,5", "FOO")).isEqualTo(42.5);
    assertThat(TcScopeCsvParser.parseValue("abc", "FOO")).isEqualTo("abc");
  }

  @Test
  void emptyValueParsesToNull() {
    assertThat(TcScopeCsvParser.parseValue("", "INT16")).isNull();
    assertThat(TcScopeCsvParser.parseValue("   ", "INT16")).isNull();
  }

  @Test
  void jaggedHeaderRowPaddedToChannelCount() {
    // Some files end headers short; we pad gracefully.
    String csv = String.join("\n",
        "Name\tx\t",
        "Starttime of export\t133238090039920000\t",
        "Endtime of export\t133238090040000000\t",
        "",
        "Name\tchA\tName\tchB",
        "Data-Type\tINT16",  // short — only 1 value
        "SampleTime[ms]\t1\tSampleTime[ms]\t1",
        "SymbolName\tGVL.chA\tSymbolName\tGVL.chB",
        "NetID\t169.254.165.182.1.1\tNetID\t169.254.165.182.1.1",
        "Port\t851\tPort\t851",
        "",
        "0\t1\t0\t2"
    );
    var p = TcScopeCsvParser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
    assertThat(p.channels()).hasSize(2);
    assertThat(p.channels().get(0).dataType()).isEqualTo("INT16");
    assertThat(p.channels().get(1).dataType()).isEmpty(); // padded
  }

  @Test
  void nullInputRejected() {
    assertThatThrownBy(() -> TcScopeCsvParser.parse(null))
        .isInstanceOf(TcScopeCsvParser.CsvParseException.class);
  }

  @Test
  void rejectsCsvWithNoHeaderColumns() {
    String csv = "Name\tx\t\nStarttime of export\t133238090039920000\t\nEndtime of export\t133238090040000000\t\n\n\n";
    assertThatThrownBy(() -> TcScopeCsvParser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8))))
        .isInstanceOf(TcScopeCsvParser.CsvParseException.class);
  }
}
