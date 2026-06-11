package de.dlr.shepard.v2.admin.config.descriptors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.dlr.shepard.v2.admin.thermography.entities.ThermographyConfig;
import de.dlr.shepard.v2.admin.thermography.io.ThermographyConfigIO;
import de.dlr.shepard.v2.admin.thermography.services.ThermographyConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * APISIMP-THERMO-ADMIN-CONFIG — unit tests for {@link ThermographyConfigDescriptor}.
 * No Quarkus boot; {@link ThermographyConfigService} is mocked. Ports the
 * PATCH-logic tests from the deleted {@code ThermographyConfigAdminRestTest}.
 */
class ThermographyConfigDescriptorTest {

  private static final double DEFAULT_THRESHOLD = 80.0;
  private static final int DEFAULT_GRID_W = 64;
  private static final int DEFAULT_GRID_H = 64;

  private ThermographyConfigService service;
  private ThermographyConfigDescriptor descriptor;

  @BeforeEach
  void setUp() {
    service = mock(ThermographyConfigService.class);

    ThermographyConfig defaultEntity = new ThermographyConfig();
    when(service.current()).thenReturn(defaultEntity);

    ThermographyConfigIO defaultIO =
      new ThermographyConfigIO("test-app-id", DEFAULT_THRESHOLD, DEFAULT_GRID_W, DEFAULT_GRID_H);
    when(service.getConfig()).thenReturn(defaultIO);

    descriptor = new ThermographyConfigDescriptor();
    descriptor.service = service;
  }

  // ─── identity ────────────────────────────────────────────────────────────

  @Test
  void featureNameIsThermography() {
    assertEquals("thermography", descriptor.featureName());
  }

  @Test
  void descriptionIsNonEmpty() {
    assertNotNull(descriptor.description());
    assertFalse(descriptor.description().isBlank());
  }

  // ─── GET ─────────────────────────────────────────────────────────────────

  @Test
  void currentShapeDelegatesToService() {
    ThermographyConfigIO result = descriptor.currentShape();
    assertEquals(DEFAULT_THRESHOLD, result.thresholdC());
    assertEquals(DEFAULT_GRID_W, result.gridWidth());
    assertEquals(DEFAULT_GRID_H, result.gridHeight());
  }

  // ─── PATCH ───────────────────────────────────────────────────────────────

  @Test
  void patchConfig_setsThresholdC_leavesGridAlone() throws Exception {
    ThermographyConfig current = new ThermographyConfig();
    current.setThresholdC(null);
    current.setGridWidth(64);
    current.setGridHeight(64);
    when(service.current()).thenReturn(current);

    ThermographyConfigIO patched =
      new ThermographyConfigIO("test-app-id", 100.0, 64, 64);
    when(service.patchConfig(eq(100.0), eq(64), eq(64))).thenReturn(patched);

    ObjectNode patch = JsonNodeFactory.instance.objectNode().put("thresholdC", 100.0);
    ThermographyConfigIO out = descriptor.applyMergePatch(patch);

    assertEquals(100.0, out.thresholdC());
    assertEquals(64, out.gridWidth());
    assertEquals(64, out.gridHeight());
  }

  @Test
  void patchConfig_setsGrid_leavesThresholdAlone() throws Exception {
    ThermographyConfig current = new ThermographyConfig();
    current.setThresholdC(80.0);
    current.setGridWidth(null);
    current.setGridHeight(null);
    when(service.current()).thenReturn(current);

    ThermographyConfigIO patched =
      new ThermographyConfigIO("test-app-id", 80.0, 128, 128);
    when(service.patchConfig(eq(80.0), eq(128), eq(128))).thenReturn(patched);

    ObjectNode patch = JsonNodeFactory.instance.objectNode()
      .put("gridWidth", 128)
      .put("gridHeight", 128);
    ThermographyConfigIO out = descriptor.applyMergePatch(patch);

    assertEquals(80.0, out.thresholdC());
    assertEquals(128, out.gridWidth());
    assertEquals(128, out.gridHeight());
  }

  @Test
  void patchConfig_nullThresholdC_revertsToDefault() throws Exception {
    ThermographyConfig current = new ThermographyConfig();
    current.setThresholdC(100.0);
    current.setGridWidth(64);
    current.setGridHeight(64);
    when(service.current()).thenReturn(current);

    ThermographyConfigIO reverted =
      new ThermographyConfigIO("test-app-id", DEFAULT_THRESHOLD, 64, 64);
    when(service.patchConfig(isNull(), eq(64), eq(64))).thenReturn(reverted);

    ObjectNode patch = JsonNodeFactory.instance.objectNode();
    patch.putNull("thresholdC"); // explicit null = revert to deploy-time default
    ThermographyConfigIO out = descriptor.applyMergePatch(patch);

    assertEquals(DEFAULT_THRESHOLD, out.thresholdC());
  }

  @Test
  void patchConfig_emptyPatch_leavesAllFieldsAlone() throws Exception {
    ThermographyConfig current = new ThermographyConfig();
    current.setThresholdC(90.0);
    current.setGridWidth(32);
    current.setGridHeight(32);
    when(service.current()).thenReturn(current);

    ThermographyConfigIO unchanged =
      new ThermographyConfigIO("test-app-id", 90.0, 32, 32);
    when(service.patchConfig(eq(90.0), eq(32), eq(32))).thenReturn(unchanged);

    ObjectNode patch = JsonNodeFactory.instance.objectNode(); // all absent = leave alone
    ThermographyConfigIO out = descriptor.applyMergePatch(patch);

    assertEquals(90.0, out.thresholdC());
    assertEquals(32, out.gridWidth());
    assertEquals(32, out.gridHeight());
  }

  @Test
  void patchConfig_patchesAllThreeFields() throws Exception {
    ThermographyConfig current = new ThermographyConfig();
    current.setThresholdC(80.0);
    current.setGridWidth(64);
    current.setGridHeight(64);
    when(service.current()).thenReturn(current);

    ThermographyConfigIO patched =
      new ThermographyConfigIO("test-app-id", 50.0, 32, 16);
    when(service.patchConfig(eq(50.0), eq(32), eq(16))).thenReturn(patched);

    ObjectNode patch = JsonNodeFactory.instance.objectNode()
      .put("thresholdC", 50.0)
      .put("gridWidth", 32)
      .put("gridHeight", 16);
    ThermographyConfigIO out = descriptor.applyMergePatch(patch);

    assertEquals(50.0, out.thresholdC());
    assertEquals(32, out.gridWidth());
    assertEquals(16, out.gridHeight());
    verify(service).patchConfig(50.0, 32, 16);
  }

  // ─── IO factory (static; no REST-class dependency) ───────────────────────

  @Test
  void thermographyConfigIO_from_resolvesNullsAgainstDefaults() {
    ThermographyConfig cfg = new ThermographyConfig();
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
}
