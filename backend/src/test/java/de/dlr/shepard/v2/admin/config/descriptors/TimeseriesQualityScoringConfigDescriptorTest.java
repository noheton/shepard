package de.dlr.shepard.v2.admin.config.descriptors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.dlr.shepard.v2.admin.config.spi.ConfigPatchException;
import de.dlr.shepard.v2.admin.qualityscoring.entities.TimeseriesQualityScoringConfig;
import de.dlr.shepard.v2.admin.qualityscoring.io.TimeseriesQualityScoringConfigIO;
import de.dlr.shepard.v2.admin.qualityscoring.services.TimeseriesQualityScoringConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** FTOGGLE-QS-1 — unit tests for {@link TimeseriesQualityScoringConfigDescriptor}. */
class TimeseriesQualityScoringConfigDescriptorTest {

  private static final boolean DEFAULT_ENABLED = false;
  private static final int DEFAULT_BATCH_SIZE = 100;

  private TimeseriesQualityScoringConfigService service;
  private TimeseriesQualityScoringConfigDescriptor descriptor;

  private static TimeseriesQualityScoringConfig cfg(Boolean enabled, Integer batchSize) {
    TimeseriesQualityScoringConfig c = new TimeseriesQualityScoringConfig();
    c.setEnabled(enabled);
    c.setBatchSize(batchSize);
    return c;
  }

  @BeforeEach
  void setUp() {
    service = mock(TimeseriesQualityScoringConfigService.class);
    when(service.getDefaultEnabled()).thenReturn(DEFAULT_ENABLED);
    when(service.getDefaultBatchSize()).thenReturn(DEFAULT_BATCH_SIZE);
    when(service.current()).thenReturn(cfg(null, null));
    when(service.getConfig()).thenReturn(
      new TimeseriesQualityScoringConfigIO(DEFAULT_ENABLED, DEFAULT_BATCH_SIZE));
    descriptor = new TimeseriesQualityScoringConfigDescriptor();
    descriptor.service = service;
  }

  @Test
  void featureNameIsTimeseriesQualityScoring() {
    assertEquals("timeseries-quality-scoring", descriptor.featureName());
  }

  @Test
  void descriptionIsNonEmpty() {
    assertNotNull(descriptor.description());
    assertFalse(descriptor.description().isBlank());
  }

  @Test
  void currentShapeDelegatesToService() {
    TimeseriesQualityScoringConfigIO result = descriptor.currentShape();
    assertFalse(result.enabled());
    assertEquals(DEFAULT_BATCH_SIZE, result.batchSize());
  }

  @Test
  void patchEnabled_setsTrue_leavesBatchAlone() throws Exception {
    when(service.current()).thenReturn(cfg(null, 50));
    when(service.patch(eq(true), eq(50)))
      .thenReturn(new TimeseriesQualityScoringConfigIO(true, 50));

    ObjectNode patch = JsonNodeFactory.instance.objectNode().put("enabled", true);
    TimeseriesQualityScoringConfigIO out = descriptor.applyMergePatch(patch);

    assertTrue(out.enabled());
    assertEquals(50, out.batchSize());
    verify(service).patch(true, 50);
  }

  @Test
  void patchBatchSize_leavesEnabledAlone() throws Exception {
    when(service.current()).thenReturn(cfg(true, null));
    when(service.patch(eq(true), eq(200)))
      .thenReturn(new TimeseriesQualityScoringConfigIO(true, 200));

    ObjectNode patch = JsonNodeFactory.instance.objectNode().put("batchSize", 200);
    TimeseriesQualityScoringConfigIO out = descriptor.applyMergePatch(patch);

    assertTrue(out.enabled());
    assertEquals(200, out.batchSize());
  }

  @Test
  void patchBothFields_updatesAll() throws Exception {
    when(service.current()).thenReturn(cfg(false, 100));
    when(service.patch(eq(true), eq(500)))
      .thenReturn(new TimeseriesQualityScoringConfigIO(true, 500));

    ObjectNode patch = JsonNodeFactory.instance.objectNode()
      .put("enabled", true)
      .put("batchSize", 500);
    TimeseriesQualityScoringConfigIO out = descriptor.applyMergePatch(patch);

    assertTrue(out.enabled());
    assertEquals(500, out.batchSize());
    verify(service).patch(true, 500);
  }

  @Test
  void patchNullEnabled_revertsToDefault() throws Exception {
    when(service.current()).thenReturn(cfg(true, 200));
    when(service.patch(isNull(), eq(200)))
      .thenReturn(new TimeseriesQualityScoringConfigIO(DEFAULT_ENABLED, 200));

    ObjectNode patch = JsonNodeFactory.instance.objectNode();
    patch.putNull("enabled");
    TimeseriesQualityScoringConfigIO out = descriptor.applyMergePatch(patch);

    assertFalse(out.enabled());
    verify(service).patch(null, 200);
  }

  @Test
  void patchNullBatchSize_revertsToDefault() throws Exception {
    when(service.current()).thenReturn(cfg(true, 500));
    when(service.patch(eq(true), isNull()))
      .thenReturn(new TimeseriesQualityScoringConfigIO(true, DEFAULT_BATCH_SIZE));

    ObjectNode patch = JsonNodeFactory.instance.objectNode();
    patch.putNull("batchSize");
    TimeseriesQualityScoringConfigIO out = descriptor.applyMergePatch(patch);

    assertEquals(DEFAULT_BATCH_SIZE, out.batchSize());
    verify(service).patch(true, null);
  }

  @Test
  void emptyPatch_leavesAllFieldsAlone() throws Exception {
    when(service.current()).thenReturn(cfg(true, 300));
    when(service.patch(eq(true), eq(300)))
      .thenReturn(new TimeseriesQualityScoringConfigIO(true, 300));

    ObjectNode patch = JsonNodeFactory.instance.objectNode();
    TimeseriesQualityScoringConfigIO out = descriptor.applyMergePatch(patch);

    assertTrue(out.enabled());
    assertEquals(300, out.batchSize());
  }

  @Test
  void zeroBatchSize_throws() {
    when(service.current()).thenReturn(cfg(false, 100));
    ConfigPatchException ex = assertThrows(
      ConfigPatchException.class,
      () -> descriptor.applyMergePatch(
        JsonNodeFactory.instance.objectNode().put("batchSize", 0)));
    assertEquals(
      TimeseriesQualityScoringConfigDescriptor.PROBLEM_TYPE_INVALID_BATCH_SIZE,
      ex.getProblemType());
  }

  @Test
  void negativeBatchSize_throws() {
    when(service.current()).thenReturn(cfg(false, 100));
    ConfigPatchException ex = assertThrows(
      ConfigPatchException.class,
      () -> descriptor.applyMergePatch(
        JsonNodeFactory.instance.objectNode().put("batchSize", -5)));
    assertEquals(
      TimeseriesQualityScoringConfigDescriptor.PROBLEM_TYPE_INVALID_BATCH_SIZE,
      ex.getProblemType());
  }

  @Test
  void configIO_from_resolvesNullsAgainstDefaults() {
    TimeseriesQualityScoringConfig c = new TimeseriesQualityScoringConfig();
    TimeseriesQualityScoringConfigIO io =
      TimeseriesQualityScoringConfigIO.from(c, DEFAULT_ENABLED, DEFAULT_BATCH_SIZE);
    assertFalse(io.enabled());
    assertEquals(DEFAULT_BATCH_SIZE, io.batchSize());
  }

  @Test
  void configIO_from_runtimeValuesWinOverDefaults() {
    TimeseriesQualityScoringConfig c = new TimeseriesQualityScoringConfig();
    c.setEnabled(true);
    c.setBatchSize(777);
    TimeseriesQualityScoringConfigIO io =
      TimeseriesQualityScoringConfigIO.from(c, DEFAULT_ENABLED, DEFAULT_BATCH_SIZE);
    assertTrue(io.enabled());
    assertEquals(777, io.batchSize());
  }
}
