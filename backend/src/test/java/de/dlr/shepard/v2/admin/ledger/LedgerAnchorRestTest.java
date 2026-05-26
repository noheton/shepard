package de.dlr.shepard.v2.admin.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.common.exceptions.ApiError;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.v2.admin.ledger.io.LedgerAnchorJobIO;
import de.dlr.shepard.v2.admin.ledger.io.LedgerAnchorRequestIO;
import io.quarkus.arc.lookup.LookupIfProperty;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.lang.annotation.Annotation;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * TPL17 Phase 1 — unit tests for {@link LedgerAnchorRest} REST skeleton.
 *
 * <p>Covers:
 * <ul>
 *   <li>All three endpoints return 501 Not Implemented (skeleton phase).</li>
 *   <li>JAX-RS annotation wiring (path, roles, produces/consumes).</li>
 *   <li>Feature-toggle CDI gate annotation present on the class.</li>
 *   <li>IO shape: {@link LedgerAnchorRequestIO} validation constraints.</li>
 *   <li>{@link LedgerAnchorJobIO} round-trip construction.</li>
 * </ul>
 *
 * <p>No Quarkus harness required — the resource has no injected dependencies
 * in Phase 1, so direct instantiation suffices.
 */
class LedgerAnchorRestTest {

  LedgerAnchorRest resource;

  @BeforeEach
  void setUp() {
    resource = new LedgerAnchorRest();
  }

  // ─── JAX-RS annotation wiring ────────────────────────────────────────────

  @Test
  void classHasCorrectPath() {
    Path path = LedgerAnchorRest.class.getAnnotation(Path.class);
    assertNotNull(path, "@Path must be present on LedgerAnchorRest");
    assertEquals("/v2/admin/ledger", path.value());
  }

  @Test
  void classRequiresInstanceAdminRole() {
    RolesAllowed roles = LedgerAnchorRest.class.getAnnotation(RolesAllowed.class);
    assertNotNull(roles, "@RolesAllowed must be present on LedgerAnchorRest");
    assertEquals(1, roles.value().length);
    assertEquals(Constants.INSTANCE_ADMIN_ROLE, roles.value()[0]);
  }

  @Test
  void classProducesAndConsumesJson() {
    assertNotNull(LedgerAnchorRest.class.getAnnotation(Produces.class));
    assertNotNull(LedgerAnchorRest.class.getAnnotation(Consumes.class));
    assertEquals(
      MediaType.APPLICATION_JSON,
      LedgerAnchorRest.class.getAnnotation(Produces.class).value()[0]
    );
    assertEquals(
      MediaType.APPLICATION_JSON,
      LedgerAnchorRest.class.getAnnotation(Consumes.class).value()[0]
    );
  }

  @Test
  void classHasFeatureToggleGate() {
    LookupIfProperty gate = LedgerAnchorRest.class.getAnnotation(LookupIfProperty.class);
    assertNotNull(gate, "@LookupIfProperty gate must be present on LedgerAnchorRest");
    assertEquals("shepard.ledger.enabled", gate.name());
    assertEquals("true", gate.stringValue());
    // lookupIfMissing = false → bean absent when property is unset (default off)
  }

  // ─── POST /anchor — 501 skeleton ─────────────────────────────────────────

  @Test
  void anchorReturns501() {
    LedgerAnchorRequestIO body = new LedgerAnchorRequestIO();
    body.setActivityAppIds(List.of("019efa00-0000-7000-8000-000000000001"));
    body.setProvider("bloxberg");

    Response response = resource.anchor(body);

    assertEquals(Status.NOT_IMPLEMENTED.getStatusCode(), response.getStatus());
    assertNotNull(response.getEntity(), "501 response must include an error body");
    assertApiError(response, Status.NOT_IMPLEMENTED);
  }

  @Test
  void anchorResponseBodyMentionsTPL17() {
    LedgerAnchorRequestIO body = new LedgerAnchorRequestIO();
    body.setActivityAppIds(List.of("019efa00-0000-7000-8000-000000000001"));

    Response response = resource.anchor(body);

    ApiError error = (ApiError) response.getEntity();
    assertTrue(
      error.getMessage().contains("TPL17") || error.getMessage().contains("111"),
      "501 message should reference TPL17 or doc 111 so callers know where to look"
    );
  }

  // ─── GET /anchor/{jobId} — 501 skeleton ──────────────────────────────────

  @Test
  void getJobReturns501() {
    Response response = resource.getJob("019efa00-0000-7000-8000-000000000001");

    assertEquals(Status.NOT_IMPLEMENTED.getStatusCode(), response.getStatus());
    assertApiError(response, Status.NOT_IMPLEMENTED);
  }

  // ─── GET /data-objects/{appId}/ledger-anchors — 501 skeleton ─────────────

  @Test
  void getAnchorsForDataObjectReturns501() {
    Response response = resource.getAnchorsForDataObject("019efa00-0000-7000-8000-000000000002");

    assertEquals(Status.NOT_IMPLEMENTED.getStatusCode(), response.getStatus());
    assertApiError(response, Status.NOT_IMPLEMENTED);
  }

  // ─── LedgerAnchorRequestIO shape ─────────────────────────────────────────

  @Test
  void requestIoDefaultProviderIsNull() {
    LedgerAnchorRequestIO io = new LedgerAnchorRequestIO();
    io.setActivityAppIds(List.of("abc"));
    // provider omitted → null, service uses instance default
    assertTrue(io.getProvider() == null || io.getProvider().isEmpty());
  }

  @Test
  void requestIoAcceptsBloxbergAndOts() {
    LedgerAnchorRequestIO io = new LedgerAnchorRequestIO();
    io.setActivityAppIds(List.of("abc"));
    io.setProvider("bloxberg");
    assertEquals("bloxberg", io.getProvider());

    io.setProvider("opentimestamps");
    assertEquals("opentimestamps", io.getProvider());
  }

  // ─── LedgerAnchorJobIO round-trip ────────────────────────────────────────

  @Test
  void jobIoRoundTrip() {
    LedgerAnchorJobIO job = new LedgerAnchorJobIO(
      "019efa00-0000-7000-8000-000000000003",
      "queued",
      "Anchor job queued for 1 activity via bloxberg."
    );
    assertEquals("019efa00-0000-7000-8000-000000000003", job.getJobId());
    assertEquals("queued", job.getStatus());
    assertTrue(job.getMessage().contains("bloxberg"));
  }

  // ─── helpers ─────────────────────────────────────────────────────────────

  private void assertApiError(Response response, Status expectedStatus) {
    assertTrue(
      response.getEntity() instanceof ApiError,
      "Response entity must be ApiError for " + expectedStatus
    );
    ApiError err = (ApiError) response.getEntity();
    assertEquals(expectedStatus.getStatusCode(), err.getStatus());
    assertNotNull(err.getException());
    assertNotNull(err.getMessage());
  }
}
