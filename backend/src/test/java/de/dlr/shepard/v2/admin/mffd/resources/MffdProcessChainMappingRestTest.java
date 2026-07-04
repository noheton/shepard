package de.dlr.shepard.v2.admin.mffd.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.provenance.filters.ProvenanceCaptureFilter;
import de.dlr.shepard.provenance.services.ProvenanceService;
import de.dlr.shepard.v2.admin.mffd.io.ProcessChainMappingResultIO;
import de.dlr.shepard.v2.admin.mffd.services.MffdProcessChainMappingService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Wire tests for {@link MffdProcessChainMappingRest}. The service is
 * mocked — we pin the gate (instance-admin), the success body shape,
 * the 400 path, and the skip-capture handoff.
 */
class MffdProcessChainMappingRestTest {

  @Mock
  MffdProcessChainMappingService service;

  @Mock
  ProvenanceService provenanceService;

  @Mock
  ContainerRequestContext requestContext;

  @Mock
  SecurityContext securityContext;

  MffdProcessChainMappingRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new MffdProcessChainMappingRest();
    resource.service = service;
    resource.provenanceService = provenanceService;
    resource.requestContext = requestContext;

    Principal principal = () -> "alice";
    when(securityContext.getUserPrincipal()).thenReturn(principal);
  }

  @Test
  void classCarriesInstanceAdminGate() {
    RolesAllowed gate = MffdProcessChainMappingRest.class.getAnnotation(RolesAllowed.class);
    assertNotNull(gate, "MffdProcessChainMappingRest must be @RolesAllowed-gated at class level");
    assertEquals(1, gate.value().length);
    assertEquals(Constants.INSTANCE_ADMIN_ROLE, gate.value()[0]);
  }

  @Test
  void apply_invalidPayloadProducesProblemJsonWith400() {
    when(service.apply(anyString()))
      .thenThrow(new MffdProcessChainMappingService.InvalidMappingPayloadException("malformed"));

    Response r = resource.apply("not really yaml: }{[", securityContext);

    assertEquals(400, r.getStatus());
    assertEquals("application/problem+json", r.getMediaType().toString());
    assertTrue(r.getEntity() instanceof ProblemJson, "Error body must be ProblemJson");
    ProblemJson body = (ProblemJson) r.getEntity();
    assertEquals(400, body.status());
    assertTrue(body.detail().contains("malformed"),
      "Problem detail must carry the service's error message: " + body.detail());
  }

  @Test
  void apply_successReturnsResultIoAndRecordsActivityWithSkipCapture() {
    ProcessChainMappingResultIO mock = new ProcessChainMappingResultIO();
    mock.setSchemaVersion(1);
    mock.setEntries(3);
    mock.setMatched(5);
    mock.setEdgesCreated(7);
    when(service.apply(anyString())).thenReturn(mock);

    Response r = resource.apply("schemaVersion: 1\nmappings: []\n", securityContext);

    assertEquals(200, r.getStatus());
    assertEquals(mock, r.getEntity());

    // Provenance must have been recorded (EXECUTE / MffdProcessChainMapping).
    verify(provenanceService).record(
      anyString(),          // actionKind
      anyString(),          // targetKind
      any(),                // targetAppId (null)
      anyString(),          // agentUsername
      anyString(),          // summary
      anyString(),          // method
      anyString(),          // path
      any(),                // status
      anyLong(),            // startedAtMillis
      anyLong()             // endedAtMillis
    );

    // Skip-capture handoff must have been signalled to the response filter.
    verify(requestContext).setProperty(ProvenanceCaptureFilter.PROP_SKIP_CAPTURE, Boolean.TRUE);
  }

  @Test
  void apply_anonymousCallerStillRecordsActivityWithSystemAgent() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    when(service.apply(anyString())).thenReturn(new ProcessChainMappingResultIO());

    Response r = resource.apply("schemaVersion: 1\nmappings: []\n", securityContext);

    assertEquals(200, r.getStatus());
    // Activity should still be recorded — the @RolesAllowed gate would
    // have stopped a real anonymous caller upstream, but the handler
    // is robust to a null principal landing here.
    verify(provenanceService).record(
      anyString(), anyString(), any(), anyString(),
      anyString(), anyString(), anyString(), any(),
      anyLong(), anyLong()
    );
    verify(requestContext).setProperty(ProvenanceCaptureFilter.PROP_SKIP_CAPTURE, Boolean.TRUE);
  }
}
