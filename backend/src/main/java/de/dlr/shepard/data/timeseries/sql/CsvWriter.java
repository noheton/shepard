package de.dlr.shepard.data.timeseries.sql;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;

/**
 * P10b — streams a JDBC {@link ResultSet} as RFC 4180 CSV to an {@link OutputStream}.
 *
 * <p>Format details:
 * <ul>
 *   <li>UTF-8 encoding, no BOM.</li>
 *   <li>CRLF ({@code \r\n}) line endings per RFC 4180.</li>
 *   <li>Comma separator.</li>
 *   <li>First row = header with column labels from result-set metadata.</li>
 *   <li>Fields containing comma, double-quote, CR, or LF are wrapped in double-quotes;
 *       embedded double-quotes are escaped as {@code ""} (RFC 4180 §2.7).</li>
 *   <li>Timestamps emitted as ISO-8601 with Z offset (e.g. {@code 2026-01-01T00:00:00Z}).</li>
 *   <li>Null values are emitted as the empty string.</li>
 * </ul>
 *
 * <p>Apache Commons CSV is not used here because it is not in the project's BOM. The RFC 4180
 * quoting rule is simple enough to implement inline without risk.
 *
 * <p>{@code truncated} in the returned {@link WriteResult} is {@code true} iff {@code maxRows}
 * was reached before the cursor was exhausted. The REST layer converts this into the HTTP trailer
 * {@code x-shepard-truncated: true}.
 */
@ApplicationScoped
public class CsvWriter {

  private static final char COMMA = ',';
  private static final String CRLF = "\r\n";
  private static final char DQUOTE = '"';

  /**
   * Writes the result-set rows into {@code out} as RFC 4180 CSV.
   *
   * @param rs      the open ResultSet to read; caller is responsible for closing it
   * @param out     the output stream to write to
   * @param maxRows hard cap on the number of rows to write; the truncation flag is set
   *                when the result set has more rows beyond the cap
   * @return a {@link WriteResult} with the number of data rows written and the truncation flag
   * @throws IOException  on write failure
   * @throws SQLException on JDBC read failure
   */
  public WriteResult write(ResultSet rs, OutputStream out, int maxRows)
      throws IOException, SQLException {
    ResultSetMetaData meta = rs.getMetaData();
    int columnCount = meta.getColumnCount();

    // Use an unbuffered OutputStreamWriter so each flush goes directly to the socket.
    // StreamingOutput's OutputStream is already buffered by the container.
    Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);

    // Header row
    for (int i = 1; i <= columnCount; i++) {
      if (i > 1) {
        writer.write(COMMA);
      }
      writeField(writer, meta.getColumnLabel(i));
    }
    writer.write(CRLF);

    int rowCount = 0;
    boolean truncated = false;

    while (rs.next()) {
      if (rowCount >= maxRows) {
        truncated = true;
        break;
      }
      for (int i = 1; i <= columnCount; i++) {
        if (i > 1) {
          writer.write(COMMA);
        }
        String value = columnToString(rs, i, meta.getColumnType(i));
        if (value != null) {
          writeField(writer, value);
        }
        // null → empty field (nothing written between commas)
      }
      writer.write(CRLF);
      rowCount++;
    }

    writer.flush();
    return new WriteResult(rowCount, truncated);
  }

  /**
   * Writes a single CSV field, quoting it if necessary per RFC 4180.
   *
   * <p>A field must be quoted when it contains a comma, double-quote, CR, or LF.
   * Embedded double-quotes are doubled ({@code "} → {@code ""}).
   */
  private void writeField(Writer writer, String value) throws IOException {
    boolean needsQuoting = value.indexOf(COMMA) >= 0
        || value.indexOf(DQUOTE) >= 0
        || value.indexOf('\r') >= 0
        || value.indexOf('\n') >= 0;

    if (!needsQuoting) {
      writer.write(value);
      return;
    }

    writer.write(DQUOTE);
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == DQUOTE) {
        writer.write(DQUOTE); // escape: "" per RFC 4180
      }
      writer.write(c);
    }
    writer.write(DQUOTE);
  }

  /**
   * Converts a single JDBC column value to its CSV string representation.
   *
   * <p>Returns {@code null} for SQL NULL (caller emits an empty field).
   * Timestamps are formatted as ISO-8601 with Z offset.
   */
  private String columnToString(ResultSet rs, int col, int sqlType)
      throws SQLException {
    switch (sqlType) {
      case Types.BOOLEAN, Types.BIT -> {
        boolean v = rs.getBoolean(col);
        return rs.wasNull() ? null : Boolean.toString(v);
      }
      case Types.TINYINT, Types.SMALLINT, Types.INTEGER -> {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : Integer.toString(v);
      }
      case Types.BIGINT -> {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : Long.toString(v);
      }
      case Types.FLOAT, Types.DOUBLE, Types.REAL -> {
        double v = rs.getDouble(col);
        return rs.wasNull() ? null : Double.toString(v);
      }
      case Types.NUMERIC, Types.DECIMAL -> {
        java.math.BigDecimal v = rs.getBigDecimal(col);
        return v == null ? null : v.toPlainString();
      }
      case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> {
        java.sql.Timestamp v = rs.getTimestamp(col);
        return v == null ? null : v.toInstant().toString();
      }
      default -> {
        return rs.getString(col);
      }
    }
  }
}
