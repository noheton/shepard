package de.dlr.shepard.v2.admin.config.descriptors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.v2.admin.config.spi.ConfigPatchException;
import de.dlr.shepard.v2.admin.provenance.entities.ProvenanceConfig;
import de.dlr.shepard.v2.admin.provenance.io.ProvenanceConfigIO;
import de.dlr.shepard.v2.admin.provenance.services.ProvenanceConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** FTOGGLE-PROV-1 — unit tests for {@link ProvenanceConfigDescriptor}. */
class ProvenanceConfigDescriptorTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private ProvenanceConfigService service;
  private ProvenanceConfigDescriptor descriptor;

  private static ProvenanceConfig cfg(Boolean enabled, Boolean captureReads, Long retentionDays) {
    ProvenanceConfig c = new ProvenanceConfig();
    c.setEnabled(enabled);
    c.setCaptureReads(captureReads);
    c.setRetentionDays(retentionDays);
    return c;
  }

  @BeforeEach
  void setUp() {
    service = Mockito.mock(ProvenanceConfigService.class);
    Mockito.when(service.getDefaultEnabled()).thenReturn(true);
    Mockito.when(service.getDefaultCaptureReads()).thenReturn(false);
    Mockito.when(service.getDefaultRetentionDays()).thenReturn(730L);
    descriptor = new ProvenanceConfigDescriptor();
    descriptor.service = service;
  }

  @Test
  void featureNameIsProvenance() {
    assertEquals("provenance", descriptor.featureName());
  }

  @Test
  void currentShapeResolvesDefaults() {
    Mockito.when(service.current()).thenReturn(cfg(null, null, null));
    ProvenanceConfigIO io = descriptor.currentShape();
    assertTrue(io.enabled());
    assertEquals(false, io.captureReads());
    assertEquals(730L, io.retentionDays());
  }

  @Test
  void patchAppliesNewValues() throws Exception {
    Mockito.when(service.current()).thenReturn(cfg(true, false, 365L));
    Mockito.when(service.patch(Mockito.any(), Mockito.any(), Mockito.any()))
      .thenAnswer(i -> cfg(i.getArgument(0), i.getArgument(1), i.getArgument(2)));

    ProvenanceConfigIO io =
      descriptor.applyMergePatch(MAPPER.readTree("{\"retentionDays\":180}"));

    assertEquals(180L, io.retentionDays());
    Mockito.verify(service).patch(true, false, 180L);
  }

  @Test
  void nonPositiveRetentionDaysThrows() {
    Mockito.when(service.current()).thenReturn(cfg(true, false, 365L));
    ConfigPatchException ex = assertThrows(
      ConfigPatchException.class,
      () -> descriptor.applyMergePatch(MAPPER.readTree("{\"retentionDays\":0}"))
    );
    assertEquals(ProvenanceConfigDescriptor.PROBLEM_TYPE_INVALID_RETENTION_DAYS, ex.getProblemType());
  }

  @Test
  void nullRetentionDaysClearsToDefault() throws Exception {
    Mockito.when(service.current()).thenReturn(cfg(true, false, 365L));
    Mockito.when(service.patch(Mockito.any(), Mockito.any(), Mockito.any()))
      .thenAnswer(i -> cfg(i.getArgument(0), i.getArgument(1), i.getArgument(2)));

    descriptor.applyMergePatch(MAPPER.readTree("{\"retentionDays\":null}"));
    Mockito.verify(service).patch(true, false, null);
  }

  @Test
  void patchEnabledFalseDisablesCapture() throws Exception {
    Mockito.when(service.current()).thenReturn(cfg(true, false, 730L));
    Mockito.when(service.patch(Mockito.any(), Mockito.any(), Mockito.any()))
      .thenAnswer(i -> cfg(i.getArgument(0), i.getArgument(1), i.getArgument(2)));

    ProvenanceConfigIO io =
      descriptor.applyMergePatch(MAPPER.readTree("{\"enabled\":false}"));

    assertEquals(false, io.enabled());
    Mockito.verify(service).patch(false, false, 730L);
  }

  @Test
  void absentFieldsAreLeftAlone() throws Exception {
    Mockito.when(service.current()).thenReturn(cfg(true, true, 365L));
    Mockito.when(service.patch(Mockito.any(), Mockito.any(), Mockito.any()))
      .thenAnswer(i -> cfg(i.getArgument(0), i.getArgument(1), i.getArgument(2)));

    descriptor.applyMergePatch(MAPPER.readTree("{}"));
    // No field touched — should pass current values through unchanged.
    Mockito.verify(service).patch(true, true, 365L);
  }
}
