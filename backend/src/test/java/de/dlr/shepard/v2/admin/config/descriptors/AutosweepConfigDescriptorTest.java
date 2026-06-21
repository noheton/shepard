package de.dlr.shepard.v2.admin.config.descriptors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.v2.admin.storage.entities.AutosweepConfig;
import de.dlr.shepard.v2.admin.storage.io.AutosweepConfigIO;
import de.dlr.shepard.v2.admin.storage.services.AutosweepConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** FTOGGLE-AUTOSWEEP-1 — unit tests for {@link AutosweepConfigDescriptor}. */
class AutosweepConfigDescriptorTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private AutosweepConfigService service;
  private AutosweepConfigDescriptor descriptor;

  private static AutosweepConfig cfg(Boolean enabled, String source, String target) {
    AutosweepConfig c = new AutosweepConfig();
    c.setEnabled(enabled);
    c.setSource(source);
    c.setTarget(target);
    return c;
  }

  @BeforeEach
  void setUp() {
    service = Mockito.mock(AutosweepConfigService.class);
    Mockito.lenient().when(service.getDefaultEnabled()).thenReturn(false);
    Mockito.lenient().when(service.getDefaultSource()).thenReturn("");
    Mockito.lenient().when(service.getDefaultTarget()).thenReturn("");
    descriptor = new AutosweepConfigDescriptor();
    descriptor.service = service;
  }

  @Test
  void featureNameIsAutosweep() {
    assertEquals("autosweep", descriptor.featureName());
  }

  @Test
  void currentShapeResolvesDefaults() {
    Mockito.when(service.current()).thenReturn(cfg(null, null, null));
    AutosweepConfigIO io = descriptor.currentShape();
    assertFalse(io.enabled());
    assertEquals("", io.source());
    assertEquals("", io.target());
  }

  @Test
  void patchAppliesEnabled() throws Exception {
    Mockito.when(service.current()).thenReturn(cfg(false, null, null));
    Mockito.when(service.patch(Mockito.any(), Mockito.any(), Mockito.any()))
      .thenAnswer(i -> cfg(i.getArgument(0), i.getArgument(1), i.getArgument(2)));

    AutosweepConfigIO io =
      descriptor.applyMergePatch(MAPPER.readTree("{\"enabled\":true}"));

    assertTrue(io.enabled());
    Mockito.verify(service).patch(true, null, null);
  }

  @Test
  void patchAppliesSourceAndTarget() throws Exception {
    Mockito.when(service.current()).thenReturn(cfg(false, null, null));
    Mockito.when(service.patch(Mockito.any(), Mockito.any(), Mockito.any()))
      .thenAnswer(i -> cfg(i.getArgument(0), i.getArgument(1), i.getArgument(2)));

    AutosweepConfigIO io =
      descriptor.applyMergePatch(MAPPER.readTree("{\"source\":\"gridfs\",\"target\":\"s3\"}"));

    assertEquals("gridfs", io.source());
    assertEquals("s3", io.target());
    Mockito.verify(service).patch(false, "gridfs", "s3");
  }

  @Test
  void nullSourceClearsToDefault() throws Exception {
    Mockito.when(service.current()).thenReturn(cfg(true, "gridfs", "s3"));
    Mockito.when(service.patch(Mockito.any(), Mockito.any(), Mockito.any()))
      .thenAnswer(i -> cfg(i.getArgument(0), i.getArgument(1), i.getArgument(2)));

    descriptor.applyMergePatch(MAPPER.readTree("{\"source\":null}"));
    Mockito.verify(service).patch(true, null, "s3");
  }

  @Test
  void absentFieldsAreLeftAlone() throws Exception {
    Mockito.when(service.current()).thenReturn(cfg(true, "gridfs", "s3"));
    Mockito.when(service.patch(Mockito.any(), Mockito.any(), Mockito.any()))
      .thenAnswer(i -> cfg(i.getArgument(0), i.getArgument(1), i.getArgument(2)));

    descriptor.applyMergePatch(MAPPER.readTree("{}"));
    Mockito.verify(service).patch(true, "gridfs", "s3");
  }
}
