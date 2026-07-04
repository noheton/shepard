package de.dlr.shepard.plugins.aas.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.plugins.aas.entities.AasConfig;
import de.dlr.shepard.plugins.aas.io.AasConfigIO;
import de.dlr.shepard.plugins.aas.services.AasConfigService;
import de.dlr.shepard.plugins.aas.services.AasConfigService.AasPatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/** V2CONV-A7 — unit tests for {@link AasConfigDescriptor}. */
class AasConfigDescriptorTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private AasConfigService service;
  private AasConfigDescriptor descriptor;

  private static AasConfig cfg(boolean enabled, String url, String key, String base) {
    AasConfig c = new AasConfig();
    c.setEnabled(enabled);
    c.setRegistryUrl(url);
    c.setRegistryApiKey(key);
    c.setBaseUrl(base);
    return c;
  }

  @BeforeEach
  void setUp() {
    service = Mockito.mock(AasConfigService.class);
    descriptor = new AasConfigDescriptor();
    descriptor.service = service;
  }

  @Test
  void featureNameIsAas() {
    assertEquals("aas", descriptor.featureName());
  }

  @Test
  void descriptionIsNonBlank() {
    String desc = descriptor.description();
    assertNotNull(desc);
    assertTrue(desc.length() > 10);
  }

  @Test
  void currentShape_delegatesToService() {
    Mockito.when(service.current()).thenReturn(cfg(true, "https://aas.example.com", "k", "https://base.example.com"));

    AasConfigIO io = descriptor.currentShape();

    assertTrue(io.enabled());
    assertEquals("https://aas.example.com", io.registryUrl());
    assertTrue(io.apiKeyPresent());
    assertEquals("https://base.example.com", io.baseUrl());
  }

  @Test
  void currentShape_noKeyShowsApiKeyPresentFalse() {
    Mockito.when(service.current()).thenReturn(cfg(false, null, null, null));

    AasConfigIO io = descriptor.currentShape();

    assertFalse(io.enabled());
    assertFalse(io.apiKeyPresent());
  }

  @Test
  void enabledFlip() throws Exception {
    ArgumentCaptor<AasPatch> cap = ArgumentCaptor.forClass(AasPatch.class);
    Mockito.when(service.patch(cap.capture())).thenReturn(cfg(true, null, null, null));

    descriptor.applyMergePatch(MAPPER.readTree("{\"enabled\":true}"));

    assertEquals(Boolean.TRUE, cap.getValue().enabled);
  }

  @Test
  void explicitNullOnEnabledLeavesAlone() throws Exception {
    ArgumentCaptor<AasPatch> cap = ArgumentCaptor.forClass(AasPatch.class);
    Mockito.when(service.patch(cap.capture())).thenReturn(cfg(false, null, null, null));

    descriptor.applyMergePatch(MAPPER.readTree("{\"enabled\":null}"));

    assertNull(cap.getValue().enabled, "null enabled must leave field untouched");
  }

  @Test
  void registryUrlSet() throws Exception {
    ArgumentCaptor<AasPatch> cap = ArgumentCaptor.forClass(AasPatch.class);
    Mockito.when(service.patch(cap.capture())).thenReturn(cfg(false, "https://new.example.com", null, null));

    descriptor.applyMergePatch(MAPPER.readTree("{\"registryUrl\":\"https://new.example.com\"}"));

    assertEquals("https://new.example.com", cap.getValue().registryUrl);
    assertTrue(cap.getValue().registryUrlTouched);
  }

  @Test
  void registryUrlNullClears() throws Exception {
    ArgumentCaptor<AasPatch> cap = ArgumentCaptor.forClass(AasPatch.class);
    Mockito.when(service.patch(cap.capture())).thenReturn(cfg(false, null, null, null));

    descriptor.applyMergePatch(MAPPER.readTree("{\"registryUrl\":null}"));

    assertNull(cap.getValue().registryUrl);
    assertTrue(cap.getValue().registryUrlTouched, "touched flag must be set even for null");
  }

  @Test
  void registryApiKeySet() throws Exception {
    ArgumentCaptor<AasPatch> cap = ArgumentCaptor.forClass(AasPatch.class);
    Mockito.when(service.patch(cap.capture())).thenReturn(cfg(false, null, "secret", null));

    descriptor.applyMergePatch(MAPPER.readTree("{\"registryApiKey\":\"secret\"}"));

    assertEquals("secret", cap.getValue().registryApiKey);
    assertTrue(cap.getValue().registryApiKeyTouched);
  }

  @Test
  void registryApiKeyNullRevokes() throws Exception {
    ArgumentCaptor<AasPatch> cap = ArgumentCaptor.forClass(AasPatch.class);
    Mockito.when(service.patch(cap.capture())).thenReturn(cfg(false, null, null, null));

    descriptor.applyMergePatch(MAPPER.readTree("{\"registryApiKey\":null}"));

    assertNull(cap.getValue().registryApiKey);
    assertTrue(cap.getValue().registryApiKeyTouched);
  }

  @Test
  void baseUrlSet() throws Exception {
    ArgumentCaptor<AasPatch> cap = ArgumentCaptor.forClass(AasPatch.class);
    Mockito.when(service.patch(cap.capture())).thenReturn(cfg(false, null, null, "https://base.example.com"));

    descriptor.applyMergePatch(MAPPER.readTree("{\"baseUrl\":\"https://base.example.com\"}"));

    assertEquals("https://base.example.com", cap.getValue().baseUrl);
    assertTrue(cap.getValue().baseUrlTouched);
  }

  @Test
  void baseUrlNullClears() throws Exception {
    ArgumentCaptor<AasPatch> cap = ArgumentCaptor.forClass(AasPatch.class);
    Mockito.when(service.patch(cap.capture())).thenReturn(cfg(false, null, null, null));

    descriptor.applyMergePatch(MAPPER.readTree("{\"baseUrl\":null}"));

    assertNull(cap.getValue().baseUrl);
    assertTrue(cap.getValue().baseUrlTouched);
  }

  @Test
  void absentFieldsAreNotTouched() throws Exception {
    ArgumentCaptor<AasPatch> cap = ArgumentCaptor.forClass(AasPatch.class);
    Mockito.when(service.patch(cap.capture())).thenReturn(cfg(false, null, null, null));

    descriptor.applyMergePatch(MAPPER.readTree("{}"));

    assertNull(cap.getValue().enabled, "absent enabled → null (leave alone)");
    assertFalse(cap.getValue().registryUrlTouched, "absent registryUrl → touched=false");
    assertFalse(cap.getValue().registryApiKeyTouched, "absent registryApiKey → touched=false");
    assertFalse(cap.getValue().baseUrlTouched, "absent baseUrl → touched=false");
  }
}
