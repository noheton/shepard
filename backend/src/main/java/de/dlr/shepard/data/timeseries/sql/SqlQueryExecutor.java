package de.dlr.shepard.data.timeseries.sql;

import io.agroal.api.AgroalDataSource;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * P10a/P10b — executes a {@link PreparedStatementSpec} against the TimescaleDB data source and
 * streams the result via {@link JsonWriter}, {@link CsvWriter}, or {@link NdjsonWriter}.
 *
 * <p>Uses server-side cursor mode ({@code setAutoCommit(false)} + {@code setFetchSize(10_000)})
 * to avoid materialising the full result set in memory. See
 * {@code aidocs/platform/29-p10-implementation-design.md §6} for the streaming design.
 *
 * <p>P10b additions:
 * <ul>
 *   <li>CSV and NDJSON output methods returning {@link WriteResult}.</li>
 *   <li>{@code statement_timeout} set on the connection at session scope before the cursor query
 *       fires — PostgreSQL enforces the timeout at the server side and raises SQLState 57014 on
 *       expiry.</li>
 *   <li>{@link Statement#cancel()} called when the writer's {@link IOException} signals a broken
 *       client socket, stopping the DB cursor promptly.</li>
 * </ul>
 *
 * <p>The public methods write directly to an {@link OutputStream} and return a {@link WriteResult}.
 * Callers (the REST layer) wrap these in a {@code StreamingOutput} lambda so that JAX-RS streams
 * the response body while retaining access to the {@link WriteResult} for trailer emission.
 */
@ApplicationScoped
public class SqlQueryExecutor {

  /** PostgreSQL SQLState code for query_canceled / statement_timeout. */
  static final String PG_SQLSTATE_QUERY_CANCELED = "57014";

  @Inject
  AgroalDataSource defaultDataSource;

  @Inject
  JsonWriter jsonWriter;

  @Inject
  CsvWriter csvWriter;

  @Inject
  NdjsonWriter ndjsonWriter;

  /**
   * Executes the compiled statement and writes streaming JSON to {@code out}.
   *
   * @param spec          the compiled statement
   * @param maxRows       hard row cap
   * @param maxDurationMs PostgreSQL {@code statement_timeout} in milliseconds; 0 = unlimited
   * @param out           the output stream to write to
   * @return a {@link WriteResult} with the number of rows written and the truncation flag
   * @throws IOException           on client-side write failure
   * @throws QueryTimeoutException if PostgreSQL fires {@code statement_timeout} (SQLState 57014)
   */
  public WriteResult executeJson(PreparedStatementSpec spec, int maxRows, long maxDurationMs,
      OutputStream out) throws IOException {
    return executeWithFormat(spec, maxRows, maxDurationMs,
        rs -> jsonWriter.write(rs, out, maxRows));
  }

  /**
   * Executes the compiled statement and writes RFC 4180 CSV to {@code out}.
   *
   * @param spec          the compiled statement
   * @param maxRows       hard row cap
   * @param maxDurationMs PostgreSQL {@code statement_timeout} in milliseconds; 0 = unlimited
   * @param out           the output stream to write to
   * @return a {@link WriteResult} with the number of rows written and the truncation flag
   * @throws IOException           on client-side write failure
   * @throws QueryTimeoutException if PostgreSQL fires {@code statement_timeout} (SQLState 57014)
   */
  public WriteResult executeCsv(PreparedStatementSpec spec, int maxRows, long maxDurationMs,
      OutputStream out) throws IOException {
    return executeWithFormat(spec, maxRows, maxDurationMs,
        rs -> csvWriter.write(rs, out, maxRows));
  }

  /**
   * Executes the compiled statement and writes NDJSON to {@code out}.
   *
   * @param spec          the compiled statement
   * @param maxRows       hard row cap
   * @param maxDurationMs PostgreSQL {@code statement_timeout} in milliseconds; 0 = unlimited
   * @param out           the output stream to write to
   * @return a {@link WriteResult} with the number of rows written and the truncation flag
   * @throws IOException           on client-side write failure
   * @throws QueryTimeoutException if PostgreSQL fires {@code statement_timeout} (SQLState 57014)
   */
  public WriteResult executeNdjson(PreparedStatementSpec spec, int maxRows, long maxDurationMs,
      OutputStream out) throws IOException {
    return executeWithFormat(spec, maxRows, maxDurationMs,
        rs -> ndjsonWriter.write(rs, out, maxRows));
  }

  /**
   * Core streaming logic shared by all format methods.
   *
   * <p>Connection lifecycle:
   * <ol>
   *   <li>Open connection, disable auto-commit (required for server-side cursor).</li>
   *   <li>Set {@code statement_timeout} on the session (if {@code maxDurationMs > 0}).</li>
   *   <li>Prepare and execute the statement with {@code fetchSize=10_000} (cursor mode).</li>
   *   <li>Delegate row writing to the format writer via {@code formatWriter}.</li>
   *   <li>On {@link IOException} (broken client socket), call {@link Statement#cancel()} before
   *       propagating — this releases the DB cursor promptly.</li>
   *   <li>Roll back the implicit read transaction and close the connection.</li>
   * </ol>
   */
  WriteResult executeWithFormat(PreparedStatementSpec spec, int maxRows,
      long maxDurationMs, FormatWriter formatWriter) throws IOException {
    try (Connection conn = defaultDataSource.getConnection()) {
      conn.setAutoCommit(false); // required for server-side cursor

      // Set session-level statement_timeout (milliseconds; 0 = no limit)
      if (maxDurationMs > 0) {
        try (Statement timeoutStmt = conn.createStatement()) {
          timeoutStmt.execute("SET statement_timeout = " + maxDurationMs);
        }
      }

      PreparedStatement stmt = null;
      try {
        stmt = conn.prepareStatement(spec.sql());
        stmt.setFetchSize(10_000); // JDBC cursor mode
        bindParameters(stmt, spec.params());

        try (ResultSet rs = stmt.executeQuery()) {
          try {
            return formatWriter.write(rs);
          } catch (IOException ioEx) {
            // Client closed the socket mid-stream — cancel the DB query promptly
            try {
              stmt.cancel();
            } catch (SQLException cancelEx) {
              Log.warnf("P10b: could not cancel statement after client disconnect: %s",
                  cancelEx.getMessage());
            }
            throw ioEx;
          }
        }
      } catch (SQLException sqlEx) {
        if (PG_SQLSTATE_QUERY_CANCELED.equals(sqlEx.getSQLState())) {
          throw new QueryTimeoutException("Query exceeded max duration", sqlEx);
        }
        throw new RuntimeException("Error executing timeseries SQL query", sqlEx);
      } finally {
        if (stmt != null) {
          try {
            stmt.close();
          } catch (SQLException closeEx) {
            Log.warnf("P10b: could not close statement: %s", closeEx.getMessage());
          }
        }
        try {
          conn.rollback();
        } catch (SQLException e) {
          Log.warnf("P10b: could not rollback read transaction: %s", e.getMessage());
        }
      }
    } catch (QueryTimeoutException qte) {
      throw qte; // propagate unwrapped so REST layer can map to 504
    } catch (IOException ioEx) {
      throw ioEx; // propagate client-disconnect IOE
    } catch (Exception e) {
      Log.errorf(e, "P10b: error executing timeseries SQL query");
      throw new RuntimeException("Error executing timeseries SQL query", e);
    }
  }

  private void bindParameters(PreparedStatement stmt, List<Object> params) throws SQLException {
    for (int i = 0; i < params.size(); i++) {
      Object value = params.get(i);
      if (value == null) {
        stmt.setNull(i + 1, java.sql.Types.NULL);
      } else {
        stmt.setObject(i + 1, value);
      }
    }
  }

  /**
   * Functional interface for format-specific row writing.
   *
   * <p>The {@link OutputStream} is not passed to this interface; callers close over it
   * in the lambda that creates the {@link FormatWriter}. The interface method takes only the
   * {@link ResultSet} and returns a {@link WriteResult}.
   */
  @FunctionalInterface
  interface FormatWriter {
    WriteResult write(ResultSet rs) throws IOException, SQLException;
  }

  /**
   * Thrown by {@link #executeWithFormat} when PostgreSQL fires {@code statement_timeout}
   * (SQLState 57014). The REST layer maps this to HTTP 504.
   */
  public static class QueryTimeoutException extends RuntimeException {
    public QueryTimeoutException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
