package de.dlr.shepard.v2.admin.hdf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.data.hdf.entities.HdfConfig;
import de.dlr.shepard.data.hdf.io.HdfConfigIO;
import de.dlr.shepard.data.hdf.services.HdfConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** FTOGGLE-HDF-ENABLE-1 — unit tests for {@link HdfConfigDescriptor}. */
class HdfConfigDescriptorTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private HdfConfigService service;
  private HdfConfigDescriptor descriptor;

  private static HdfConfig cfg(Boolean enabled) {
    HdfConfig c = new HdfConfig();
    c.setEnabled(enabled);
    return c;
  }

  @BeforeEach
  void setUp() {
    service = Mockito.mock(HdfConfigService.class);
    Mockito.when(service.getDefaultEnabled()).thenReturn(true);
    descriptor = new HdfConfigDescriptor();
    descriptor.service = service;
  }

  @Test
  void featureNameIsHdf() {
    assertEquals("hdf", descriptor.featureName());
  }

  @Test
  void currentShapeResolvesDefault() {
    Mockito.when(service.current()).thenReturn(cfg(null));
    HdfConfigIO io = descriptor.currentShape();
    assertEquals(true, io.enabled());
  }

  @Test
  void patchDisablesFeature() throws Exception {
    Mockito.when(service.current()).thenReturn(cfg(true));
    Mockito.when(service.patch(Mockito.any())).thenAnswer(i -> cfg(i.getArgument(0)));
    HdfConfigIO io = descriptor.applyMergePatch(MAPPER.readTree("{\"enabled\":false}"));
    assertEquals(false, io.enabled());
    Mockito.verify(service).patch(false);
  }

  @Test
  void patchEnablesFeature() throws Exception {
    Mockito.when(service.current()).thenReturn(cfg(false));
    Mockito.when(service.patch(Mockito.any())).thenAnswer(i -> cfg(i.getArgument(0)));
    HdfConfigIO io = descriptor.applyMergePatch(MAPPER.readTree("{\"enabled\":true}"));
    assertEquals(true, io.enabled());
    Mockito.verify(service).patch(true);
  }

  @Test
  void nullClearsToDefault() throws Exception {
    Mockito.when(service.current()).thenReturn(cfg(false));
    Mockito.when(service.patch(Mockito.any())).thenAnswer(i -> cfg(i.getArgument(0)));
    descriptor.applyMergePatch(MAPPER.readTree("{\"enabled\":null}"));
    Mockito.verify(service).patch(null);
  }

  @Test
  void absentFieldIsLeftAlone() throws Exception {
    Mockito.when(service.current()).thenReturn(cfg(false));
    Mockito.when(service.patch(Mockito.any())).thenAnswer(i -> cfg(i.getArgument(0)));
    descriptor.applyMergePatch(MAPPER.readTree("{}"));
    // should patch with the current value (false) — field left alone
    Mockito.verify(service).patch(false);
  }
}
