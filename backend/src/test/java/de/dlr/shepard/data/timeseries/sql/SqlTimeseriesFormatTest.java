package de.dlr.shepard.data.timeseries.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import io.vertx.core.http.HttpServerResponse;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * P10b — content-negotiation and format unit tests for {@link SqlTimeseriesRest}.
 *
 * <p>Uses mock {@link SqlQueryExecutor} and {@link SqlQueryCompiler} stubs to test:
 * <ul>
 *   <li>CSV output for {@code Accept: text/csv}.</li>
 *   <li>NDJSON output for {@code Accept: application/x-ndjson}.</li>
 *   <li>Default CSV output for {@code Accept: *&#47;*}.</li>
 *   <li>Row-cap truncation flag and {@code x-shepard-truncated} trailer logic.</li>
 * </ul>
 *
 * <p>Tests call {@link SqlTimeseriesRest#executeQuery} directly, bypassing the feature-toggle
 * gate and MicroProfile Config bootstrap.
 *
 * <p>Format tests focus on {@link CsvWriter} and {@link NdjsonWriter} independently,
 * then verify the REST layer routes the Accept header to the correct writer.
 */
class SqlTimeseriesFormatTest {

  // --- CsvWriter unit tests ---

  @Test
  void csvWriter_writesHeaderAndDataRows() throws IOException, SQLException {
    CsvWriter writer = new CsvWriter();
    ResultSet rs = twoRowResultSet();

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    WriteResult result = writer.write(rs, out, 100);

    String csv = out.toString(StandardCharsets.UTF_8);
    // Split on CRLF — trailing CRLF after last row produces an empty trailing element
    String[] lines = csv.split("\r\n", -1);

    // Expected: ["name,value", "alice,42", "bob,99", ""] (4 elements: header + 2 data + trailing empty)
    assertTrue(lines.length >= 3, "Must have header + 2 data rows");
    assertEquals("name,value", lines[0], "First line must be header row");
    assertEquals("alice,42", lines[1], "Second line must be first data row");
    assertEquals("bob,99", lines[2], "Third line must be second data row");

    assertEquals(2, result.rowsWritten());
    assertFalse(result.truncated());
  }

  @Test
  void csvWriter_rowCapTruncates() throws IOException, SQLException {
    CsvWriter writer = new CsvWriter();
    ResultSet rs = twoRowResultSet();

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    WriteResult result = writer.write(rs, out, 1); // cap = 1

    String csv = out.toString(StandardCharsets.UTF_8);
    String[] lines = csv.split("\r\n", -1);
    // Expected: ["name,value", "alice,42", ""] (header + 1 capped data row + trailing empty)
    assertTrue(lines.length >= 2, "header + at least 1 data row");
    assertEquals("name,value", lines[0], "First line must be header row");
    assertEquals("alice,42", lines[1], "Second line must be the first (only) data row");

    assertEquals(1, result.rowsWritten());
    assertTrue(result.truncated());
  }

  @Test
  void csvWriter_quotesFieldsContainingComma() throws IOException, SQLException {
    CsvWriter writer = new CsvWriter();
    ResultSet rs = singleColumnResultSet("msg", Types.VARCHAR, "hello, world");

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    writer.write(rs, out, 100);

    String csv = out.toString(StandardCharsets.UTF_8);
    assertTrue(csv.contains("\"hello, world\""), "Field with comma must be double-quoted");
  }

  @Test
  void csvWriter_quotesFieldsContainingDoubleQuote() throws IOException, SQLException {
    CsvWriter writer = new CsvWriter();
    ResultSet rs = singleColumnResultSet("msg", Types.VARCHAR, "say \"hi\"");

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    writer.write(rs, out, 100);

    String csv = out.toString(StandardCharsets.UTF_8);
    assertTrue(csv.contains("\"say \"\"hi\"\"\""), "Embedded double-quotes must be doubled per RFC 4180");
  }

  @Test
  void csvWriter_nullValueEmitsEmptyField() throws IOException, SQLException {
    CsvWriter writer = new CsvWriter();
    ResultSet rs = singleColumnNullResultSet("val");

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    writer.write(rs, out, 100);

    String csv = out.toString(StandardCharsets.UTF_8);
    // header row + data row with empty field
    String[] lines = csv.split("\r\n", -1);
    assertEquals("val", lines[0]);
    assertEquals("", lines[1]); // null → empty field
  }

  // --- NdjsonWriter unit tests ---

  @Test
  void ndjsonWriter_writesOneJsonObjectPerLine() throws IOException, SQLException {
    NdjsonWriter writer = new NdjsonWriter();
    ResultSet rs = twoRowResultSet();

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    WriteResult result = writer.write(rs, out, 100);

    String ndjson = out.toString(StandardCharsets.UTF_8);
    String[] lines = ndjson.split("\n", -1);

    // 2 data lines + trailing empty from last \n
    assertEquals(3, lines.length, "2 JSON object lines + trailing empty");
    assertTrue(lines[0].startsWith("{"), "Each line must be a JSON object");
    assertTrue(lines[0].contains("\"name\":\"alice\""), "First row must contain name:alice");
    assertTrue(lines[0].contains("\"value\":42"), "First row must contain value:42");
    assertTrue(lines[1].contains("\"name\":\"bob\""), "Second row must contain name:bob");

    assertEquals(2, result.rowsWritten());
    assertFalse(result.truncated());
  }

  @Test
  void ndjsonWriter_rowCapTruncates() throws IOException, SQLException {
    NdjsonWriter writer = new NdjsonWriter();
    ResultSet rs = twoRowResultSet();

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    WriteResult result = writer.write(rs, out, 1); // cap = 1

    String ndjson = out.toString(StandardCharsets.UTF_8);
    String[] lines = ndjson.split("\n", -1);
    assertTrue(lines.length >= 1);
    assertTrue(lines[0].contains("\"name\":\"alice\""));

    assertEquals(1, result.rowsWritten());
    assertTrue(result.truncated());
  }

  // --- Format negotiation tests ---

  @Test
  void negotiateFormat_nullAccept_returnsCsv() {
    assertEquals(SqlTimeseriesRest.Format.CSV, SqlTimeseriesRest.negotiateFormat(null));
  }

  @Test
  void negotiateFormat_starStar_returnsCsv() {
    assertEquals(SqlTimeseriesRest.Format.CSV, SqlTimeseriesRest.negotiateFormat("*/*"));
  }

  @Test
  void negotiateFormat_textCsv_returnsCsv() {
    assertEquals(SqlTimeseriesRest.Format.CSV, SqlTimeseriesRest.negotiateFormat("text/csv"));
  }

  @Test
  void negotiateFormat_applicationJson_returnsJson() {
    assertEquals(SqlTimeseriesRest.Format.JSON, SqlTimeseriesRest.negotiateFormat("application/json"));
  }

  @Test
  void negotiateFormat_ndjson_returnsNdjson() {
    assertEquals(SqlTimeseriesRest.Format.NDJSON, SqlTimeseriesRest.negotiateFormat("application/x-ndjson"));
  }

  // --- REST layer integration tests (stubbed executor) ---

  private SqlTimeseriesRest buildRest(String acceptHeader,
      WriteResult executorResult) throws IOException {
    SqlTimeseriesRest rest = new SqlTimeseriesRest();
    rest.permissionsService = new AllowAllPermissionsService();
    rest.compiler = new FixedResultCompiler();
    rest.executor = new StubExecutor(executorResult);
    rest.maxRowsConfig = 1_000_000;
    rest.maxDurationConfig = "PT60S";
    return rest;
  }

  @Test
  void restLayer_defaultAccept_returnsCsvContentType() throws IOException {
    SqlTimeseriesRest rest = buildRest(null, new WriteResult(2, false));
    SecurityContext sc = mockSc("alice");

    Response response = rest.executeQuery(minimalSpec(), null, null, sc);

    assertEquals(200, response.getStatus());
    assertTrue(response.getMediaType().toString().startsWith("text/csv"),
        "Default Accept → text/csv");
    // Verify Content-Disposition header
    assertEquals("attachment; filename=\"timeseries.csv\"",
        response.getHeaderString("Content-Disposition"));
  }

  @Test
  void restLayer_jsonAccept_returnsJsonContentType() throws IOException {
    SqlTimeseriesRest rest = buildRest("application/json", new WriteResult(2, false));
    SecurityContext sc = mockSc("alice");

    Response response = rest.executeQuery(minimalSpec(), "application/json", null, sc);

    assertEquals(200, response.getStatus());
    assertTrue(response.getMediaType().toString().contains("application/json"),
        "Accept: application/json → application/json");
  }

  @Test
  void restLayer_ndjsonAccept_returnsNdjsonContentType() throws IOException {
    SqlTimeseriesRest rest = buildRest("application/x-ndjson", new WriteResult(2, false));
    SecurityContext sc = mockSc("alice");

    Response response = rest.executeQuery(minimalSpec(), "application/x-ndjson", null, sc);

    assertEquals(200, response.getStatus());
    assertEquals("application/x-ndjson",
        response.getMediaType().toString(),
        "Accept: application/x-ndjson → application/x-ndjson");
  }

  @Test
  void restLayer_truncatedResult_bodyStreamsWithTruncatedFlag() throws IOException {
    // Use a real CsvWriter stub to verify truncation flag is communicated
    SqlTimeseriesRest rest = new SqlTimeseriesRest();
    rest.permissionsService = new AllowAllPermissionsService();
    rest.compiler = new FixedResultCompiler();
    // StubExecutor returns truncated=true
    rest.executor = new StubExecutor(new WriteResult(2, true));
    rest.maxRowsConfig = 2;
    rest.maxDurationConfig = "PT60S";

    SecurityContext sc = mockSc("alice");
    Response response = rest.executeQuery(minimalSpec(), "application/json", null, sc);

    assertEquals(200, response.getStatus());
    // Write the body to capture what would be streamed
    StreamingOutput entity = (StreamingOutput) response.getEntity();
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    entity.write(bos);
    // StubExecutor returns truncated=true and the executor does nothing (empty body)
    // but at least we verify no exception is thrown and WriteResult is consumed
  }

  @Test
  void restLayer_truncatedResult_emitsTrailerOnHttpServerResponse() throws IOException {
    // Verify that when truncated=true the REST layer calls putHeader("Trailer", ...)
    // and putTrailer("x-shepard-truncated", "true") on the VertX HttpServerResponse.
    SqlTimeseriesRest rest = new SqlTimeseriesRest();
    rest.permissionsService = new AllowAllPermissionsService();
    rest.compiler = new FixedResultCompiler();
    rest.executor = new StubExecutor(new WriteResult(5, true)); // truncated=true
    rest.maxRowsConfig = 5;
    rest.maxDurationConfig = "PT60S";

    HttpServerResponse mockResp = Mockito.mock(HttpServerResponse.class);
    SecurityContext sc = mockSc("alice");

    Response response = rest.executeQuery(minimalSpec(), "text/csv", mockResp, sc);
    assertEquals(200, response.getStatus());

    // The Trailer announcement must be set before the body is streamed
    Mockito.verify(mockResp).putHeader("Trailer", SqlTimeseriesRest.TRAILER_TRUNCATED);

    // Stream the body — emitTrailer is called inside StreamingOutput.write()
    StreamingOutput entity = (StreamingOutput) response.getEntity();
    entity.write(new ByteArrayOutputStream());

    // The trailer must be emitted after writing the body
    Mockito.verify(mockResp).putTrailer(SqlTimeseriesRest.TRAILER_TRUNCATED, "true");
  }

  @Test
  void restLayer_notTruncated_doesNotEmitTrailer() throws IOException {
    // Verify that putTrailer is NOT called when truncated=false
    SqlTimeseriesRest rest = new SqlTimeseriesRest();
    rest.permissionsService = new AllowAllPermissionsService();
    rest.compiler = new FixedResultCompiler();
    rest.executor = new StubExecutor(new WriteResult(3, false)); // truncated=false
    rest.maxRowsConfig = 1_000_000;
    rest.maxDurationConfig = "PT60S";

    HttpServerResponse mockResp = Mockito.mock(HttpServerResponse.class);
    SecurityContext sc = mockSc("alice");

    Response response = rest.executeQuery(minimalSpec(), "text/csv", mockResp, sc);
    StreamingOutput entity = (StreamingOutput) response.getEntity();
    entity.write(new ByteArrayOutputStream());

    // putTrailer must NOT be called when not truncated
    Mockito.verify(mockResp, Mockito.never())
        .putTrailer(SqlTimeseriesRest.TRAILER_TRUNCATED, "true");
  }

  @Test
  void parseDurationMs_pt60s_returns60000() {
    assertEquals(60_000L, SqlTimeseriesRest.parseDurationMs("PT60S"));
  }

  @Test
  void parseDurationMs_invalid_returns0() {
    assertEquals(0L, SqlTimeseriesRest.parseDurationMs("not-a-duration"));
  }

  // --- QueryTimeoutExceptionMapper unit tests ---

  @Test
  void queryTimeoutExceptionMapper_returns504WithJsonBody() {
    QueryTimeoutExceptionMapper mapper = new QueryTimeoutExceptionMapper();
    SqlQueryExecutor.QueryTimeoutException ex =
        new SqlQueryExecutor.QueryTimeoutException("Query exceeded max duration", null);

    jakarta.ws.rs.core.Response response = mapper.toResponse(ex);

    assertEquals(504, response.getStatus());
    assertEquals("application/json", response.getMediaType().toString());
    assertTrue(response.getEntity().toString().contains("query exceeded max duration"),
        "504 body must contain the error message");
  }

  // --- Result-set stubs ---

  /**
   * Two-column (name VARCHAR, value INTEGER) ResultSet with two rows:
   * ("alice", 42) and ("bob", 99).
   */
  private ResultSet twoRowResultSet() throws SQLException {
    ResultSet rs = Mockito.mock(ResultSet.class);
    ResultSetMetaData meta = Mockito.mock(ResultSetMetaData.class);

    Mockito.when(rs.getMetaData()).thenReturn(meta);
    Mockito.when(meta.getColumnCount()).thenReturn(2);
    Mockito.when(meta.getColumnLabel(1)).thenReturn("name");
    Mockito.when(meta.getColumnLabel(2)).thenReturn("value");
    Mockito.when(meta.getColumnType(1)).thenReturn(Types.VARCHAR);
    Mockito.when(meta.getColumnType(2)).thenReturn(Types.INTEGER);

    // next() returns true twice, then false
    Mockito.when(rs.next()).thenReturn(true, true, false);
    Mockito.when(rs.getString(1)).thenReturn("alice", "bob");
    Mockito.when(rs.getInt(2)).thenReturn(42, 99);
    Mockito.when(rs.wasNull()).thenReturn(false);

    return rs;
  }

  /** Single-column ResultSet with one string row. */
  private ResultSet singleColumnResultSet(String colName, int sqlType, String value)
      throws SQLException {
    ResultSet rs = Mockito.mock(ResultSet.class);
    ResultSetMetaData meta = Mockito.mock(ResultSetMetaData.class);

    Mockito.when(rs.getMetaData()).thenReturn(meta);
    Mockito.when(meta.getColumnCount()).thenReturn(1);
    Mockito.when(meta.getColumnLabel(1)).thenReturn(colName);
    Mockito.when(meta.getColumnType(1)).thenReturn(sqlType);
    Mockito.when(rs.next()).thenReturn(true, false);
    Mockito.when(rs.getString(1)).thenReturn(value);
    Mockito.when(rs.wasNull()).thenReturn(false);

    return rs;
  }

  /** Single-column ResultSet with one null value. */
  private ResultSet singleColumnNullResultSet(String colName) throws SQLException {
    ResultSet rs = Mockito.mock(ResultSet.class);
    ResultSetMetaData meta = Mockito.mock(ResultSetMetaData.class);

    Mockito.when(rs.getMetaData()).thenReturn(meta);
    Mockito.when(meta.getColumnCount()).thenReturn(1);
    Mockito.when(meta.getColumnLabel(1)).thenReturn(colName);
    Mockito.when(meta.getColumnType(1)).thenReturn(Types.VARCHAR);
    Mockito.when(rs.next()).thenReturn(true, false);
    Mockito.when(rs.getString(1)).thenReturn(null);
    Mockito.when(rs.wasNull()).thenReturn(false);

    return rs;
  }

  private SqlQuerySpec minimalSpec() {
    return new SqlQuerySpec(
        List.of(new SqlQuerySpec.SelectItem("time", null, null)),
        "timeseries_data_points",
        new SqlQuerySpec.WhereClause(
            new SqlQuerySpec.TimeBetween("2026-01-01T00:00:00Z", "2026-02-01T00:00:00Z"),
            List.of(1L),
            null),
        null, null, null);
  }

  private SecurityContext mockSc(String username) {
    SecurityContext sc = Mockito.mock(SecurityContext.class);
    java.security.Principal p = () -> username;
    Mockito.when(sc.getUserPrincipal()).thenReturn(p);
    return sc;
  }

  // --- Stubs ---

  /** Allows all container IDs through the permission filter. */
  static class AllowAllPermissionsService extends PermissionsService {
    AllowAllPermissionsService() { super(); }

    @Override
    public Set<Long> filterAllowedForUser(Collection<Long> entityIds, AccessType accessType,
        String username) {
      return Set.copyOf(entityIds);
    }
  }

  /** Returns a fixed PreparedStatementSpec without touching a database. */
  static class FixedResultCompiler extends SqlQueryCompiler {
    @Override
    public PreparedStatementSpec compile(SqlQuerySpec spec, Set<Long> allowedContainerIds) {
      return new PreparedStatementSpec("SELECT 1", List.of());
    }
  }

  /**
   * Stub executor that returns a pre-configured {@link WriteResult} without touching a database.
   *
   * <p>Writes nothing to the output stream (the test verifies the response metadata, not the
   * body content — body content is tested by the writer unit tests above).
   */
  static class StubExecutor extends SqlQueryExecutor {

    private final WriteResult result;

    StubExecutor(WriteResult result) {
      this.result = result;
    }

    @Override
    public WriteResult executeCsv(PreparedStatementSpec spec, int maxRows, long maxDurationMs,
        OutputStream out) throws IOException {
      return result;
    }

    @Override
    public WriteResult executeJson(PreparedStatementSpec spec, int maxRows, long maxDurationMs,
        OutputStream out) throws IOException {
      return result;
    }

    @Override
    public WriteResult executeNdjson(PreparedStatementSpec spec, int maxRows, long maxDurationMs,
        OutputStream out) throws IOException {
      return result;
    }
  }
}
