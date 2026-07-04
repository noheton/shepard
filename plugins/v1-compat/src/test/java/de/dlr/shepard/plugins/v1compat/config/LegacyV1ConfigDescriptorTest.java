package de.dlr.shepard.plugins.v1compat.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.plugins.v1compat.entities.LegacyV1Config;
import de.dlr.shepard.plugins.v1compat.io.LegacyV1ConfigIO;
import de.dlr.shepard.plugins.v1compat.services.LegacyV1ConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** V2CONV-A7 — unit tests for {@link LegacyV1ConfigDescriptor}. */
class LegacyV1ConfigDescriptorTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private LegacyV1ConfigService service;
  private LegacyV1ConfigDescriptor descriptor;

  private static LegacyV1Config cfg(boolean enabled, boolean suppress) {
    LegacyV1Config c = new LegacyV1Config();
    c.setEnabled(enabled);
    c.setSuppressDeprecationHeaders(suppress);
    return c;
  }

  @BeforeEach
  void setUp() {
    service = Mockito.mock(LegacyV1ConfigService.class);
    descriptor = new LegacyV1ConfigDescriptor();
    descriptor.service = service;
  }

  @Test
  void featureNameIsLegacyV1() {
    assertEquals("legacy-v1", descriptor.featureName());
  }

  @Test
  void descriptionIsNonBlank() {
    String desc = descriptor.description();
    assertNotNull(desc);
    assertTrue(desc.length() > 10);
  }

  @Test
  void currentShape_delegatesToService() {
    Mockito.when(service.current()).thenReturn(cfg(true, false));

    LegacyV1ConfigIO io = descriptor.currentShape();

    assertTrue(io.enabled());
    assertFalse(io.suppressDeprecationHeaders());
  }

  @Test
  void enabledFlip_callsSetEnabled() throws Exception {
    Mockito.when(service.setEnabled(false, null)).thenReturn(cfg(false, false));
    Mockito.when(service.current()).thenReturn(cfg(false, false));

    descriptor.applyMergePatch(MAPPER.readTree("{\"enabled\":false}"));

    Mockito.verify(service).setEnabled(false, null);
  }

  @Test
  void explicitNullOnEnabledLeavesAlone() throws Exception {
    Mockito.when(service.current()).thenReturn(cfg(true, false));

    descriptor.applyMergePatch(MAPPER.readTree("{\"enabled\":null}"));

    Mockito.verify(service, Mockito.never()).setEnabled(Mockito.anyBoolean(), Mockito.any());
  }

  @Test
  void suppressDeprecationHeadersFlip() throws Exception {
    Mockito.when(service.setSuppressDeprecationHeaders(true, null)).thenReturn(cfg(true, true));
    Mockito.when(service.current()).thenReturn(cfg(true, true));

    descriptor.applyMergePatch(MAPPER.readTree("{\"suppressDeprecationHeaders\":true}"));

    Mockito.verify(service).setSuppressDeprecationHeaders(true, null);
  }

  @Test
  void explicitNullOnSuppressLeavesAlone() throws Exception {
    Mockito.when(service.current()).thenReturn(cfg(true, false));

    descriptor.applyMergePatch(MAPPER.readTree("{\"suppressDeprecationHeaders\":null}"));

    Mockito.verify(service, Mockito.never()).setSuppressDeprecationHeaders(Mockito.anyBoolean(), Mockito.any());
  }

  @Test
  void absentFieldsCallNoMutations() throws Exception {
    Mockito.when(service.current()).thenReturn(cfg(true, false));

    descriptor.applyMergePatch(MAPPER.readTree("{}"));

    Mockito.verify(service, Mockito.never()).setEnabled(Mockito.anyBoolean(), Mockito.any());
    Mockito.verify(service, Mockito.never()).setSuppressDeprecationHeaders(Mockito.anyBoolean(), Mockito.any());
    Mockito.verify(service).current();
  }

  @Test
  void patchBothFieldsTogether() throws Exception {
    Mockito.when(service.setEnabled(false, null)).thenReturn(cfg(false, false));
    Mockito.when(service.setSuppressDeprecationHeaders(true, null)).thenReturn(cfg(false, true));
    Mockito.when(service.current()).thenReturn(cfg(false, true));

    LegacyV1ConfigIO io = descriptor.applyMergePatch(
      MAPPER.readTree("{\"enabled\":false,\"suppressDeprecationHeaders\":true}")
    );

    Mockito.verify(service).setEnabled(false, null);
    Mockito.verify(service).setSuppressDeprecationHeaders(true, null);
    assertFalse(io.enabled());
    assertTrue(io.suppressDeprecationHeaders());
  }
}
