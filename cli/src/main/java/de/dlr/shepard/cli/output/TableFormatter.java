package de.dlr.shepard.cli.output;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Minimal column-aligned table renderer. Single-pass: compute the
 * widest cell per column, then format each row left-aligned. No
 * borders or ANSI colour — the goal is "looks fine in a plain
 * terminal and pipes cleanly into {@code awk}".
 *
 * <p>Nulls render as the empty string.
 */
public final class TableFormatter {

  private final List<String> headers;
  private final List<List<String>> rows = new ArrayList<>();

  public TableFormatter(String... headers) {
    this.headers = Arrays.asList(headers);
  }

  public TableFormatter addRow(String... cells) {
    List<String> row = new ArrayList<>(headers.size());
    for (int i = 0; i < headers.size(); i++) {
      String cell = i < cells.length ? cells[i] : "";
      row.add(cell == null ? "" : cell);
    }
    rows.add(row);
    return this;
  }

  public String render() {
    int cols = headers.size();
    int[] widths = new int[cols];
    for (int i = 0; i < cols; i++) {
      widths[i] = headers.get(i).length();
    }
    for (List<String> row : rows) {
      for (int i = 0; i < cols; i++) {
        widths[i] = Math.max(widths[i], row.get(i).length());
      }
    }

    StringBuilder sb = new StringBuilder();
    appendRow(sb, headers, widths);
    sb.append('\n');
    for (int i = 0; i < cols; i++) {
      if (i > 0) sb.append("  ");
      sb.append("-".repeat(widths[i]));
    }
    sb.append('\n');
    for (List<String> row : rows) {
      appendRow(sb, row, widths);
      sb.append('\n');
    }
    return sb.toString();
  }

  private static void appendRow(StringBuilder sb, List<String> cells, int[] widths) {
    for (int i = 0; i < cells.size(); i++) {
      if (i > 0) sb.append("  ");
      String cell = cells.get(i);
      sb.append(cell);
      if (i < cells.size() - 1) {
        sb.append(" ".repeat(widths[i] - cell.length()));
      }
    }
  }
}
