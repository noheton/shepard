package de.dlr.shepard.v2.sql.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.data.timeseries.sql.PreparedStatementSpec;
import de.dlr.shepard.data.timeseries.sql.SqlQueryCompiler;
import de.dlr.shepard.data.timeseries.sql.SqlQueryExecutor;
import de.dlr.shepard.data.timeseries.sql.SqlQuerySpec;
import de.dlr.shepard.data.timeseries.sql.WriteResult;
import de.dlr.shepard.v2.admin.sqltimeseries.services.SqlTimeseriesConfigService;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.io.IOException;
import java.security.Principal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
  private StubEntityIdResolver entityIdResolver;

  @BeforeEach
  void setUp() {
    rest = new SqlTimeseriesRest();
    permissionsService = new StubPermissionsService();
    compiler = new SpyQueryCompiler();
    executor = mock(SqlQueryExecutor.class);
    securityContext = mock(SecurityContext.class);
    entityIdResolver = new StubEntityIdResolver();

    rest.permissionsService = permissionsService;
    rest.compiler = compiler;
    rest.executor = executor;
    rest.entityIdResolver = entityIdResolver;
    // P10c: wire a stub configService (not injected by CDI in unit tests)
    SqlTimeseriesConfigService configService = Mockito.mock(SqlTimeseriesConfigService.class);
    Mockito.when(configService.effectiveMaxRows()).thenReturn(1_000_000L);
    Mockito.when(configService.effectiveMaxDurationIso()).thenReturn("PT60S");
    rest.configService = configService;

    Principal alice = () -> "alice";
    when(securityContext.getUserPrincipal()).thenReturn(alice);
  }

  // Helper: build a SqlQuerySpec with container_id_in (now rejected with 400)
  private SqlQuerySpec specWithContainerIds(List<Long> ids) {
    return new SqlQuerySpec(
        List.of(new SqlQuerySpec.SelectItem("time", null, null)),
        "timeseries_data_points",
        new SqlQuerySpec.WhereClause(
            new SqlQuerySpec.TimeBetween("2026-01-01T00:00:00Z", "2026-02-01T00:00:00Z"),
            ids, null, null),
        null, null, null);
  }

  // Helper: build a minimal valid SqlQuerySpec with container_app_id_in (UUID v7 path)
  private SqlQuerySpec specWithContainerAppIds(List<String> appIds) {
    return new SqlQuerySpec(
        List.of(new SqlQuerySpec.SelectItem("time", null, null)),
        "timeseries_data_points",
        new SqlQuerySpec.WhereClause(
            new SqlQuerySpec.TimeBetween("2026-01-01T00:00:00Z", "2026-02-01T00:00:00Z"),
            null, appIds, null),
        null, null, null);
  }

  // ─── APISIMP-SQL-TIMESERIES-NUMERIC-CONTAINER-ID-FALLBACK tombstone ─────────

  @Test
  void containerIdIn_returns400WithMigrationGuidance() {
    // container_id_in is removed; any non-empty value must be rejected with 400.
    SqlQuerySpec spec = specWithContainerIds(List.of(42L));

    Response response = rest.executeQuery(spec, "application/json", null, securityContext);

    assertEquals(400, response.getStatus());
    String body = response.getEntity().toString();
    assertTrue(body.contains("container_app_id_in"), "Error must guide caller to container_app_id_in");
  }

  @Test
  void containerIdIn_emptyList_noTombstone() {
    // An empty container_id_in list is equivalent to omitting it — no tombstone triggered.
    SqlQuerySpec spec = specWithContainerIds(List.of());
    permissionsService.stubbedResult = Set.of();

    Response response = rest.executeQuery(spec, "application/json", null, securityContext);

    assertEquals(200, response.getStatus());
  }

  // ─── Permission-logic tests (all use container_app_id_in) ────────────────

  @Test
  void emptyAllowedSet_withJsonAccept_returns200WithEmptyRows() {
    entityIdResolver.register("container-x", 42L);
    SqlQuerySpec spec = specWithContainerAppIds(List.of("container-x"));
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
    entityIdResolver.register("container-y", 99L);
    SqlQuerySpec spec = specWithContainerAppIds(List.of("container-y"));
    permissionsService.stubbedResult = Set.of();

    // null Accept = default CSV
    Response response = rest.executeQuery(spec, null, null, securityContext);

    assertEquals(200, response.getStatus());
    org.junit.jupiter.api.Assertions.assertTrue(
        response.getMediaType().toString().startsWith("text/csv"),
        "Content-Type must be text/csv");
  }

  @Test
  void allowedSetAboveCap_returns400() {
    entityIdResolver.register("c", 1L);
    SqlQuerySpec spec = specWithContainerAppIds(List.of("c"));
    // Build an allowed set with 1001 IDs (above MAX_CONTAINERS=1000)
    permissionsService.stubbedResult = LongStream.rangeClosed(1, 1001)
        .boxed()
        .collect(Collectors.toSet());

    assertThrows(BadRequestException.class,
        () -> rest.executeQuery(spec, "application/json", null, securityContext));
  }

  @Test
  void allowedSetWithinCap_passedToCompiler() throws IOException {
    entityIdResolver.register("c10", 10L);
    entityIdResolver.register("c20", 20L);
    entityIdResolver.register("c30", 30L);
    Set<Long> allowedIds = Set.of(10L, 20L, 30L);
    SqlQuerySpec spec = specWithContainerAppIds(List.of("c10", "c20", "c30"));
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

  @Test
  void containerAppIdIn_resolvedToLongs_passedToPermissionsService() throws IOException {
    // appIds "aaa" → 10L, "bbb" → 20L registered in the stub resolver
    entityIdResolver.register("aaa", 10L);
    entityIdResolver.register("bbb", 20L);
    SqlQuerySpec spec = specWithContainerAppIds(List.of("aaa", "bbb"));
    permissionsService.stubbedResult = Set.of(10L, 20L);

    when(executor.executeCsv(
        Mockito.any(), Mockito.anyInt(), Mockito.anyLong(), Mockito.any()))
        .thenReturn(new WriteResult(0, false));

    rest.executeQuery(spec, null, null, securityContext);

    assertEquals(Set.of(10L, 20L), compiler.lastAllowedIds,
        "Resolved appIds must be passed to permission filter and compiler");
  }

  @Test
  void containerAppIdIn_unknownAppIdSilentlySkipped() throws IOException {
    entityIdResolver.register("known", 42L);
    // "unknown" is not registered → resolveLong throws NotFoundException → skipped
    SqlQuerySpec spec = specWithContainerAppIds(List.of("known", "unknown-appid"));
    permissionsService.stubbedResult = Set.of(42L);

    when(executor.executeCsv(
        Mockito.any(), Mockito.anyInt(), Mockito.anyLong(), Mockito.any()))
        .thenReturn(new WriteResult(0, false));

    rest.executeQuery(spec, null, null, securityContext);

    // Only the resolved "known" → 42L is passed; "unknown-appid" is silently excluded
    assertEquals(Set.of(42L), compiler.lastAllowedIds);
  }

  @Test
  void containerIdIn_nonEmpty_withContainerAppIdIn_stillReturns400() {
    // Even when container_app_id_in is also supplied, container_id_in triggers the tombstone.
    entityIdResolver.register("uuid-99", 99L);
    SqlQuerySpec spec = new SqlQuerySpec(
        List.of(new SqlQuerySpec.SelectItem("time", null, null)),
        "timeseries_data_points",
        new SqlQuerySpec.WhereClause(
            new SqlQuerySpec.TimeBetween("2026-01-01T00:00:00Z", "2026-02-01T00:00:00Z"),
            List.of(5L), List.of("uuid-99"), null),
        null, null, null);

    Response response = rest.executeQuery(spec, "application/json", null, securityContext);

    assertEquals(400, response.getStatus());
  }

  @Test
  void resolveAppIds_filtersUnknownAppIds() {
    entityIdResolver.register("x", 1L);
    entityIdResolver.register("y", 2L);
    // "z" is not registered

    List<Long> result = rest.resolveAppIds(List.of("x", "z", "y"));

    assertTrue(result.contains(1L), "x → 1L expected");
    assertTrue(result.contains(2L), "y → 2L expected");
    assertEquals(2, result.size(), "Unknown 'z' must be silently dropped");
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

  /**
   * Stub EntityIdResolver that resolves from a pre-populated map without touching Neo4j.
   * Unknown appIds throw NotFoundException (mirroring production behaviour).
   */
  static class StubEntityIdResolver extends EntityIdResolver {

    private final Map<String, Long> map = new HashMap<>();

    void register(String appId, long neo4jId) {
      map.put(appId, neo4jId);
    }

    @Override
    public long resolveLong(String appId) {
      Long id = map.get(appId);
      if (id == null) {
        throw new NotFoundException("No entity with appId " + appId);
      }
      return id;
    }

    @Override
    protected org.neo4j.ogm.session.Session session() {
      return null; // never called — resolveLong is fully overridden
    }
  }
}
