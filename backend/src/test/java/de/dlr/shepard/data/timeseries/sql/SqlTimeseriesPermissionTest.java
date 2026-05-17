package de.dlr.shepard.data.timeseries.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.Principal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * P10a — permission-logic unit tests for {@link SqlTimeseriesRest}.
 *
 * <p>Instantiates the REST handler directly with mocked dependencies (no HTTP layer).
 * Tests cover the three permission-logic branches:
 * <ul>
 *   <li>Empty allowed set → 200 with empty rows body.</li>
 *   <li>Allowed set above the 1000 cap → {@link BadRequestException}.</li>
 *   <li>Allowed set within cap → compiler called with exactly those IDs.</li>
 * </ul>
 */
class SqlTimeseriesPermissionTest {

  private static final String TOGGLE_KEY = "shepard.timeseries.sql.enabled";
  private String previousToggleValue;

  private SqlTimeseriesRest rest;
  private PermissionsService permissionsService;
  private SqlQueryCompiler compiler;
  private SqlQueryExecutor executor;
  private SecurityContext securityContext;

  @BeforeEach
  void setUp() {
    previousToggleValue = System.getProperty(TOGGLE_KEY);
    // Enable the feature toggle for all permission tests
    System.setProperty(TOGGLE_KEY, "true");

    rest = new SqlTimeseriesRest();
    permissionsService = mock(PermissionsService.class);
    compiler = mock(SqlQueryCompiler.class);
    executor = mock(SqlQueryExecutor.class);
    securityContext = mock(SecurityContext.class);

    rest.permissionsService = permissionsService;
    rest.compiler = compiler;
    rest.executor = executor;

    Principal alice = () -> "alice";
    when(securityContext.getUserPrincipal()).thenReturn(alice);
  }

  @AfterEach
  void tearDown() {
    if (previousToggleValue == null) {
      System.clearProperty(TOGGLE_KEY);
    } else {
      System.setProperty(TOGGLE_KEY, previousToggleValue);
    }
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
  void emptyAllowedSet_returns200WithEmptyRows() {
    SqlQuerySpec spec = specWithContainerIds(List.of(42L));
    when(permissionsService.filterAllowedForUser(any(), eq(AccessType.Read), anyString()))
        .thenReturn(Set.of());

    Response response = rest.query(spec, securityContext);

    assertEquals(200, response.getStatus());
    String body = response.getEntity().toString();
    assertTrue(body.contains("\"rows\":[]"), "Body must contain empty rows array");
    assertTrue(body.contains("\"truncated\":false"), "Body must contain truncated=false");
  }

  @Test
  void allowedSetAboveCap_returns400() {
    SqlQuerySpec spec = specWithContainerIds(List.of(1L));
    // Build an allowed set with 1001 IDs (above MAX_CONTAINERS=1000)
    Set<Long> bigSet = LongStream.rangeClosed(1, 1001)
        .boxed()
        .collect(Collectors.toSet());
    when(permissionsService.filterAllowedForUser(any(), eq(AccessType.Read), anyString()))
        .thenReturn(bigSet);

    assertThrows(BadRequestException.class, () -> rest.query(spec, securityContext));
  }

  @Test
  void allowedSetWithinCap_passedToCompiler() {
    Set<Long> allowedIds = Set.of(10L, 20L, 30L);
    SqlQuerySpec spec = specWithContainerIds(List.of(10L, 20L, 30L));
    when(permissionsService.filterAllowedForUser(any(), eq(AccessType.Read), anyString()))
        .thenReturn(allowedIds);

    PreparedStatementSpec dummySpec = new PreparedStatementSpec("SELECT 1", List.of());
    when(compiler.compile(any(), any())).thenReturn(dummySpec);
    when(executor.executeJson(any(), any(int.class))).thenReturn(out -> {
      try {
        out.write("{\"rows\":[],\"truncated\":false}".getBytes());
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    });

    rest.query(spec, securityContext);

    // Verify compiler was called with exactly the allowed IDs
    verify(compiler).compile(eq(spec), eq(allowedIds));
  }
}
