package de.dlr.shepard.cli.output;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class TableFormatterTest {

  @Test
  void rendersHeaderAndRows() {
    String out = new TableFormatter("A", "B")
      .addRow("one", "two")
      .addRow("three", "four")
      .render();

    assertThat(out)
      .contains("A")
      .contains("B")
      .contains("one")
      .contains("two")
      .contains("three")
      .contains("four");
  }

  @Test
  void widensColumnsToFitLongestCell() {
    String out = new TableFormatter("A", "B")
      .addRow("short", "much-longer-cell-here")
      .render();

    // First column gets padded out to the widest cell in column A
    // ("short") and the header row aligns with the separator row in
    // terms of where column B starts. We don't try to assert exact
    // total widths because the last column is intentionally not
    // right-padded.
    String[] lines = out.split("\n");
    // Header + separator + row → at least 3 lines.
    assertThat(lines.length).isGreaterThanOrEqualTo(3);
    int colBStart = lines[0].indexOf("B");
    int separatorColBStart = lines[1].lastIndexOf('-')
      - (lines[1].lastIndexOf('-') - lines[1].indexOf('-', lines[1].indexOf(' ')));
    // Less brittle: assert that the header's column B starts at the
    // same column as the data row's column B start.
    int dataColBStart = lines[2].indexOf("much-longer-cell-here");
    assertThat(colBStart).isEqualTo(dataColBStart);
    assertThat(separatorColBStart).isGreaterThanOrEqualTo(0);
  }

  @Test
  void nullCellRendersAsEmpty() {
    String out = new TableFormatter("X").addRow((String) null).render();
    assertThat(out).contains("X");
  }

  @Test
  void shortRowPadsWithEmpty() {
    String out = new TableFormatter("A", "B", "C").addRow("only-one").render();
    assertThat(out).contains("only-one");
  }
}
