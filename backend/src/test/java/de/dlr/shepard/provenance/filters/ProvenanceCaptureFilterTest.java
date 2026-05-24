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

  @Mock
  EntityAppIdLookup entityAppIdLookup;

  ProvenanceCaptureFilter filter;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    filter = new ProvenanceCaptureFilter();
    filter.provenance = provenance;
    filter.captureReads = false;
    // Wire the real resolver bean against the mocked lookup so the path-walk
    // tests run end-to-end through the parser + resolver layers.
    TargetEntityResolver resolver = new TargetEntityResolver();
    resolver.lookup = entityAppIdLookup;
    filter.targetEntityResolver = resolver;

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
  void targetExtractionForV2EntityPath() throws IOException {
    String uuid = "018f9c5a-7e26-7000-a000-000000000123";
    when(request.getMethod()).thenReturn("PATCH");
    when(response.getStatus()).thenReturn(200);
    when(uriInfo.getPath()).thenReturn("v2/collections/" + uuid);

    filter.filter(request, response);

    verify(provenance).record(
      eq("UPDATE"),
      eq("Collection"),
      eq(uuid),
      eq("alice"),
      eq("PATCH /v2/collections/" + uuid),
      eq("PATCH"),
      eq("v2/collections/" + uuid),
      eq(200),
      anyLong(),
      anyLong()
    );
  }

  @Test
  void targetExtractionForV1NumericPath_resolvesAppId() throws IOException {
    // RDM-2026-05-24-004 bucket C — v1 numeric ids must resolve to appIds
    // via EntityAppIdLookup so the per-entity drill-down finds the row.
    String collAppId = "018f9c5a-7e26-7000-a000-0000000000c1";
    when(entityAppIdLookup.findAppIdByNumericId("Collection", 42L)).thenReturn(java.util.Optional.of(collAppId));
    when(request.getMethod()).thenReturn("PATCH");
    when(response.getStatus()).thenReturn(200);
    when(uriInfo.getPath()).thenReturn("shepard/api/collections/42");

    filter.filter(request, response);

    verify(provenance).record(
      eq("UPDATE"),
      eq("Collection"),
      eq(collAppId),
      eq("alice"),
      eq("PATCH /shepard/api/collections/42"),
      eq("PATCH"),
      eq("shepard/api/collections/42"),
      eq(200),
      anyLong(),
      anyLong()
    );
  }

  @Test
  void targetExtractionForV1DeepNumericPath_landsOnLeafDataObject() throws IOException {
    // POST /shepard/api/collections/42/dataObjects/45/timeseriesReferences →
    // attribute to DataObject 45 (leaf), not Collection 42.
    String doAppId = "018f9c5a-7e26-7000-a000-0000000000d1";
    when(entityAppIdLookup.findAppIdByNumericId("DataObject", 45L)).thenReturn(java.util.Optional.of(doAppId));
    when(request.getMethod()).thenReturn("POST");
    when(response.getStatus()).thenReturn(201);
    when(uriInfo.getPath()).thenReturn("shepard/api/collections/42/dataObjects/45/timeseriesReferences");

    filter.filter(request, response);

    verify(provenance).record(
      eq("CREATE"),
      eq("DataObject"),
      eq(doAppId),
      eq("alice"),
      eq("POST /shepard/api/collections/42/dataObjects/45/timeseriesReferences"),
      eq("POST"),
      eq("shepard/api/collections/42/dataObjects/45/timeseriesReferences"),
      eq(201),
      anyLong(),
      anyLong()
    );
  }

  @Test
  void targetExtractionForV2NestedSubresource_landsOnLeafDataObject() throws IOException {
    // RDM-2026-05-24-004 bucket B — POST /v2/collections/<C>/data-objects/<D>
    // must attribute to the DataObject, not the Collection.
    String collUuid = "018f9c5a-7e26-7000-a000-000000000010";
    String doUuid = "018f9c5a-7e26-7000-a000-000000000020";
    when(request.getMethod()).thenReturn("POST");
    when(response.getStatus()).thenReturn(201);
    when(uriInfo.getPath()).thenReturn("v2/collections/" + collUuid + "/data-objects/" + doUuid);

    filter.filter(request, response);

    verify(provenance).record(
      eq("CREATE"),
      eq("DataObject"),
      eq(doUuid),
      eq("alice"),
      eq("POST /v2/collections/" + collUuid + "/data-objects/" + doUuid),
      eq("POST"),
      eq("v2/collections/" + collUuid + "/data-objects/" + doUuid),
      eq(201),
      anyLong(),
      anyLong()
    );
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
