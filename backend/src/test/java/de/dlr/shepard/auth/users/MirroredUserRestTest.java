package de.dlr.shepard.auth.users;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.users.daos.MirroredUserDAO;
import de.dlr.shepard.auth.users.entities.MirroredUser;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.v2.admin.users.MirroredUserRest;
import de.dlr.shepard.v2.admin.users.io.MirroredUserCreateIO;
import de.dlr.shepard.v2.admin.users.io.MirroredUserIO;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * PROV-USER-MIRROR-ENDPOINT — unit tests for {@link MirroredUserRest}.
 *
 * <p>No Quarkus boot required — {@link MirroredUserDAO} is mocked.
 * The 401/403 cases are validated via annotation-gate assertions (same
 * pattern as {@code AdminConfigRestTest}).
 */
class MirroredUserRestTest {

  private MirroredUserDAO dao;
  private MirroredUserRest rest;

  @BeforeEach
  void setUp() {
    dao = mock(MirroredUserDAO.class);
    rest = new MirroredUserRest();
    rest.mirroredUserDAO = dao;
  }

  // ─── annotation gates (401 / 403) ────────────────────────────────────────

  /**
   * "401 unauthenticated" / "403 non-admin" — asserted via the class-level
   * {@code @RolesAllowed} annotation, which is the JAX-RS mechanism that the
   * Quarkus security layer enforces at wire level. A full-stack 401/403 test
   * requires a running container; the annotation gate is the right unit-level
   * assertion for this pattern.
   */
  @Test
  void classCarriesInstanceAdminGate() {
    RolesAllowed gate = MirroredUserRest.class.getAnnotation(RolesAllowed.class);
    assertNotNull(gate, "MirroredUserRest must be @RolesAllowed-gated at class level");
    assertEquals(1, gate.value().length);
    assertEquals(Constants.INSTANCE_ADMIN_ROLE, gate.value()[0]);
  }

  @Test
  void pathIsV2AdminUsersMirror() {
    Path p = MirroredUserRest.class.getAnnotation(Path.class);
    assertNotNull(p);
    assertEquals("/v2/admin/users/mirror", p.value(), "endpoint lives on the /v2/ shelf per fork policy");
  }

  // ─── 201 successful create ────────────────────────────────────────────────

  @Test
  void post_allFields_createsNewNode_returns201() {
    MirroredUser saved = buildEntity("app-uuid-001", "https://cube3.dlr.de", "fkrebs",
      "Florian Krebs", "flo@dlr.de");

    when(dao.findBySourceInstanceAndUsername("https://cube3.dlr.de", "fkrebs"))
      .thenReturn(Optional.empty());
    when(dao.createOrUpdateBySourceKey(any())).thenReturn(saved);

    var body = new MirroredUserCreateIO(
      "https://cube3.dlr.de", "fkrebs", "Florian Krebs", "flo@dlr.de");

    Response r = rest.post(body);

    assertEquals(201, r.getStatus());
    MirroredUserIO io = (MirroredUserIO) r.getEntity();
    assertEquals("app-uuid-001", io.appId());
    assertEquals("https://cube3.dlr.de", io.sourceInstance());
    assertEquals("fkrebs", io.sourceUsername());
    assertEquals("Florian Krebs", io.sourceDisplayName());
    assertEquals("flo@dlr.de", io.sourceEmail());
  }

  @Test
  void post_requiredFieldsOnly_createsNewNode_returns201() {
    MirroredUser saved = buildEntity("app-uuid-002", "https://nuclide.local", "jdoe",
      null, null);

    when(dao.findBySourceInstanceAndUsername("https://nuclide.local", "jdoe"))
      .thenReturn(Optional.empty());
    when(dao.createOrUpdateBySourceKey(any())).thenReturn(saved);

    var body = new MirroredUserCreateIO("https://nuclide.local", "jdoe", null, null);

    Response r = rest.post(body);

    assertEquals(201, r.getStatus());
    MirroredUserIO io = (MirroredUserIO) r.getEntity();
    assertEquals("app-uuid-002", io.appId());
    assertEquals("https://nuclide.local", io.sourceInstance());
    assertEquals("jdoe", io.sourceUsername());
  }

  // ─── 200 idempotent re-create ─────────────────────────────────────────────

  @Test
  void post_existingPair_returns200_withSameAppId() {
    MirroredUser existing = buildEntity("app-uuid-001", "https://cube3.dlr.de", "fkrebs",
      "Florian K.", "flo2@dlr.de");

    when(dao.findBySourceInstanceAndUsername("https://cube3.dlr.de", "fkrebs"))
      .thenReturn(Optional.of(existing));
    when(dao.createOrUpdateBySourceKey(any())).thenReturn(existing);

    var body = new MirroredUserCreateIO(
      "https://cube3.dlr.de", "fkrebs", "Florian K. (updated)", "flo2@dlr.de");

    Response r = rest.post(body);

    assertEquals(200, r.getStatus(), "idempotent call must return 200, not 201");
    MirroredUserIO io = (MirroredUserIO) r.getEntity();
    assertEquals("app-uuid-001", io.appId(), "same appId must be returned on re-create");
  }

  // ─── 400 validation ──────────────────────────────────────────────────────

  @Test
  void post_nullBody_returns400() {
    Response r = rest.post(null);

    assertEquals(400, r.getStatus());
    verify(dao, never()).createOrUpdateBySourceKey(any());
  }

  @Test
  void post_missingSourceInstance_returns400() {
    var body = new MirroredUserCreateIO(null, "fkrebs", null, null);

    Response r = rest.post(body);

    assertEquals(400, r.getStatus());
    ProblemJson err = (ProblemJson) r.getEntity();
    assertNotNull(err.detail());
    verify(dao, never()).createOrUpdateBySourceKey(any());
  }

  @Test
  void post_blankSourceInstance_returns400() {
    var body = new MirroredUserCreateIO("   ", "fkrebs", null, null);

    Response r = rest.post(body);

    assertEquals(400, r.getStatus());
    verify(dao, never()).createOrUpdateBySourceKey(any());
  }

  @Test
  void post_missingSourceUsername_returns400() {
    var body = new MirroredUserCreateIO("https://cube3.dlr.de", null, null, null);

    Response r = rest.post(body);

    assertEquals(400, r.getStatus());
    ProblemJson err = (ProblemJson) r.getEntity();
    assertNotNull(err.detail());
    verify(dao, never()).createOrUpdateBySourceKey(any());
  }

  @Test
  void post_blankSourceUsername_returns400() {
    var body = new MirroredUserCreateIO("https://cube3.dlr.de", "", null, null);

    Response r = rest.post(body);

    assertEquals(400, r.getStatus());
    verify(dao, never()).createOrUpdateBySourceKey(any());
  }

  // ─── helpers ─────────────────────────────────────────────────────────────

  private MirroredUser buildEntity(
      String appId,
      String sourceInstance,
      String sourceUsername,
      String displayName,
      String email) {
    MirroredUser u = new MirroredUser();
    u.setAppId(appId);
    u.setSourceInstance(sourceInstance);
    u.setSourceUsername(sourceUsername);
    u.setSourceDisplayName(displayName);
    u.setSourceEmail(email);
    return u;
  }
}
