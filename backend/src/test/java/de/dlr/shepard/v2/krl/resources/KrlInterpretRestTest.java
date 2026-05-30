package de.dlr.shepard.v2.krl.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.v2.krl.io.KrlInterpretRequestIO;
import de.dlr.shepard.v2.krl.io.KrlInterpretResponseIO;
import de.dlr.shepard.v2.krl.services.KrlInterpretService;
import de.dlr.shepard.v2.krl.services.KrlSidecarClient;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * KRL-INTERPRETER-05 — unit tests for {@link KrlInterpretRest}.
 *
 * <p>Mock-based, no Quarkus boot. Covers the §7.3 error mapping table
 * end-to-end at the resource layer: 400 / 502 / 504 / 501 + happy 201.
 */
class KrlInterpretRestTest {

  private KrlInterpretRest resource;
  private KrlInterpretService service;
  private ContainerRequestContext requestContext;
  private SecurityContext securityContext;

  @BeforeEach
  void setUp() {
    service = mock(KrlInterpretService.class);
    requestContext = mock(ContainerRequestContext.class);
    securityContext = mock(SecurityContext.class);
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("alice");
    when(securityContext.getUserPrincipal()).thenReturn(principal);
    resource = new KrlInterpretRest();
    resource.service = service;
    resource.requestContext = requestContext;
  }

  @Test
  void happyPath_returns201_andHandsOffSkipCapture() {
    KrlInterpretResponseIO body = KrlInterpretResponseIO.builder()
      .trajectoryAppId("traj-1")
      .activityAppId("act-1")
      .interpreterVersion("0.1.0")
      .build();
    when(service.interpret(any(), eqAlice(), any())).thenReturn(body);

    Response r = resource.interpret(new KrlInterpretRequestIO(), null, securityContext);

    assertEquals(201, r.getStatus());
    assertNotNull(r.getEntity());
    verify(requestContext).setProperty(
      eq("shepard.provenance.skip-capture"), eq(Boolean.TRUE));
  }

  @Test
  void aiAgentHeader_propagatesToService() {
    KrlInterpretResponseIO body = KrlInterpretResponseIO.builder().trajectoryAppId("t").build();
    when(service.interpret(any(), eqAlice(), eq("claude-opus-4-7"))).thenReturn(body);

    Response r = resource.interpret(new KrlInterpretRequestIO(), "claude-opus-4-7", securityContext);

    assertEquals(201, r.getStatus());
    verify(service).interpret(any(), eq("alice"), eq("claude-opus-4-7"));
  }

  @Test
  void badRequest_mappedTo400() {
    when(service.interpret(any(), anyString(), any()))
      .thenThrow(new BadRequestException("srcFileAppId is required"));
    Response r = resource.interpret(new KrlInterpretRequestIO(), null, securityContext);
    assertEquals(400, r.getStatus());
    assertTrue(r.getEntity().toString().contains("srcFileAppId"));
  }

  @Test
  void sidecarUnreachable_mappedTo502() {
    when(service.interpret(any(), anyString(), any()))
      .thenThrow(new KrlInterpretService.SidecarException(
        KrlSidecarClient.SidecarOutcome.unreachable("connection refused")));
    Response r = resource.interpret(new KrlInterpretRequestIO(), null, securityContext);
    assertEquals(502, r.getStatus());
    assertTrue(r.getEntity().toString().contains("connection refused"));
  }

  @Test
  void sidecarTimeout_mappedTo504() {
    when(service.interpret(any(), anyString(), any()))
      .thenThrow(new KrlInterpretService.SidecarException(
        KrlSidecarClient.SidecarOutcome.timeout("deadline 120s")));
    Response r = resource.interpret(new KrlInterpretRequestIO(), null, securityContext);
    assertEquals(504, r.getStatus());
  }

  @Test
  void sidecar501HardStop_mappedTo501() {
    when(service.interpret(any(), anyString(), any()))
      .thenThrow(new KrlInterpretService.SidecarException(
        KrlSidecarClient.SidecarOutcome.sidecarError(501, "SPS not supported offline")));
    Response r = resource.interpret(new KrlInterpretRequestIO(), null, securityContext);
    assertEquals(501, r.getStatus());
  }

  @Test
  void sidecar422IkDivergence_mappedTo422() {
    when(service.interpret(any(), anyString(), any()))
      .thenThrow(new KrlInterpretService.SidecarException(
        KrlSidecarClient.SidecarOutcome.sidecarError(422, "IK failed on >5% of poses")));
    Response r = resource.interpret(new KrlInterpretRequestIO(), null, securityContext);
    assertEquals(422, r.getStatus());
  }

  @Test
  void sidecar500Generic_mappedTo502() {
    when(service.interpret(any(), anyString(), any()))
      .thenThrow(new KrlInterpretService.SidecarException(
        KrlSidecarClient.SidecarOutcome.sidecarError(503, "service unavailable")));
    Response r = resource.interpret(new KrlInterpretRequestIO(), null, securityContext);
    assertEquals(502, r.getStatus());
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  private static String eq(String s) { return org.mockito.ArgumentMatchers.eq(s); }
  private static Object eq(Object o) { return org.mockito.ArgumentMatchers.eq(o); }
  private static String eqAlice() { return org.mockito.ArgumentMatchers.eq("alice"); }
}
