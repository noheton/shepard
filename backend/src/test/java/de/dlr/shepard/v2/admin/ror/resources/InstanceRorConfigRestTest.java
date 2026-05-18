package de.dlr.shepard.v2.admin.ror;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.v2.admin.ror.entities.InstanceRorConfig;
import de.dlr.shepard.v2.admin.ror.io.InstanceRorConfigIO;
import de.dlr.shepard.v2.admin.ror.io.InstanceRorConfigPatchIO;
import de.dlr.shepard.v2.admin.ror.resources.InstanceRorConfigRest;
import de.dlr.shepard.v2.admin.ror.services.InstanceRorConfigService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * ROR1 — unit tests for the admin REST surface.
 *
 * <p>No Quarkus boot — {@link InstanceRorConfigService} is mocked.
 * Tests cover: GET round-trip, PATCH updates rorId, PATCH with null
 * clears rorId, PATCH with invalid rorId returns 400, annotation-gate
 * assertion (401/403), and rorUrl computed correctly.
 */
class InstanceRorConfigRestTest {

  private InstanceRorConfigService service;
  private InstanceRorConfigRest rest;

  @BeforeEach
  void setUp() {
    service = mock(InstanceRorConfigService.class);
    rest = new InstanceRorConfigRest();
    rest.service = service;
  }

  // ─── annotation gates ────────────────────────────────────────────────────

  /**
   * Test #4 (spec): "401 unauthenticated" — we assert the class-level
   * {@code @RolesAllowed} gate which is the JAX-RS mechanism enforcing
   * authentication + role checks. A wire-level 401 test requires a full
   * Quarkus integration test harness.
   */
  @Test
  void classCarriesInstanceAdminGate() {
    RolesAllowed gate = InstanceRorConfigRest.class.getAnnotation(RolesAllowed.class);
    assertNotNull(gate, "InstanceRorConfigRest must be @RolesAllowed-gated at class level");
    assertEquals(1, gate.value().length);
    assertEquals(Constants.INSTANCE_ADMIN_ROLE, gate.value()[0]);
  }

  @Test
  void pathIsV2AdminInstanceRor() {
    Path p = InstanceRorConfigRest.class.getAnnotation(Path.class);
    assertNotNull(p);
    assertEquals("/v2/admin/instance/ror", p.value(), "endpoint lives on the /v2/ shelf per fork policy");
  }

  // ─── Test #1: GET returns config ────────────────────────────────────────

  @Test
  void getConfig_returnsCurrentConfig() {
    InstanceRorConfig cfg = new InstanceRorConfig();
    cfg.setRorId("04cvxnb49");
    cfg.setOrganizationName("DLR e.V.");
    when(service.current()).thenReturn(cfg);

    Response r = rest.getConfig();

    assertEquals(200, r.getStatus());
    InstanceRorConfigIO body = (InstanceRorConfigIO) r.getEntity();
    assertEquals("04cvxnb49", body.rorId());
    assertEquals("DLR e.V.", body.organizationName());
    assertEquals("https://ror.org/04cvxnb49", body.rorUrl());
  }

  // ─── Test #5 (spec): computes rorUrl correctly ───────────────────────────

  @Test
  void getConfig_computesRorUrl_fromRorId() {
    InstanceRorConfig cfg = new InstanceRorConfig();
    cfg.setRorId("0abcdef12");
    when(service.current()).thenReturn(cfg);

    Response r = rest.getConfig();
    InstanceRorConfigIO body = (InstanceRorConfigIO) r.getEntity();
    assertEquals("https://ror.org/0abcdef12", body.rorUrl());
  }

  @Test
  void getConfig_rorUrl_isNull_whenRorIdNotSet() {
    InstanceRorConfig cfg = new InstanceRorConfig();
    when(service.current()).thenReturn(cfg);

    Response r = rest.getConfig();
    InstanceRorConfigIO body = (InstanceRorConfigIO) r.getEntity();
    assertNull(body.rorId());
    assertNull(body.rorUrl(), "rorUrl must be null when rorId is not set");
  }

  // ─── Test #2: PATCH updates rorId ───────────────────────────────────────

  @Test
  void patchConfig_updatesRorId() {
    InstanceRorConfig current = new InstanceRorConfig();
    InstanceRorConfig updated = new InstanceRorConfig();
    updated.setRorId("04cvxnb49");
    updated.setOrganizationName("DLR e.V.");

    when(service.current()).thenReturn(current);
    when(service.patch(nullable(String.class), nullable(String.class))).thenReturn(updated);

    InstanceRorConfigPatchIO body = new InstanceRorConfigPatchIO();
    body.setRorId("04cvxnb49");
    body.setOrganizationName("DLR e.V.");

    Response r = rest.patchConfig(body);

    assertEquals(200, r.getStatus());
    InstanceRorConfigIO out = (InstanceRorConfigIO) r.getEntity();
    assertEquals("04cvxnb49", out.rorId());
    assertEquals("https://ror.org/04cvxnb49", out.rorUrl());
  }

  // ─── Test #3: PATCH with null clears rorId ──────────────────────────────

  @Test
  void patchConfig_withNullRorId_clearsField() {
    InstanceRorConfig current = new InstanceRorConfig();
    current.setRorId("04cvxnb49");
    InstanceRorConfig cleared = new InstanceRorConfig();
    // rorId and organizationName both null after clear

    when(service.current()).thenReturn(current);
    when(service.patch(isNull(), isNull())).thenReturn(cleared);

    InstanceRorConfigPatchIO body = new InstanceRorConfigPatchIO();
    body.setRorId(null);           // explicit null = clear
    body.setOrganizationName(null); // explicit null = clear

    Response r = rest.patchConfig(body);

    assertEquals(200, r.getStatus());
    InstanceRorConfigIO out = (InstanceRorConfigIO) r.getEntity();
    assertNull(out.rorId(), "explicit null must clear rorId");
    assertNull(out.rorUrl(), "rorUrl must be null when rorId is cleared");
  }

  // ─── Test #4: PATCH with invalid rorId returns 400 ──────────────────────

  @Test
  void patchConfig_withInvalidRorId_returns400() {
    InstanceRorConfig current = new InstanceRorConfig();
    when(service.current()).thenReturn(current);

    InstanceRorConfigPatchIO body = new InstanceRorConfigPatchIO();
    body.setRorId("https://ror.org/04cvxnb49"); // full URL — not valid

    Response r = rest.patchConfig(body);

    assertEquals(400, r.getStatus());
    assertEquals("application/problem+json", r.getMediaType().toString());
    ProblemJson problem = assertInstanceOf(ProblemJson.class, r.getEntity());
    assertEquals(InstanceRorConfigRest.PROBLEM_TYPE_INVALID_ROR_ID, problem.type());
    assertEquals(400, problem.status());
  }

  @Test
  void patchConfig_withTooLongRorId_returns400() {
    InstanceRorConfig current = new InstanceRorConfig();
    when(service.current()).thenReturn(current);

    InstanceRorConfigPatchIO body = new InstanceRorConfigPatchIO();
    body.setRorId("1234567890"); // 10 chars — too long

    Response r = rest.patchConfig(body);

    assertEquals(400, r.getStatus());
    ProblemJson problem = assertInstanceOf(ProblemJson.class, r.getEntity());
    assertEquals(InstanceRorConfigRest.PROBLEM_TYPE_INVALID_ROR_ID, problem.type());
  }

  @Test
  void patchConfig_nullBody_treatedAsEmptyPatch() {
    InstanceRorConfig current = new InstanceRorConfig();
    when(service.current()).thenReturn(current);
    when(service.patch(nullable(String.class), nullable(String.class))).thenReturn(current);

    Response r = rest.patchConfig(null);

    assertEquals(200, r.getStatus(), "null body is a legal RFC 7396 no-op patch");
  }
}
