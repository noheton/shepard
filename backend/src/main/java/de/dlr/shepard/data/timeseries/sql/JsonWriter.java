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
 * P10a — streams a JDBC {@link ResultSet} as {@code {"rows": [...], "truncated": bool}} JSON.
 *
 * <p>Each row is written as a JSON object keyed by the column label from the result-set metadata.
 * Column values are mapped to JSON scalars based on SQL type. The writer never buffers more than
 * one row in memory.
 *
 * <p>{@code truncated} is {@code true} iff {@code maxRows} was reached before the cursor was
 * exhausted.
 */
@ApplicationScoped
public class JsonWriter {

  private static final JsonFactory JSON_FACTORY = new JsonFactory();

  /**
   * Writes the result-set rows into {@code out} as JSON.
   *
   * @param rs      the open ResultSet to read; caller is responsible for closing it
   * @param out     the output stream to write to
   * @param maxRows hard cap on the number of rows to write; {@code truncated} is set to {@code true}
   *                if the result set has more rows beyond the cap
   * @throws IOException  on write failure
   * @throws SQLException on JDBC read failure
   */
  public void write(ResultSet rs, OutputStream out, int maxRows) throws IOException, SQLException {
    ResultSetMetaData meta = rs.getMetaData();
    int columnCount = meta.getColumnCount();

    try (JsonGenerator gen = JSON_FACTORY.createGenerator(out)) {
      gen.writeStartObject();
      gen.writeFieldName("rows");
      gen.writeStartArray();

      int rowCount = 0;
      boolean truncated = false;

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
        rowCount++;
      }

      gen.writeEndArray();
      gen.writeBooleanField("truncated", truncated);
      gen.writeEndObject();
    }
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
          // Emit as ISO-8601 string
          gen.writeString(v.toInstant().toString());
        }
      }
      default -> {
        // VARCHAR, TEXT, other types: emit as string or null
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
