package de.dlr.shepard.data.timeseries.sql;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;

/**
 * P10b — streams a JDBC {@link ResultSet} as Newline-Delimited JSON (NDJSON) to an
 * {@link OutputStream}.
 *
 * <p>Format details:
 * <ul>
 *   <li>One JSON object per line, each terminated with {@code \n} (LF, not CRLF).</li>
 *   <li>Column labels from result-set metadata are used as object keys.</li>
 *   <li>No trailing comma; no envelope object; each line is independently parseable.</li>
 *   <li>Timestamps emitted as ISO-8601 strings (e.g. {@code "2026-01-01T00:00:00Z"}).</li>
 *   <li>Null values emitted as JSON {@code null}.</li>
 * </ul>
 *
 * <p>{@code truncated} in the returned {@link WriteResult} is {@code true} iff {@code maxRows}
 * was reached before the cursor was exhausted. The REST layer converts this into the HTTP trailer
 * {@code x-shepard-truncated: true}.
 */
@ApplicationScoped
public class NdjsonWriter {

  private static final JsonFactory JSON_FACTORY = new JsonFactory();

  /**
   * Writes the result-set rows into {@code out} as NDJSON.
   *
   * @param rs      the open ResultSet to read; caller is responsible for closing it
   * @param out     the output stream to write to
   * @param maxRows hard cap on the number of rows to write; the truncation flag is set
   *                when the result set has more rows beyond the cap
   * @return a {@link WriteResult} with the number of rows written and the truncation flag
   * @throws IOException  on write failure
   * @throws SQLException on JDBC read failure
   */
  public WriteResult write(ResultSet rs, OutputStream out, int maxRows)
      throws IOException, SQLException {
    ResultSetMetaData meta = rs.getMetaData();
    int columnCount = meta.getColumnCount();

    int rowCount = 0;
    boolean truncated = false;

    // Create one JsonGenerator for the entire result set. AUTO_CLOSE_TARGET=false
    // prevents the generator from closing the underlying OutputStream when it is closed.
    // We flush + write '\n' after each object and close the generator once at the end.
    try (JsonGenerator gen = JSON_FACTORY.createGenerator(out)) {
      gen.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);

      while (rs.next()) {
        if (rowCount >= maxRows) {
          truncated = true;
          break;
        }
        gen.writeStartObject();
        for (int i = 1; i <= columnCount; i++) {
          String label = meta.getColumnLabel(i);
          gen.writeFieldName(label);
          writeColumnValue(gen, rs, i, meta.getColumnType(i));
        }
        gen.writeEndObject();
        gen.flush(); // flush the JSON bytes for this object to 'out'
        out.write('\n'); // NDJSON line terminator
        rowCount++;
      }
    }

    out.flush();
    return new WriteResult(rowCount, truncated);
  }

  private void writeColumnValue(JsonGenerator gen, ResultSet rs, int col, int sqlType)
      throws IOException, SQLException {
    switch (sqlType) {
      case Types.BOOLEAN, Types.BIT -> {
        boolean v = rs.getBoolean(col);
        if (rs.wasNull()) {
          gen.writeNull();
        } else {
          gen.writeBoolean(v);
        }
      }
      case Types.TINYINT, Types.SMALLINT, Types.INTEGER -> {
        int v = rs.getInt(col);
        if (rs.wasNull()) {
          gen.writeNull();
        } else {
          gen.writeNumber(v);
        }
      }
      case Types.BIGINT -> {
        long v = rs.getLong(col);
        if (rs.wasNull()) {
          gen.writeNull();
        } else {
          gen.writeNumber(v);
        }
      }
      case Types.FLOAT, Types.DOUBLE, Types.REAL -> {
        double v = rs.getDouble(col);
        if (rs.wasNull()) {
          gen.writeNull();
        } else {
          gen.writeNumber(v);
        }
      }
      case Types.NUMERIC, Types.DECIMAL -> {
        java.math.BigDecimal v = rs.getBigDecimal(col);
        if (v == null) {
          gen.writeNull();
        } else {
          gen.writeNumber(v);
        }
      }
      case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> {
        java.sql.Timestamp v = rs.getTimestamp(col);
        if (v == null) {
          gen.writeNull();
        } else {
          gen.writeString(v.toInstant().toString());
        }
      }
      default -> {
        String v = rs.getString(col);
        if (v == null) {
          gen.writeNull();
        } else {
          gen.writeString(v);
        }
      }
    }
  }
}
