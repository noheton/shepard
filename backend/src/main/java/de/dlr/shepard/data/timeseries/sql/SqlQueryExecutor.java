package de.dlr.shepard.data.timeseries.sql;

import io.agroal.api.AgroalDataSource;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.StreamingOutput;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * P10a — executes a {@link PreparedStatementSpec} against the TimescaleDB data source and
 * streams the result as JSON via {@link JsonWriter}.
 *
 * <p>Uses server-side cursor mode ({@code setAutoCommit(false)} + {@code setFetchSize(10_000)})
 * to avoid materialising the full result set in memory. See
 * {@code aidocs/platform/29-p10-implementation-design.md §6} for the streaming design.
 */
@ApplicationScoped
public class SqlQueryExecutor {

  @Inject
  AgroalDataSource defaultDataSource;

  @Inject
  JsonWriter jsonWriter;

  /**
   * Returns a JAX-RS {@link StreamingOutput} that, when written, opens a JDBC connection,
   * executes the compiled statement, and streams rows to the response output stream via
   * {@link JsonWriter}.
   *
   * @param spec    the compiled statement (SQL with {@code ?} placeholders + bound params)
   * @param maxRows hard row cap; {@code truncated=true} is emitted if the result exceeds it
   */
  public StreamingOutput executeJson(PreparedStatementSpec spec, int maxRows) {
    return out -> {
      try (Connection conn = defaultDataSource.getConnection()) {
        conn.setAutoCommit(false); // required for server-side cursor
        try (PreparedStatement stmt = conn.prepareStatement(spec.sql())) {
          stmt.setFetchSize(10_000); // JDBC cursor mode
          bindParameters(stmt, spec.params());
          try (ResultSet rs = stmt.executeQuery()) {
            jsonWriter.write(rs, out, maxRows);
          }
        } finally {
          // Roll back the implicit read transaction opened by setAutoCommit(false)
          try {
            conn.rollback();
          } catch (SQLException e) {
            Log.warnf("P10a: could not rollback read transaction: %s", e.getMessage());
          }
        }
      } catch (Exception e) {
        Log.errorf(e, "P10a: error executing timeseries SQL query");
        throw new RuntimeException("Error executing timeseries SQL query", e);
      }
    };
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
}
