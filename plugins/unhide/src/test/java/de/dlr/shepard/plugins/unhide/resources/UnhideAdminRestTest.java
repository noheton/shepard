package de.dlr.shepard.plugins.unhide.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.plugins.unhide.entities.UnhideConfig;
import de.dlr.shepard.plugins.unhide.io.HarvestKeyMintedIO;
import de.dlr.shepard.plugins.unhide.io.UnhideConfigIO;
import de.dlr.shepard.plugins.unhide.io.UnhideConfigPatchIO;
import de.dlr.shepard.plugins.unhide.services.UnhideConfigService;
import de.dlr.shepard.plugins.unhide.services.UnhideConfigService.MintResult;
import de.dlr.shepard.plugins.unhide.services.UnhideConfigService.ReadOnlyFieldException;
import de.dlr.shepard.plugins.unhide.services.UnhideConfigService.UnhidePatch;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UnhideAdminRestTest {

  private UnhideConfigService service;
  private UnhideAdminRest rest;

  @BeforeEach
  void setUp() {
    service = mock(UnhideConfigService.class);
    rest = new UnhideAdminRest();
    rest.service = service;
  }

  // ─── annotation gates ────────────────────────────────────────────────────

  @Test
  void classCarriesInstanceAdminGate() {
    RolesAllowed gate = UnhideAdminRest.class.getAnnotation(RolesAllowed.class);
    assertNotNull(gate, "UnhideAdminRest must be @RolesAllowed-gated at class level");
    assertEquals(1, gate.value().length);
    assertEquals(Constants.INSTANCE_ADMIN_ROLE, gate.value()[0]);
  }

  @Test
  void pathIsV2() {
    Path p = UnhideAdminRest.class.getAnnotation(Path.class);
    assertNotNull(p);
    assertEquals("/v2/admin/unhide", p.value(), "endpoint lives on the /v2/ shelf per fork policy");
  }

  // ─── GET /config ─────────────────────────────────────────────────────────

  @Test
  void getConfig_returnsMaskedIO() {
    UnhideConfig cfg = new UnhideConfig();
    cfg.setEnabled(true);
    cfg.setFeedPublic(false);
    cfg.setContactEmail("ops@example.dlr.de");
    cfg.setHarvestApiKeyHash("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    cfg.setHarvestApiKeyLastRotatedAt(1700000000000L);
    when(service.current()).thenReturn(cfg);

    Response r = rest.getConfig();

    assertEquals(200, r.getStatus());
    UnhideConfigIO body = (UnhideConfigIO) r.getEntity();
    assertTrue(body.enabled());
    assertEquals("ops@example.dlr.de", body.contactEmail());
    assertEquals("ba7816bf", body.harvestApiKeyFingerprint(), "fingerprint = first 8 hex chars");
    // CRITICAL — the response must NEVER carry the raw hash. We assert
    // that the IO's toString() (a proxy for the JSON serialisation
    // shape) carries only the first-8 fingerprint and never the tail.
    String rendered = body.toString();
    assertTrue(rendered.contains("ba7816bf"), "fingerprint surfaces: " + rendered);
    assertTrue(!rendered.contains("8f01cfea"),
      "raw hash tail must not appear in the IO toString(): " + rendered);
  }

  // ─── PATCH /config ───────────────────────────────────────────────────────

  @Test
  void patchConfig_appliesPatch() {
    UnhideConfig cfg = new UnhideConfig();
    cfg.setEnabled(true);
    when(service.patch(org.mockito.ArgumentMatchers.any(UnhidePatch.class))).thenReturn(cfg);

    UnhideConfigPatchIO body = new UnhideConfigPatchIO();
    body.setEnabled(Boolean.TRUE);

    Response r = rest.patchConfig(body);

    assertEquals(200, r.getStatus());
    UnhideConfigIO out = (UnhideConfigIO) r.getEntity();
    assertTrue(out.enabled());
  }

  @Test
  void patchConfig_passesPatchFieldsThroughToService() {
    UnhideConfig cfg = new UnhideConfig();
    ArgumentCaptor<UnhidePatch> captor = ArgumentCaptor.forClass(UnhidePatch.class);
    when(service.patch(captor.capture())).thenReturn(cfg);

    UnhideConfigPatchIO body = new UnhideConfigPatchIO();
    body.setEnabled(Boolean.FALSE);
    body.setFeedPublic(Boolean.TRUE);
    body.setContactEmail("alice@example.dlr.de");

    rest.patchConfig(body);

    UnhidePatch captured = captor.getValue();
    assertEquals(Boolean.FALSE, captured.enabled);
    assertEquals(Boolean.TRUE, captured.feedPublic);
    assertEquals("alice@example.dlr.de", captured.contactEmail);
    assertTrue(captured.contactEmailTouched, "setContactEmail flips the touched flag");
  }

  @Test
  void patchConfig_returnsProblemJson_whenReadOnlyFieldTouched() {
    when(service.patch(org.mockito.ArgumentMatchers.any(UnhidePatch.class)))
      .thenThrow(new ReadOnlyFieldException("harvestApiKeyHash"));

    UnhideConfigPatchIO body = new UnhideConfigPatchIO();
    body.setHarvestApiKeyHash("anything"); // setter flips harvestApiKeyHashTouched

    Response r = rest.patchConfig(body);

    assertEquals(400, r.getStatus());
    assertEquals("application/problem+json", r.getMediaType().toString());
    ProblemJson problem = assertInstanceOf(ProblemJson.class, r.getEntity());
    assertEquals(UnhideAdminRest.PROBLEM_TYPE_READ_ONLY_FIELD, problem.type());
    assertEquals(400, problem.status());
    assertTrue(problem.detail().contains("harvestApiKeyHash"), "detail names the offending field");
  }

  @Test
  void patchConfig_nullBody_treatedAsEmptyPatch() {
    UnhideConfig cfg = new UnhideConfig();
    when(service.patch(org.mockito.ArgumentMatchers.any(UnhidePatch.class))).thenReturn(cfg);

    Response r = rest.patchConfig(null);

    assertEquals(200, r.getStatus(), "null body is a legal RFC 7396 no-op patch");
  }

  // ─── POST /harvest-key/rotate ────────────────────────────────────────────

  @Test
  void rotateHarvestKey_returnsPlaintextWithWarning() {
    UnhideConfig cfg = new UnhideConfig();
    cfg.setHarvestApiKeyHash("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
    cfg.setHarvestApiKeyLastRotatedAt(1700000001234L);
    when(service.rotateHarvestKey())
      .thenReturn(new MintResult("11111111-2222-4333-8444-555555555555", cfg));

    Response r = rest.rotateHarvestKey();

    assertEquals(200, r.getStatus());
    HarvestKeyMintedIO body = (HarvestKeyMintedIO) r.getEntity();
    assertEquals("11111111-2222-4333-8444-555555555555", body.harvestApiKey());
    assertEquals("01234567", body.fingerprint());
    assertNotNull(body.mintedAt());
    assertNotNull(body.warning(), "warning text must be present");
    assertTrue(body.warning().contains("only time"), "warning explains one-shot nature");
  }

  // ─── revoke (both POST + DELETE shapes) ──────────────────────────────────

  @Test
  void revokeHarvestKey_postShape() {
    UnhideConfig cfg = new UnhideConfig();
    when(service.revokeHarvestKey()).thenReturn(cfg);

    Response r = rest.revokeHarvestKey();

    assertEquals(200, r.getStatus());
    UnhideConfigIO body = (UnhideConfigIO) r.getEntity();
    assertNull(body.harvestApiKeyFingerprint());
  }

  @Test
  void revokeHarvestKey_deleteShape_equivalentToPost() {
    UnhideConfig cfg = new UnhideConfig();
    when(service.revokeHarvestKey()).thenReturn(cfg);

    Response r = rest.deleteHarvestKey();

    assertEquals(200, r.getStatus());
    assertInstanceOf(UnhideConfigIO.class, r.getEntity());
  }
}
