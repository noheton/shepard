package de.dlr.shepard.v2.admin.thermography.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.v2.admin.thermography.entities.ThermographyConfig;
import de.dlr.shepard.v2.admin.thermography.io.ThermographyConfigIO;
import de.dlr.shepard.v2.admin.thermography.io.ThermographyConfigPatchIO;
import de.dlr.shepard.v2.admin.thermography.resources.ThermographyConfigAdminRest;
import de.dlr.shepard.v2.admin.thermography.services.ThermographyConfigService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * MFFD-NDT-ADMIN-CONFIG-1 — unit tests for the admin REST surface.
 *
 * <p>No Quarkus boot — {@link ThermographyConfigService} is mocked. Tests
 * cover: GET round-trip, PATCH single field, PATCH multiple fields, PATCH null
 * (revert to default), absent field leaves value alone, and annotation gates.
 */
class ThermographyConfigAdminRestTest {

  private static final double DEFAULT_THRESHOLD = 80.0;
  private static final int DEFAULT_GRID_W = 64;
  private static final int DEFAULT_GRID_H = 64;

  private ThermographyConfigService service;
  private ThermographyConfigAdminRest rest;

  @BeforeEach
  void setUp() {
    service = mock(ThermographyConfigService.class);
    when(service.getDefaultThresholdC()).thenReturn(DEFAULT_THRESHOLD);
    when(service.getDefaultGridWidth()).thenReturn(DEFAULT_GRID_W);
    when(service.getDefaultGridHeight()).thenReturn(DEFAULT_GRID_H);

    // current() returns an entity with null fields → resolved IO uses defaults
    ThermographyConfig defaultEntity = new ThermographyConfig();
    when(service.current()).thenReturn(defaultEntity);

    // getConfig() returns a fully-resolved IO
    ThermographyConfigIO defaultIO =
      new ThermographyConfigIO("test-app-id", DEFAULT_THRESHOLD, DEFAULT_GRID_W, DEFAULT_GRID_H);
    when(service.getConfig()).thenReturn(defaultIO);

    rest = new ThermographyConfigAdminRest();
    rest.service = service;
  }

  // ─── annotation gates ────────────────────────────────────────────────────

  @Test
  void classCarriesInstanceAdminGate() {
    RolesAllowed gate =
      ThermographyConfigAdminRest.class.getAnnotation(RolesAllowed.class);
    assertNotNull(gate, "ThermographyConfigAdminRest must be @RolesAllowed-gated at class level");
    assertEquals(1, gate.value().length);
    assertEquals(Constants.INSTANCE_ADMIN_ROLE, gate.value()[0]);
  }

  @Test
  void pathIsV2AdminThermographyConfig() {
    Path p = ThermographyConfigAdminRest.class.getAnnotation(Path.class);
    assertNotNull(p);
    assertEquals("/v2/admin/thermography/config", p.value(),
      "endpoint lives on the /v2/ shelf per fork API-version policy");
  }

  // ─── GET ─────────────────────────────────────────────────────────────────

  @Test
  void getConfig_returns200WithResolvedIO() {
    Response r = rest.getConfig();

    assertEquals(200, r.getStatus());
    ThermographyConfigIO body = (ThermographyConfigIO) r.getEntity();
    assertEquals(DEFAULT_THRESHOLD, body.thresholdC());
    assertEquals(DEFAULT_GRID_W, body.gridWidth());
    assertEquals(DEFAULT_GRID_H, body.gridHeight());
  }

  @Test
  void getConfig_returnsRuntimeValuesWhenSingletonOverrides() {
    ThermographyConfigIO overridden =
      new ThermographyConfigIO("test-app-id", 120.0, 128, 128);
    when(service.getConfig()).thenReturn(overridden);

    Response r = rest.getConfig();

    assertEquals(200, r.getStatus());
    ThermographyConfigIO body = (ThermographyConfigIO) r.getEntity();
    assertEquals(120.0, body.thresholdC());
    assertEquals(128, body.gridWidth());
    assertEquals(128, body.gridHeight());
  }

  // ─── PATCH ───────────────────────────────────────────────────────────────

  @Test
  void patchConfig_setsThresholdC_leavesGridAlone() {
    ThermographyConfig current = new ThermographyConfig();
    current.setThresholdC(null);
    current.setGridWidth(64);
    current.setGridHeight(64);
    when(service.current()).thenReturn(current);

    ThermographyConfigIO patched =
      new ThermographyConfigIO("test-app-id", 100.0, 64, 64);
    when(service.patchConfig(eq(100.0), eq(64), eq(64))).thenReturn(patched);

    ThermographyConfigPatchIO body = new ThermographyConfigPatchIO();
    body.setThresholdC(100.0);

    Response r = rest.patchConfig(body);

    assertEquals(200, r.getStatus());
    ThermographyConfigIO out = (ThermographyConfigIO) r.getEntity();
    assertEquals(100.0, out.thresholdC());
    assertEquals(64, out.gridWidth());
    assertEquals(64, out.gridHeight());
  }

  @Test
  void patchConfig_setsGrid_leavesThresholdAlone() {
    ThermographyConfig current = new ThermographyConfig();
    current.setThresholdC(80.0);
    current.setGridWidth(null);
    current.setGridHeight(null);
    when(service.current()).thenReturn(current);

    ThermographyConfigIO patched =
      new ThermographyConfigIO("test-app-id", 80.0, 128, 128);
    when(service.patchConfig(eq(80.0), eq(128), eq(128))).thenReturn(patched);

    ThermographyConfigPatchIO body = new ThermographyConfigPatchIO();
    body.setGridWidth(128);
    body.setGridHeight(128);

    Response r = rest.patchConfig(body);

    assertEquals(200, r.getStatus());
    ThermographyConfigIO out = (ThermographyConfigIO) r.getEntity();
    assertEquals(80.0, out.thresholdC());
    assertEquals(128, out.gridWidth());
    assertEquals(128, out.gridHeight());
  }

  @Test
  void patchConfig_nullThresholdC_revertsToDefault() {
    ThermographyConfig current = new ThermographyConfig();
    current.setThresholdC(100.0);
    current.setGridWidth(64);
    current.setGridHeight(64);
    when(service.current()).thenReturn(current);

    ThermographyConfigIO reverted =
      new ThermographyConfigIO("test-app-id", DEFAULT_THRESHOLD, 64, 64);
    when(service.patchConfig(isNull(), eq(64), eq(64))).thenReturn(reverted);

    ThermographyConfigPatchIO body = new ThermographyConfigPatchIO();
    body.setThresholdC(null);  // explicit null = revert to default

    Response r = rest.patchConfig(body);

    assertEquals(200, r.getStatus());
    ThermographyConfigIO out = (ThermographyConfigIO) r.getEntity();
    assertEquals(DEFAULT_THRESHOLD, out.thresholdC());
  }

  @Test
  void patchConfig_nullBody_leavesAllFieldsAlone() {
    ThermographyConfig current = new ThermographyConfig();
    current.setThresholdC(90.0);
    current.setGridWidth(32);
    current.setGridHeight(32);
    when(service.current()).thenReturn(current);

    ThermographyConfigIO unchanged =
      new ThermographyConfigIO("test-app-id", 90.0, 32, 32);
    when(service.patchConfig(eq(90.0), eq(32), eq(32))).thenReturn(unchanged);

    Response r = rest.patchConfig(null);

    assertEquals(200, r.getStatus());
    ThermographyConfigIO out = (ThermographyConfigIO) r.getEntity();
    assertEquals(90.0, out.thresholdC());
    assertEquals(32, out.gridWidth());
    assertEquals(32, out.gridHeight());
  }

  @Test
  void patchConfig_emptyBody_leavesAllFieldsAlone() {
    ThermographyConfig current = new ThermographyConfig();
    current.setThresholdC(75.0);
    current.setGridWidth(48);
    current.setGridHeight(48);
    when(service.current()).thenReturn(current);

    ThermographyConfigIO unchanged =
      new ThermographyConfigIO("test-app-id", 75.0, 48, 48);
    when(service.patchConfig(eq(75.0), eq(48), eq(48))).thenReturn(unchanged);

    // No fields set in patch → all absent → all left alone
    Response r = rest.patchConfig(new ThermographyConfigPatchIO());

    assertEquals(200, r.getStatus());
    ThermographyConfigIO out = (ThermographyConfigIO) r.getEntity();
    assertEquals(75.0, out.thresholdC());
  }

  // ─── IO factory ──────────────────────────────────────────────────────────

  @Test
  void thermographyConfigIO_from_resolvesNullsAgainstDefaults() {
    ThermographyConfig cfg = new ThermographyConfig();
    // all fields null → resolves against defaults
    ThermographyConfigIO io = ThermographyConfigIO.from(cfg, 80.0, 64, 64);

    assertEquals(80.0, io.thresholdC());
    assertEquals(64, io.gridWidth());
    assertEquals(64, io.gridHeight());
  }

  @Test
  void thermographyConfigIO_from_runtimeValuesWinOverDefaults() {
    ThermographyConfig cfg = new ThermographyConfig();
    cfg.setThresholdC(120.0);
    cfg.setGridWidth(128);
    cfg.setGridHeight(256);

    ThermographyConfigIO io = ThermographyConfigIO.from(cfg, 80.0, 64, 64);

    assertEquals(120.0, io.thresholdC());
    assertEquals(128, io.gridWidth());
    assertEquals(256, io.gridHeight());
  }

  @Test
  void patchConfig_patchesAllThreeFields() {
    ThermographyConfig current = new ThermographyConfig();
    current.setThresholdC(80.0);
    current.setGridWidth(64);
    current.setGridHeight(64);
    when(service.current()).thenReturn(current);

    ThermographyConfigIO patched =
      new ThermographyConfigIO("test-app-id", 50.0, 32, 16);
    when(service.patchConfig(eq(50.0), eq(32), eq(16))).thenReturn(patched);

    ThermographyConfigPatchIO body = new ThermographyConfigPatchIO();
    body.setThresholdC(50.0);
    body.setGridWidth(32);
    body.setGridHeight(16);

    Response r = rest.patchConfig(body);

    assertEquals(200, r.getStatus());
    ThermographyConfigIO out = (ThermographyConfigIO) r.getEntity();
    assertEquals(50.0, out.thresholdC());
    assertEquals(32, out.gridWidth());
    assertEquals(16, out.gridHeight());
    verify(service).patchConfig(50.0, 32, 16);
  }
}
