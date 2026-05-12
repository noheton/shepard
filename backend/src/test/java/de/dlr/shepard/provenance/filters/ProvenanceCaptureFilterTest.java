package de.dlr.shepard.provenance.filters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.provenance.services.ProvenanceService;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import java.io.IOException;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class ProvenanceCaptureFilterTest {

  @Mock
  ProvenanceService provenance;

  @Mock
  ContainerRequestContext request;

  @Mock
  ContainerResponseContext response;

  @Mock
  SecurityContext securityContext;

  @Mock
  UriInfo uriInfo;

  @Mock
  Principal principal;

  ProvenanceCaptureFilter filter;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    filter = new ProvenanceCaptureFilter();
    filter.provenance = provenance;
    filter.captureReads = false;

    when(provenance.isEnabled()).thenReturn(true);
    when(request.getSecurityContext()).thenReturn(securityContext);
    when(securityContext.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn("alice");
    when(request.getUriInfo()).thenReturn(uriInfo);
    when(uriInfo.getPath()).thenReturn("v2/collections");
    when(request.getProperty(ProvenanceCaptureFilter.PROP_STARTED_AT_MILLIS)).thenReturn(1_700_000_000_000L);
  }

  @Test
  void requestFilterStampsStartTime() throws IOException {
    filter.filter(request);
    verify(request).setProperty(eq(ProvenanceCaptureFilter.PROP_STARTED_AT_MILLIS), any(Long.class));
  }

  @Test
  void successful2xxMutationLandsActivityRow() throws IOException {
    when(request.getMethod()).thenReturn("POST");
    when(response.getStatus()).thenReturn(201);

    filter.filter(request, response);

    verify(provenance).record(
      eq("CREATE"),
      eq(null),
      eq(null),
      eq("alice"),
      eq("POST /v2/collections"),
      eq("POST"),
      eq("v2/collections"),
      eq(201),
      anyLong(),
      anyLong()
    );
  }

  @Test
  void getDoesNotCapture_whenCaptureReadsOff() throws IOException {
    when(request.getMethod()).thenReturn("GET");
    when(response.getStatus()).thenReturn(200);

    filter.filter(request, response);

    verify(provenance, never()).record(any(), any(), any(), any(), any(), any(), any(), anyInt(), anyLong(), anyLong());
  }

  @Test
  void getCaptures_whenCaptureReadsOn() throws IOException {
    filter.captureReads = true;
    when(request.getMethod()).thenReturn("GET");
    when(response.getStatus()).thenReturn(200);

    filter.filter(request, response);

    verify(provenance).record(
      eq("READ"),
      eq(null),
      eq(null),
      eq("alice"),
      eq("GET /v2/collections"),
      eq("GET"),
      eq("v2/collections"),
      eq(200),
      anyLong(),
      anyLong()
    );
  }

  @Test
  void failedRequestDoesNotCapture() throws IOException {
    when(request.getMethod()).thenReturn("POST");
    when(response.getStatus()).thenReturn(403);

    filter.filter(request, response);

    verify(provenance, never()).record(any(), any(), any(), any(), any(), any(), any(), anyInt(), anyLong(), anyLong());
  }

  @Test
  void unauthenticatedRequestDoesNotCapture() throws IOException {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    when(request.getMethod()).thenReturn("POST");
    when(response.getStatus()).thenReturn(201);

    filter.filter(request, response);

    verify(provenance, never()).record(any(), any(), any(), any(), any(), any(), any(), anyInt(), anyLong(), anyLong());
  }

  @Test
  void disabledFilterIsNoOp() throws IOException {
    when(provenance.isEnabled()).thenReturn(false);
    when(request.getMethod()).thenReturn("POST");
    when(response.getStatus()).thenReturn(201);

    filter.filter(request, response);

    verify(provenance, never()).record(any(), any(), any(), any(), any(), any(), any(), anyInt(), anyLong(), anyLong());
  }

  @Test
  void actionKindMappingCoversAllVerbs() {
    assertEquals("CREATE", ProvenanceCaptureFilter.actionKindFor("POST"));
    assertEquals("UPDATE", ProvenanceCaptureFilter.actionKindFor("PUT"));
    assertEquals("UPDATE", ProvenanceCaptureFilter.actionKindFor("PATCH"));
    assertEquals("DELETE", ProvenanceCaptureFilter.actionKindFor("DELETE"));
    assertEquals("READ", ProvenanceCaptureFilter.actionKindFor("GET"));
    assertEquals("READ", ProvenanceCaptureFilter.actionKindFor("HEAD"));
    assertEquals("EXECUTE", ProvenanceCaptureFilter.actionKindFor("CUSTOM"));
  }
}
