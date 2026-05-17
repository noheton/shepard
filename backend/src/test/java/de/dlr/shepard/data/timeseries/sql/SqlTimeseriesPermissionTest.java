package de.dlr.shepard.data.timeseries.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.v2.admin.sqltimeseries.services.SqlTimeseriesConfigService;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.io.IOException;
import java.security.Principal;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * P10a/P10b — permission-logic unit tests for {@link SqlTimeseriesRest}.
 *
 * <p>Instantiates the REST handler directly with hand-written stubs (no HTTP layer).
 * Uses a subclass stub for {@link PermissionsService} to avoid Mockito byte-buddy
 * instrumentation issues with the Quarkus cache extension classes that PermissionsService
 * references. {@link SqlQueryCompiler} is also stubbed via subclass for the same reason.
 *
 * <p>Tests call {@link SqlTimeseriesRest#executeQuery} directly, bypassing the feature-toggle
 * gate in {@link SqlTimeseriesRest#query}, so MicroProfile Config / Quarkus bootstrap is not
 * required in the standalone JUnit runner.
 *
 * <p>Tests cover the three permission-logic branches:
 * <ul>
 *   <li>Empty allowed set → 200 with format-appropriate empty body.</li>
 *   <li>Allowed set above the 1000 cap → {@link BadRequestException}.</li>
 *   <li>Allowed set within cap → compiler called with exactly those IDs.</li>
 * </ul>
 */
class SqlTimeseriesPermissionTest {

  private SqlTimeseriesRest rest;
  private StubPermissionsService permissionsService;
  private SpyQueryCompiler compiler;
  private SqlQueryExecutor executor;
  private SecurityContext securityContext;

  @BeforeEach
  void setUp() {
    rest = new SqlTimeseriesRest();
    permissionsService = new StubPermissionsService();
    compiler = new SpyQueryCompiler();
    executor = mock(SqlQueryExecutor.class);
    securityContext = mock(SecurityContext.class);

    rest.permissionsService = permissionsService;
    rest.compiler = compiler;
    rest.executor = executor;
    // P10c: wire a stub configService (not injected by CDI in unit tests)
    SqlTimeseriesConfigService configService = Mockito.mock(SqlTimeseriesConfigService.class);
    Mockito.when(configService.effectiveMaxRows()).thenReturn(1_000_000L);
    Mockito.when(configService.effectiveMaxDurationIso()).thenReturn("PT60S");
    rest.configService = configService;

    Principal alice = () -> "alice";
    when(securityContext.getUserPrincipal()).thenReturn(alice);
  }

  // Helper: build a minimal valid SqlQuerySpec with given container IDs
  private SqlQuerySpec specWithContainerIds(List<Long> ids) {
    return new SqlQuerySpec(
        List.of(new SqlQuerySpec.SelectItem("time", null, null)),
        "timeseries_data_points",
        new SqlQuerySpec.WhereClause(
            new SqlQuerySpec.TimeBetween("2026-01-01T00:00:00Z", "2026-02-01T00:00:00Z"),
            ids,
            null),
        null, null, null);
  }

  @Test
  void emptyAllowedSet_withJsonAccept_returns200WithEmptyRows() {
    SqlQuerySpec spec = specWithContainerIds(List.of(42L));
    permissionsService.stubbedResult = Set.of();

    Response response = rest.executeQuery(spec, "application/json", null, securityContext);

    assertEquals(200, response.getStatus());
    String body = response.getEntity().toString();
    org.junit.jupiter.api.Assertions.assertTrue(
        body.contains("\"rows\":[]"), "Body must contain empty rows array");
    org.junit.jupiter.api.Assertions.assertTrue(
        body.contains("\"truncated\":false"), "Body must contain truncated=false");
  }

  @Test
  void emptyAllowedSet_defaultAccept_returns200WithCsv() {
    SqlQuerySpec spec = specWithContainerIds(List.of(42L));
    permissionsService.stubbedResult = Set.of();

    // null Accept = default CSV
    Response response = rest.executeQuery(spec, null, null, securityContext);

    assertEquals(200, response.getStatus());
    // CSV empty response = empty string (just headers, no data rows)
    org.junit.jupiter.api.Assertions.assertTrue(
        response.getMediaType().toString().startsWith("text/csv"),
        "Content-Type must be text/csv");
  }

  @Test
  void allowedSetAboveCap_returns400() {
    SqlQuerySpec spec = specWithContainerIds(List.of(1L));
    // Build an allowed set with 1001 IDs (above MAX_CONTAINERS=1000)
    permissionsService.stubbedResult = LongStream.rangeClosed(1, 1001)
        .boxed()
        .collect(Collectors.toSet());

    assertThrows(BadRequestException.class,
        () -> rest.executeQuery(spec, "application/json", null, securityContext));
  }

  @Test
  void allowedSetWithinCap_passedToCompiler() throws IOException {
    Set<Long> allowedIds = Set.of(10L, 20L, 30L);
    SqlQuerySpec spec = specWithContainerIds(List.of(10L, 20L, 30L));
    permissionsService.stubbedResult = allowedIds;

    // executor.executeCsv (default format) needs to return a WriteResult
    when(executor.executeCsv(
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.anyInt(),
        org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.any()))
        .thenReturn(new WriteResult(0, false));

    rest.executeQuery(spec, null, null, securityContext);

    // Verify compiler was called with exactly the allowed IDs
    assertEquals(spec, compiler.lastSpec, "Compiler must be called with the original spec");
    assertEquals(allowedIds, compiler.lastAllowedIds,
        "Compiler must receive exactly the allowed container IDs");
  }

  // --- Stubs ---

  /**
   * Hand-written stub for PermissionsService. Avoids Mockito byte-buddy instrumentation
   * issues with Quarkus cache extension classes that PermissionsService references via
   * class-level annotations.
   */
  static class StubPermissionsService extends PermissionsService {

    Set<Long> stubbedResult = Set.of();

    StubPermissionsService() {
      super();
    }

    @Override
    public Set<Long> filterAllowedForUser(Collection<Long> entityIds, AccessType accessType,
        String username) {
      return stubbedResult;
    }
  }

  /** Hand-written spy for SqlQueryCompiler that records the last compile() call arguments. */
  static class SpyQueryCompiler extends SqlQueryCompiler {

    SqlQuerySpec lastSpec;
    Set<Long> lastAllowedIds;
    PreparedStatementSpec lastResult;

    @Override
    public PreparedStatementSpec compile(SqlQuerySpec spec, Set<Long> allowedContainerIds) {
      lastSpec = spec;
      lastAllowedIds = allowedContainerIds;
      lastResult = new PreparedStatementSpec("SELECT 1", List.of());
      return lastResult;
    }
  }
}
