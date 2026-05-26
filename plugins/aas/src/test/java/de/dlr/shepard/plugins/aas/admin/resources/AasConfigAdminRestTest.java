package de.dlr.shepard.plugins.aas.admin.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.plugins.aas.entities.AasConfig;
import de.dlr.shepard.plugins.aas.io.AasConfigIO;
import de.dlr.shepard.plugins.aas.io.AasConfigPatchIO;
import de.dlr.shepard.plugins.aas.services.AasConfigService;
import de.dlr.shepard.plugins.aas.services.AasConfigService.AasPatch;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * AAS1l — unit tests for {@link AasConfigAdminRest}.
 *
 * <p>Pattern follows {@code UnhideAdminRestTest}: pure-unit with
 * Mockito mocks; no Quarkus CDI / REST Assured / Testcontainer.
 */
class AasConfigAdminRestTest {

  private AasConfigService service;
  private AasConfigAdminRest rest;

  @BeforeEach
  void setUp() {
    service = mock(AasConfigService.class);
    rest = new AasConfigAdminRest();
    rest.service = service;
  }

  // ─── annotation gates ────────────────────────────────────────────

  @Test
  void classCarriesInstanceAdminGate() {
    RolesAllowed gate = AasConfigAdminRest.class.getAnnotation(RolesAllowed.class);
    assertNotNull(gate, "AasConfigAdminRest must be @RolesAllowed-gated at class level");
    assertEquals(1, gate.value().length);
    assertEquals(Constants.INSTANCE_ADMIN_ROLE, gate.value()[0]);
  }

  @Test
  void pathIsV2AdminAasConfig() {
    Path p = AasConfigAdminRest.class.getAnnotation(Path.class);
    assertNotNull(p);
    assertEquals("/v2/admin/aas/config", p.value(), "endpoint lives on the /v2/admin/aas/config path per AAS1l");
  }

  // ─── GET /v2/admin/aas/config ─────────────────────────────────────

  @Test
  void getConfig_returnsIoWithMaskedApiKey() {
    AasConfig cfg = new AasConfig();
    cfg.setEnabled(true);
    cfg.setRegistryUrl("https://registry.example.dlr.de");
    cfg.setRegistryApiKey("secret-token-abc123");
    cfg.setBaseUrl("https://shepard.example.dlr.de");
    when(service.current()).thenReturn(cfg);

    Response r = rest.getConfig();

    assertEquals(200, r.getStatus());
    AasConfigIO body = (AasConfigIO) r.getEntity();
    assertTrue(body.enabled());
    assertEquals("https://registry.example.dlr.de", body.registryUrl());
    assertTrue(body.apiKeyPresent(), "apiKeyPresent must be true when a key is stored");
    assertEquals("https://shepard.example.dlr.de", body.baseUrl());
  }

  @Test
  void getConfig_apiKeyPresentFalse_whenNoKeyStored() {
    AasConfig cfg = new AasConfig();
    cfg.setEnabled(false);
    // no apiKey set
    when(service.current()).thenReturn(cfg);

    Response r = rest.getConfig();

    assertEquals(200, r.getStatus());
    AasConfigIO body = (AasConfigIO) r.getEntity();
    assertFalse(body.apiKeyPresent(), "apiKeyPresent must be false when no key is stored");
    assertNull(body.registryUrl());
    assertNull(body.baseUrl());
  }

  @Test
  void getConfig_rawApiKeyNeverInResponseShape() {
    AasConfig cfg = new AasConfig();
    cfg.setRegistryApiKey("super-secret-should-not-leak");
    when(service.current()).thenReturn(cfg);

    Response r = rest.getConfig();

    AasConfigIO body = (AasConfigIO) r.getEntity();
    // Verify the IO record does not carry the plaintext key anywhere.
    // AasConfigIO only has: enabled, registryUrl, apiKeyPresent, baseUrl.
    // The apiKeyPresent field is a boolean — impossible to leak the key.
    String rendered = body.toString();
    assertFalse(rendered.contains("super-secret"), "raw API key must never appear in the IO toString(): " + rendered);
  }

  // ─── PATCH /v2/admin/aas/config ───────────────────────────────────

  @Test
  void patchConfig_appliesPatch_returnsUpdatedIO() {
    AasConfig cfg = new AasConfig();
    cfg.setEnabled(true);
    cfg.setRegistryUrl("https://registry.example.dlr.de");
    when(service.patch(org.mockito.ArgumentMatchers.any(AasPatch.class))).thenReturn(cfg);

    AasConfigPatchIO body = new AasConfigPatchIO();
    body.setEnabled(Boolean.TRUE);
    body.setRegistryUrl("https://registry.example.dlr.de");

    Response r = rest.patchConfig(body);

    assertEquals(200, r.getStatus());
    AasConfigIO out = (AasConfigIO) r.getEntity();
    assertTrue(out.enabled());
    assertEquals("https://registry.example.dlr.de", out.registryUrl());
  }

  @Test
  void patchConfig_passesPatchFieldsThroughToService() {
    AasConfig cfg = new AasConfig();
    ArgumentCaptor<AasPatch> captor = ArgumentCaptor.forClass(AasPatch.class);
    when(service.patch(captor.capture())).thenReturn(cfg);

    AasConfigPatchIO body = new AasConfigPatchIO();
    body.setEnabled(Boolean.FALSE);
    body.setRegistryUrl("https://registry.example.dlr.de");
    body.setRegistryApiKey("my-token");
    body.setBaseUrl("https://shepard.example.dlr.de");

    rest.patchConfig(body);

    AasPatch captured = captor.getValue();
    assertEquals(Boolean.FALSE, captured.enabled);
    assertEquals("https://registry.example.dlr.de", captured.registryUrl);
    assertTrue(captured.registryUrlTouched, "setRegistryUrl flips registryUrlTouched");
    assertEquals("my-token", captured.registryApiKey);
    assertTrue(captured.registryApiKeyTouched, "setRegistryApiKey flips registryApiKeyTouched");
    assertEquals("https://shepard.example.dlr.de", captured.baseUrl);
    assertTrue(captured.baseUrlTouched, "setBaseUrl flips baseUrlTouched");
  }

  @Test
  void patchConfig_nullApiKey_clearsKey() {
    AasConfig cfg = new AasConfig();
    cfg.setRegistryApiKey(null); // cleared
    ArgumentCaptor<AasPatch> captor = ArgumentCaptor.forClass(AasPatch.class);
    when(service.patch(captor.capture())).thenReturn(cfg);

    AasConfigPatchIO body = new AasConfigPatchIO();
    body.setRegistryApiKey(null); // explicit null = revoke

    rest.patchConfig(body);

    AasPatch captured = captor.getValue();
    assertNull(captured.registryApiKey, "explicit null on apiKey means revoke/clear");
    assertTrue(captured.registryApiKeyTouched, "touched flag must be set even for explicit null");
  }

  @Test
  void patchConfig_nullBody_treatedAsEmptyPatch() {
    AasConfig cfg = new AasConfig();
    when(service.patch(org.mockito.ArgumentMatchers.any(AasPatch.class))).thenReturn(cfg);

    Response r = rest.patchConfig(null);

    assertEquals(200, r.getStatus(), "null body is a legal RFC 7396 no-op patch");
  }

  @Test
  void patchConfig_absentFields_doNotTouchService() {
    AasConfig cfg = new AasConfig();
    cfg.setEnabled(true);
    cfg.setRegistryUrl("https://existing.dlr.de");
    ArgumentCaptor<AasPatch> captor = ArgumentCaptor.forClass(AasPatch.class);
    when(service.patch(captor.capture())).thenReturn(cfg);

    // Only patch enabled — leave everything else alone
    AasConfigPatchIO body = new AasConfigPatchIO();
    body.setEnabled(Boolean.TRUE);
    // registryUrl, registryApiKey, baseUrl are NOT set (absent in JSON)

    rest.patchConfig(body);

    AasPatch captured = captor.getValue();
    assertEquals(Boolean.TRUE, captured.enabled);
    assertFalse(captured.registryUrlTouched, "absent registryUrl must NOT set the touched flag");
    assertFalse(captured.registryApiKeyTouched, "absent registryApiKey must NOT set the touched flag");
    assertFalse(captured.baseUrlTouched, "absent baseUrl must NOT set the touched flag");
  }
}
