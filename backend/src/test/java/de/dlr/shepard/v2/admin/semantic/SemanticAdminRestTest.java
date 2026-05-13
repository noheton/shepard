package de.dlr.shepard.v2.admin.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.semantic.OntologyRefreshService;
import de.dlr.shepard.context.semantic.OntologyRefreshService.BundleError;
import de.dlr.shepard.context.semantic.OntologyRefreshService.RefreshOutcome;
import de.dlr.shepard.v2.admin.semantic.io.RefreshOntologiesRequestIO;
import de.dlr.shepard.v2.admin.semantic.io.RefreshOntologiesResultIO;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SemanticAdminRestTest {

  private OntologyRefreshService refreshService;
  private AuthenticationContext authCtx;
  private SecurityContext securityContext;

  private SemanticAdminRest rest;

  @BeforeEach
  void setUp() {
    refreshService = mock(OntologyRefreshService.class);
    authCtx = mock(AuthenticationContext.class);
    securityContext = mock(SecurityContext.class);
    when(securityContext.getUserPrincipal()).thenReturn(() -> "admin");
    when(securityContext.isUserInRole(Constants.INSTANCE_ADMIN_ROLE)).thenReturn(true);

    rest = new SemanticAdminRest();
    rest.refreshService = refreshService;
    rest.authenticationContext = authCtx;
  }

  // ---------- @Annotation gates ---------------------------------------------

  @Test
  void classCarriesInstanceAdminGate() {
    RolesAllowed gate = SemanticAdminRest.class.getAnnotation(RolesAllowed.class);
    assertNotNull(gate, "SemanticAdminRest must be @RolesAllowed-gated at class level");
    assertEquals(1, gate.value().length);
    assertEquals(Constants.INSTANCE_ADMIN_ROLE, gate.value()[0]);
  }

  @Test
  void pathIsV2() {
    Path p = SemanticAdminRest.class.getAnnotation(Path.class);
    assertNotNull(p);
    assertEquals("/v2/admin/semantic", p.value(), "endpoint lives on the /v2/ shelf per fork policy");
  }

  // ---------- happy path -----------------------------------------------------

  @Test
  void refresh_emptyBody_defaultsToAllBundles_force_false() {
    RefreshOutcome happy = new RefreshOutcome();
    happy.requested = 9;
    happy.refreshed = 7;
    happy.alreadyCurrent = 2;
    happy.errors = new ArrayList<>();
    when(refreshService.refresh(any(), anyBoolean())).thenReturn(happy);

    var r = rest.refreshOntologies(null, securityContext);

    assertEquals(200, r.getStatus());
    RefreshOntologiesResultIO body = (RefreshOntologiesResultIO) r.getEntity();
    assertEquals(9, body.getRequested());
    assertEquals(7, body.getRefreshed());
    assertEquals(2, body.getAlreadyCurrent());
    assertTrue(body.getErrors().isEmpty());

    // Service was invoked with the defaults (empty bundles list, force=false).
    verify(refreshService).refresh(List.of(), false);
  }

  @Test
  void refresh_explicitBundles_andForce_passedThrough() {
    RefreshOutcome happy = new RefreshOutcome();
    happy.requested = 2;
    happy.refreshed = 2;
    happy.alreadyCurrent = 0;
    happy.errors = new ArrayList<>();
    when(refreshService.refresh(any(), anyBoolean())).thenReturn(happy);

    var req = new RefreshOntologiesRequestIO(List.of("prov-o", "qudt"), true);
    var r = rest.refreshOntologies(req, securityContext);

    assertEquals(200, r.getStatus());
    verify(refreshService).refresh(List.of("prov-o", "qudt"), true);
  }

  @Test
  void refresh_partialFailure_returns200_withErrorsArray() {
    RefreshOutcome mixed = new RefreshOutcome();
    mixed.requested = 3;
    mixed.refreshed = 1;
    mixed.alreadyCurrent = 1;
    mixed.errors = List.of(new BundleError("qudt", "Could not fetch http://qudt.org/2.1/vocab/unit.ttl: timeout"));
    when(refreshService.refresh(any(), anyBoolean())).thenReturn(mixed);

    var r = rest.refreshOntologies(new RefreshOntologiesRequestIO(List.of(), false), securityContext);

    assertEquals(200, r.getStatus(), "partial failure stays 200, per the IO shape");
    RefreshOntologiesResultIO body = (RefreshOntologiesResultIO) r.getEntity();
    assertEquals(3, body.getRequested());
    assertEquals(1, body.getRefreshed());
    assertEquals(1, body.getAlreadyCurrent());
    assertEquals(1, body.getErrors().size());
    assertEquals("qudt", body.getErrors().get(0).getBundle());
    assertTrue(body.getErrors().get(0).getReason().contains("timeout"));
  }

  @Test
  void refresh_unknownBundleId_surfacesInErrorsArray() {
    RefreshOutcome outcome = new RefreshOutcome();
    outcome.requested = 1;
    outcome.refreshed = 0;
    outcome.alreadyCurrent = 0;
    outcome.errors = List.of(new BundleError("not-a-bundle", "Unknown bundle id — not present in ontologies-manifest.json."));
    when(refreshService.refresh(any(), anyBoolean())).thenReturn(outcome);

    var r = rest.refreshOntologies(new RefreshOntologiesRequestIO(List.of("not-a-bundle"), false), securityContext);

    assertEquals(200, r.getStatus());
    RefreshOntologiesResultIO body = (RefreshOntologiesResultIO) r.getEntity();
    assertEquals(1, body.getErrors().size());
    assertEquals("not-a-bundle", body.getErrors().get(0).getBundle());
    assertTrue(body.getErrors().get(0).getReason().toLowerCase().contains("unknown"));
  }

  // ---------- auth paths -----------------------------------------------------

  @Test
  void refresh_noPrincipal_returns401Problem() {
    when(securityContext.getUserPrincipal()).thenReturn(null);

    var r = rest.refreshOntologies(null, securityContext);

    assertEquals(401, r.getStatus());
    assertEquals("application/problem+json", r.getMediaType().toString());
    ProblemJson body = (ProblemJson) r.getEntity();
    assertEquals(SemanticAdminRest.PROBLEM_TYPE_AUTH, body.type());
    assertEquals(401, body.status());
  }

  @Test
  void refresh_principalButNoRole_returns403Problem() {
    when(securityContext.isUserInRole(Constants.INSTANCE_ADMIN_ROLE)).thenReturn(false);

    var r = rest.refreshOntologies(null, securityContext);

    assertEquals(403, r.getStatus());
    assertEquals("application/problem+json", r.getMediaType().toString());
    ProblemJson body = (ProblemJson) r.getEntity();
    assertEquals(SemanticAdminRest.PROBLEM_TYPE_AUTH, body.type());
    assertEquals(403, body.status());
  }

  @Test
  void refresh_nullSecurityContext_returns401Problem() {
    var r = rest.refreshOntologies(null, null);

    assertEquals(401, r.getStatus());
  }

  // ---------- request-IO defaults --------------------------------------------

  @Test
  void requestIO_defaultConstructor_yieldsEmptyBundlesAndForceFalse() {
    var io = new RefreshOntologiesRequestIO();
    assertTrue(io.getBundles().isEmpty());
    assertEquals(false, io.isForce());
  }

  @Test
  void requestIO_nullBundles_areNormalisedToEmpty() {
    var io = new RefreshOntologiesRequestIO();
    io.setBundles(null);
    assertTrue(io.getBundles().isEmpty());
  }

  @Test
  void resultIO_nullErrors_areNormalisedToEmpty() {
    var io = new RefreshOntologiesResultIO();
    io.setErrors(null);
    assertTrue(io.getErrors().isEmpty());
  }

  @Test
  void resultIO_getters_setters_roundtrip() {
    var io = new RefreshOntologiesResultIO();
    io.setRequested(5);
    io.setRefreshed(3);
    io.setAlreadyCurrent(1);
    io.setErrors(List.of(new RefreshOntologiesResultIO.Error("x", "boom")));
    assertEquals(5, io.getRequested());
    assertEquals(3, io.getRefreshed());
    assertEquals(1, io.getAlreadyCurrent());
    assertEquals("x", io.getErrors().get(0).getBundle());
    assertEquals("boom", io.getErrors().get(0).getReason());
  }

  @Test
  void resultErrorIO_defaultConstructor_yieldsNullFields() {
    var e = new RefreshOntologiesResultIO.Error();
    assertNull(e.getBundle());
    assertNull(e.getReason());
    e.setBundle("p");
    e.setReason("r");
    assertEquals("p", e.getBundle());
    assertEquals("r", e.getReason());
  }
}
