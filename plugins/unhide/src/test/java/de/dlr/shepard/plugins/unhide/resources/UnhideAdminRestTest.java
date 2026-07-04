package de.dlr.shepard.plugins.unhide.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.plugins.unhide.entities.UnhideConfig;
import de.dlr.shepard.plugins.unhide.io.HarvestKeyMintedIO;
import de.dlr.shepard.plugins.unhide.io.UnhideConfigIO;
import de.dlr.shepard.plugins.unhide.services.UnhideConfigService;
import de.dlr.shepard.plugins.unhide.services.UnhideConfigService.MintResult;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * V2CONV-A7 — GET/PATCH /config moved to {@link de.dlr.shepard.plugins.unhide.config.UnhideConfigDescriptor};
 * this test class covers harvest-key operations only.
 */
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
