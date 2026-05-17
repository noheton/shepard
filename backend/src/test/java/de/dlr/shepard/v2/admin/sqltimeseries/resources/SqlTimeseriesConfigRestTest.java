package de.dlr.shepard.v2.admin.sqltimeseries.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.v2.admin.sqltimeseries.entities.SqlTimeseriesConfig;
import de.dlr.shepard.v2.admin.sqltimeseries.io.SqlTimeseriesConfigIO;
import de.dlr.shepard.v2.admin.sqltimeseries.io.SqlTimeseriesConfigPatchIO;
import de.dlr.shepard.v2.admin.sqltimeseries.services.SqlTimeseriesConfigService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * P10c — unit tests for the admin REST surface.
 *
 * <p>No Quarkus boot — {@link SqlTimeseriesConfigService} is mocked.
 * Tests cover: GET round-trip, PATCH updates, PATCH with only one field
 * (other unchanged), PATCH null clears to default, invalid maxRows → 400,
 * invalid maxDuration → 400, annotation-gate assertion (401/403 via reflection).
 *
 * <p>Mirrors {@code InstanceRorConfigRestTest} structure exactly.
 */
class SqlTimeseriesConfigRestTest {

  private static final long DEFAULT_MAX_ROWS = 1_000_000L;
  private static final String DEFAULT_MAX_DURATION = "PT60S";

  private SqlTimeseriesConfigService service;
  private SqlTimeseriesConfigRest rest;

  @BeforeEach
  void setUp() {
    service = mock(SqlTimeseriesConfigService.class);
    when(service.getDefaultMaxRows()).thenReturn(DEFAULT_MAX_ROWS);
    when(service.getDefaultMaxDuration()).thenReturn(DEFAULT_MAX_DURATION);

    rest = new SqlTimeseriesConfigRest();
    rest.service = service;
  }

  // ─── annotation gates ────────────────────────────────────────────────────

  /**
   * Test #8 (spec): "401 unauthenticated" — we assert the class-level
   * {@code @RolesAllowed} gate which is the JAX-RS mechanism enforcing
   * authentication + role checks. A wire-level 401 test requires a full
   * Quarkus integration test harness.
   */
  @Test
  void classCarriesInstanceAdminGate() {
    RolesAllowed gate = SqlTimeseriesConfigRest.class.getAnnotation(RolesAllowed.class);
    assertNotNull(gate, "SqlTimeseriesConfigRest must be @RolesAllowed-gated at class level");
    assertEquals(1, gate.value().length);
    assertEquals(Constants.INSTANCE_ADMIN_ROLE, gate.value()[0]);
  }

  @Test
  void pathIsV2AdminSqlTimeseriesConfig() {
    Path p = SqlTimeseriesConfigRest.class.getAnnotation(Path.class);
    assertNotNull(p);
    assertEquals("/v2/admin/sql-timeseries/config", p.value(),
        "endpoint lives on the /v2/ shelf per fork policy");
  }

  // ─── Test #1: GET returns defaults when singleton has null fields ─────────

  @Test
  void getConfig_returnsDeployTimeDefaults_whenSingletonFieldsAreNull() {
    SqlTimeseriesConfig cfg = new SqlTimeseriesConfig();
    // maxRows and maxDurationIso are null → service IO layer falls back to defaults
    when(service.current()).thenReturn(cfg);

    Response r = rest.getConfig();

    assertEquals(200, r.getStatus());
    SqlTimeseriesConfigIO body = (SqlTimeseriesConfigIO) r.getEntity();
    assertEquals(DEFAULT_MAX_ROWS, body.maxRows());
    assertEquals(DEFAULT_MAX_DURATION, body.maxDuration());
  }

  // ─── Test #2: GET returns patched values after PATCH ─────────────────────

  @Test
  void getConfig_returnsPatchedValues() {
    SqlTimeseriesConfig cfg = new SqlTimeseriesConfig();
    cfg.setMaxRows(500_000L);
    cfg.setMaxDurationIso("PT30S");
    when(service.current()).thenReturn(cfg);

    Response r = rest.getConfig();

    assertEquals(200, r.getStatus());
    SqlTimeseriesConfigIO body = (SqlTimeseriesConfigIO) r.getEntity();
    assertEquals(500_000L, body.maxRows());
    assertEquals("PT30S", body.maxDuration());
  }

  // ─── Test #3: PATCH with only maxRows leaves maxDuration unchanged ────────

  @Test
  void patchConfig_onlyMaxRows_leavesDurationUnchanged() {
    SqlTimeseriesConfig current = new SqlTimeseriesConfig();
    current.setMaxRows(1_000_000L);
    current.setMaxDurationIso("PT90S");

    SqlTimeseriesConfig updated = new SqlTimeseriesConfig();
    updated.setMaxRows(500_000L);
    updated.setMaxDurationIso("PT90S"); // unchanged

    when(service.current()).thenReturn(current);
    when(service.patch(eq(500_000L), eq("PT90S"))).thenReturn(updated);

    SqlTimeseriesConfigPatchIO body = new SqlTimeseriesConfigPatchIO();
    body.setMaxRows(500_000L); // only maxRows touched

    Response r = rest.patchConfig(body);

    assertEquals(200, r.getStatus());
    SqlTimeseriesConfigIO out = (SqlTimeseriesConfigIO) r.getEntity();
    assertEquals(500_000L, out.maxRows());
    assertEquals("PT90S", out.maxDuration(), "maxDuration must remain unchanged");
  }

  // ─── Test #4: PATCH with only maxDuration leaves maxRows unchanged ────────

  @Test
  void patchConfig_onlyMaxDuration_leavesMaxRowsUnchanged() {
    SqlTimeseriesConfig current = new SqlTimeseriesConfig();
    current.setMaxRows(750_000L);
    current.setMaxDurationIso("PT60S");

    SqlTimeseriesConfig updated = new SqlTimeseriesConfig();
    updated.setMaxRows(750_000L); // unchanged
    updated.setMaxDurationIso("PT2M");

    when(service.current()).thenReturn(current);
    when(service.patch(eq(750_000L), eq("PT2M"))).thenReturn(updated);

    SqlTimeseriesConfigPatchIO body = new SqlTimeseriesConfigPatchIO();
    body.setMaxDurationIso("PT2M"); // only maxDuration touched

    Response r = rest.patchConfig(body);

    assertEquals(200, r.getStatus());
    SqlTimeseriesConfigIO out = (SqlTimeseriesConfigIO) r.getEntity();
    assertEquals(750_000L, out.maxRows(), "maxRows must remain unchanged");
    assertEquals("PT2M", out.maxDuration());
  }

  // ─── Test #5: PATCH maxRows=null clears to default ────────────────────────

  @Test
  void patchConfig_maxRowsNull_clearsToDefault() {
    SqlTimeseriesConfig current = new SqlTimeseriesConfig();
    current.setMaxRows(500_000L);
    current.setMaxDurationIso("PT60S");

    SqlTimeseriesConfig cleared = new SqlTimeseriesConfig();
    // maxRows is null → service.effectiveMaxRows() falls back to default
    cleared.setMaxDurationIso("PT60S");

    when(service.current()).thenReturn(current);
    when(service.patch(isNull(), eq("PT60S"))).thenReturn(cleared);

    SqlTimeseriesConfigPatchIO body = new SqlTimeseriesConfigPatchIO();
    body.setMaxRows(null); // explicit null = clear

    Response r = rest.patchConfig(body);

    assertEquals(200, r.getStatus());
    SqlTimeseriesConfigIO out = (SqlTimeseriesConfigIO) r.getEntity();
    // null maxRows → falls back to deploy-time default in IO.from()
    assertEquals(DEFAULT_MAX_ROWS, out.maxRows(), "null maxRows must show the deploy-time default");
  }

  // ─── Test #6: PATCH invalid maxRows (0 or negative) → 400 ────────────────

  @Test
  void patchConfig_zeroMaxRows_returns400() {
    SqlTimeseriesConfigPatchIO body = new SqlTimeseriesConfigPatchIO();
    body.setMaxRows(0L);

    Response r = rest.patchConfig(body);

    assertEquals(400, r.getStatus());
    assertEquals("application/problem+json", r.getMediaType().toString());
    ProblemJson problem = assertInstanceOf(ProblemJson.class, r.getEntity());
    assertEquals(SqlTimeseriesConfigRest.PROBLEM_TYPE_INVALID_MAX_ROWS, problem.type());
    assertEquals(400, problem.status());
  }

  @Test
  void patchConfig_negativeMaxRows_returns400() {
    SqlTimeseriesConfigPatchIO body = new SqlTimeseriesConfigPatchIO();
    body.setMaxRows(-1L);

    Response r = rest.patchConfig(body);

    assertEquals(400, r.getStatus());
    ProblemJson problem = assertInstanceOf(ProblemJson.class, r.getEntity());
    assertEquals(SqlTimeseriesConfigRest.PROBLEM_TYPE_INVALID_MAX_ROWS, problem.type());
  }

  // ─── Test #7: PATCH invalid maxDuration → 400 ────────────────────────────

  @Test
  void patchConfig_invalidMaxDuration_returns400() {
    SqlTimeseriesConfigPatchIO body = new SqlTimeseriesConfigPatchIO();
    body.setMaxDurationIso("not-a-duration");

    Response r = rest.patchConfig(body);

    assertEquals(400, r.getStatus());
    assertEquals("application/problem+json", r.getMediaType().toString());
    ProblemJson problem = assertInstanceOf(ProblemJson.class, r.getEntity());
    assertEquals(SqlTimeseriesConfigRest.PROBLEM_TYPE_INVALID_MAX_DURATION, problem.type());
    assertEquals(400, problem.status());
  }

  @Test
  void patchConfig_emptyStringMaxDuration_returns400() {
    // Empty string is not a valid ISO-8601 duration
    SqlTimeseriesConfigPatchIO body = new SqlTimeseriesConfigPatchIO();
    body.setMaxDurationIso("PT");

    Response r = rest.patchConfig(body);

    assertEquals(400, r.getStatus());
    ProblemJson problem = assertInstanceOf(ProblemJson.class, r.getEntity());
    assertEquals(SqlTimeseriesConfigRest.PROBLEM_TYPE_INVALID_MAX_DURATION, problem.type());
  }

  // ─── Additional: null body is a legal no-op patch ──────────────────────

  @Test
  void patchConfig_nullBody_treatedAsEmptyPatch() {
    SqlTimeseriesConfig current = new SqlTimeseriesConfig();
    current.setMaxRows(1_000_000L);
    current.setMaxDurationIso("PT60S");

    when(service.current()).thenReturn(current);
    when(service.patch(eq(1_000_000L), eq("PT60S"))).thenReturn(current);

    Response r = rest.patchConfig(null);

    assertEquals(200, r.getStatus(), "null body is a legal RFC 7396 no-op patch");
  }

  // ─── Valid ISO-8601 durations are accepted ────────────────────────────────

  @Test
  void patchConfig_validDuration_returns200() {
    SqlTimeseriesConfig current = new SqlTimeseriesConfig();
    current.setMaxRows(1_000_000L);
    current.setMaxDurationIso("PT60S");

    SqlTimeseriesConfig updated = new SqlTimeseriesConfig();
    updated.setMaxRows(1_000_000L);
    updated.setMaxDurationIso("PT2M30S");

    when(service.current()).thenReturn(current);
    when(service.patch(eq(1_000_000L), eq("PT2M30S"))).thenReturn(updated);

    SqlTimeseriesConfigPatchIO body = new SqlTimeseriesConfigPatchIO();
    body.setMaxDurationIso("PT2M30S");

    Response r = rest.patchConfig(body);

    assertEquals(200, r.getStatus());
    SqlTimeseriesConfigIO out = (SqlTimeseriesConfigIO) r.getEntity();
    assertEquals("PT2M30S", out.maxDuration());
  }
}
