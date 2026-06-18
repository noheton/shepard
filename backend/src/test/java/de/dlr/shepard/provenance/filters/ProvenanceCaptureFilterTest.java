package de.dlr.shepard.provenance.filters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.users.daos.MirroredUserDAO;
import de.dlr.shepard.auth.users.daos.UserDAO;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.MirroredUserEnrichmentCache;
import de.dlr.shepard.provenance.services.ProvenanceService;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import java.io.IOException;
import java.security.Principal;
import java.util.Optional;
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

  @Mock
  MirroredUserDAO mirroredUserDAO;

  @Mock
  UserDAO userDAO;

  @Mock
  MirroredUserEnrichmentCache enrichmentCache;

  ProvenanceCaptureFilter filter;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    filter = new ProvenanceCaptureFilter();
    filter.provenance = provenance;
    filter.captureReads = false;
    filter.mirroredUserDAO = mirroredUserDAO;
    filter.userDAO = userDAO;
    filter.enrichmentCache = enrichmentCache;
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
    // Default: no X-Source-User-* headers present → mirroredUserAppId is null.
    when(request.getHeaderString(ProvenanceCaptureFilter.HDR_SOURCE_USERNAME)).thenReturn(null);
    when(enrichmentCache.get(any(), any())).thenReturn(Optional.empty());
    // PROV1j: default — no X-AI-Agent header stashed
    when(request.getProperty(ProvenanceCaptureFilter.PROP_AI_AGENT)).thenReturn(null);
    // PROV1j: response.getHeaders() must return a real map so the filter can inject headers.
    when(response.getHeaders()).thenReturn(new MultivaluedHashMap<>());
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
      anyLong(),
      isNull(),    // no X-Source-User-* headers → mirroredUserAppId is null
      eq("human"), // PROV1j: no X-AI-Agent → human
      isNull()     // PROV1j: agentId null
    );
  }

  @Test
  void getDoesNotCapture_whenCaptureReadsOff() throws IOException {
    when(request.getMethod()).thenReturn("GET");
    when(response.getStatus()).thenReturn(200);

    filter.filter(request, response);

    verify(provenance, never()).record(any(), any(), any(), any(), any(), any(), any(), anyInt(), anyLong(), anyLong(), any(), any(), any());
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
      anyLong(),
      isNull(),
      eq("human"),
      isNull()
    );
  }

  /**
   * PROV-CAPTURE-READS-FLIP: even when {@code captureReads=true} (the new default),
   * reads on v1 {@code /shepard/api/...} paths must NOT produce an :Activity row.
   * The operator decision is "v2 paths only" for read capture.
   */
  @Test
  void getDoesNotCapture_onV1Path_whenCaptureReadsOn() throws IOException {
    filter.captureReads = true;
    when(request.getMethod()).thenReturn("GET");
    when(response.getStatus()).thenReturn(200);
    when(uriInfo.getPath()).thenReturn("shepard/api/collections/42");

    filter.filter(request, response);

    verify(provenance, never()).record(any(), any(), any(), any(), any(), any(), any(), anyInt(), anyLong(), anyLong(), any(), any(), any());
  }

  @Test
  void failedRequestDoesNotCapture() throws IOException {
    when(request.getMethod()).thenReturn("POST");
    when(response.getStatus()).thenReturn(403);

    filter.filter(request, response);

    verify(provenance, never()).record(any(), any(), any(), any(), any(), any(), any(), anyInt(), anyLong(), anyLong(), any(), any(), any());
  }

  @Test
  void unauthenticatedRequestDoesNotCapture() throws IOException {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    when(request.getMethod()).thenReturn("POST");
    when(response.getStatus()).thenReturn(201);

    filter.filter(request, response);

    verify(provenance, never()).record(any(), any(), any(), any(), any(), any(), any(), anyInt(), anyLong(), anyLong(), any(), any(), any());
  }

  @Test
  void disabledFilterIsNoOp() throws IOException {
    when(provenance.isEnabled()).thenReturn(false);
    when(request.getMethod()).thenReturn("POST");
    when(response.getStatus()).thenReturn(201);

    filter.filter(request, response);

    verify(provenance, never()).record(any(), any(), any(), any(), any(), any(), any(), anyInt(), anyLong(), anyLong(), any(), any(), any());
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
      anyLong(),
      isNull(),
      eq("human"),
      isNull()
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
      anyLong(),
      isNull(),
      eq("human"),
      isNull()
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
      anyLong(),
      isNull(),
      eq("human"),
      isNull()
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
      anyLong(),
      isNull(),
      eq("human"),
      isNull()
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

  // ── PROV1l: anonymizeInProvenance ─────────────────────────────────────────

  /**
   * PROV1l test 1: default behaviour — when {@code anonymizeInProvenance=false}
   * (or the :User node is absent), the {@code agentUsername} is included in the
   * captured :Activity row.
   */
  @Test
  void anonymizeOff_defaultBehaviour_identityIncluded() throws IOException {
    User alice = new User("alice");
    alice.setAnonymizeInProvenance(false);
    when(userDAO.find("alice")).thenReturn(alice);

    when(request.getMethod()).thenReturn("POST");
    when(response.getStatus()).thenReturn(201);

    filter.filter(request, response);

    verify(provenance).record(
      eq("CREATE"),
      eq(null),
      eq(null),
      eq("alice"),           // identity IS included
      eq("POST /v2/collections"),
      eq("POST"),
      eq("v2/collections"),
      eq(201),
      anyLong(),
      anyLong(),
      isNull(),
      eq("human"),
      isNull()
    );
  }

  /**
   * PROV1l test 2: when {@code anonymizeInProvenance=true}, {@code agentUsername}
   * is null on the captured :Activity row (identity suppressed).
   */
  @Test
  void anonymizeOn_identitySuppressedInActivityRow() throws IOException {
    User alice = new User("alice");
    alice.setAnonymizeInProvenance(true);
    when(userDAO.find("alice")).thenReturn(alice);

    when(request.getMethod()).thenReturn("POST");
    when(response.getStatus()).thenReturn(201);

    filter.filter(request, response);

    verify(provenance).record(
      eq("CREATE"),
      eq(null),
      eq(null),
      isNull(),              // identity suppressed
      eq("POST /v2/collections"),
      eq("POST"),
      eq("v2/collections"),
      eq(201),
      anyLong(),
      anyLong(),
      isNull(),              // mirroredUserAppId also suppressed
      eq("human"),
      isNull()
    );
  }

  /**
   * PROV1l test 3: when {@code anonymizeInProvenance=true} AND the request carries
   * {@code X-Source-User-*} headers (cross-instance importer), both
   * {@code agentUsername} AND {@code mirroredUserAppId} are null on the activity —
   * no personal identifier leaks via either channel.
   */
  @Test
  void anonymizeOn_suppressesMirroredUserAppIdToo() throws IOException {
    User alice = new User("alice");
    alice.setAnonymizeInProvenance(true);
    when(userDAO.find("alice")).thenReturn(alice);

    // Simulate X-Source-User-* headers being present (importer scenario).
    when(request.getHeaderString(ProvenanceCaptureFilter.HDR_SOURCE_USERNAME)).thenReturn("ext_user");
    when(request.getHeaderString(ProvenanceCaptureFilter.HDR_SOURCE_INSTANCE)).thenReturn("https://other.instance");
    // Return a cached mirroredUserAppId from the enrichment cache.
    when(enrichmentCache.get("https://other.instance", "ext_user"))
      .thenReturn(java.util.Optional.of("mirrored-app-id-123"));

    when(request.getMethod()).thenReturn("PUT");
    when(response.getStatus()).thenReturn(200);

    filter.filter(request, response);

    verify(provenance).record(
      eq("UPDATE"),
      eq(null),
      eq(null),
      isNull(),              // agentUsername suppressed
      eq("PUT /v2/collections"),
      eq("PUT"),
      eq("v2/collections"),
      eq(200),
      anyLong(),
      anyLong(),
      isNull(),              // mirroredUserAppId also suppressed
      eq("human"),
      isNull()
    );
  }

  /**
   * PROV1l test 4: {@code resolveAgentUsername} returns the principal name when
   * the :User node cannot be found (first-login race / DB hiccup) — fail-open
   * to non-anonymized behaviour, never silently drop identity on DB error.
   */
  @Test
  void resolveAgentUsername_defaultsToNonAnonymized_onUserNotFound() {
    when(userDAO.find("alice")).thenReturn(null); // user not yet in DB

    String result = filter.resolveAgentUsername("alice");

    assertEquals("alice", result);
  }

  /**
   * PROV1l test 5: {@code resolveAgentUsername} returns the principal name
   * when a DB exception occurs — fail-safe, never blocks the request.
   */
  @Test
  void resolveAgentUsername_defaultsToNonAnonymized_onDAOException() {
    when(userDAO.find("alice")).thenThrow(new RuntimeException("Neo4j timeout"));

    // Must not throw; must return the principal name (fail-open).
    String result = filter.resolveAgentUsername("alice");

    assertEquals("alice", result);
  }

  /**
   * PROV1l test 6: {@code resolveAgentUsername} returns {@code null}
   * when {@code anonymizeInProvenance=true}.
   */
  @Test
  void resolveAgentUsername_returnsNull_whenAnonymizeEnabled() {
    User alice = new User("alice");
    alice.setAnonymizeInProvenance(true);
    when(userDAO.find("alice")).thenReturn(alice);

    String result = filter.resolveAgentUsername("alice");

    assertNull(result);
  }
}
