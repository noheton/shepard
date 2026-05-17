package de.dlr.shepard.data.timeseries.sql;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * P10b — maps {@link SqlQueryExecutor.QueryTimeoutException} (PostgreSQL SQLState 57014,
 * {@code statement_timeout} expired) to HTTP 504 with a structured JSON error body.
 *
 * <p>This works for timeouts that fire before the first response byte is written to the
 * socket — the common case when a slow query plan causes {@code stmt.executeQuery()} itself
 * to block and then time out. Once the container has flushed any response headers
 * (mid-stream timeouts), the HTTP 504 status can no longer be set and the connection is
 * closed instead.
 *
 * <p>The exception is thrown as a {@link RuntimeException} from within
 * {@code StreamingOutput.write()} (declared {@code throws IOException}), which JAX-RS
 * containers pass to the registered {@link ExceptionMapper} chain when headers have
 * not yet been committed.
 */
@Provider
public class QueryTimeoutExceptionMapper
    implements ExceptionMapper<SqlQueryExecutor.QueryTimeoutException> {

  @Override
  public Response toResponse(SqlQueryExecutor.QueryTimeoutException exception) {
    return Response.status(504)
        .type(MediaType.APPLICATION_JSON)
        .entity("{\"error\":\"query exceeded max duration\"}")
        .build();
  }
}
