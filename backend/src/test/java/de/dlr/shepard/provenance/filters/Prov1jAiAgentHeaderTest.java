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
import de.dlr.shepard.auth.users.services.MirroredUserEnrichmentCache;
import de.dlr.shepard.provenance.services.ProvenanceService;
import de.dlr.shepard.v2.admin.provenance.services.ProvenanceConfigService;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import java.io.IOException;
import java.security.Principal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * PROV1j unit tests — X-AI-Agent header classification + response header injection.
 *
 * <p>Covers the six required tests:
 * <ol>
 *   <li>X-AI-Agent absent → sourceMode="human", agentId=null on captured Activity</li>
 *   <li>X-AI-Agent: claude-sonnet-4-6 → sourceMode="ai", agentId="claude-sonnet-4-6"</li>
 *   <li>X-AI-Agent blank → treated as absent → sourceMode="human"</li>
 *   <li>Response header X-Provenance-Mode present on /v2/ response</li>
 *   <li>ActivityIO.from(activity) correctly serialises sourceMode + agentId (see {@link Prov1jActivityIOTest})</li>
 *   <li>Filter does not inject response headers on non-/v2/ paths</li>
 * </ol>
 */
class Prov1jAiAgentHeaderTest {

  @Mock ProvenanceService provenance;
  @Mock ContainerRequestContext request;
  @Mock ContainerResponseContext response;
  @Mock SecurityContext securityContext;
  @Mock UriInfo uriInfo;
  @Mock Principal principal;
  @Mock EntityAppIdLookup entityAppIdLookup;
  @Mock MirroredUserDAO mirroredUserDAO;
  @Mock UserDAO userDAO;
  @Mock MirroredUserEnrichmentCache enrichmentCache;
  @Mock ProvenanceConfigService provenanceConfigService;

  MultivaluedMap<String, Object> responseHeaders;

  ProvenanceCaptureFilter filter;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    filter = new ProvenanceCaptureFilter();
    filter.provenance = provenance;
    filter.provenanceConfigService = provenanceConfigService;
    filter.mirroredUserDAO = mirroredUserDAO;
    filter.userDAO = userDAO;
    filter.enrichmentCache = enrichmentCache;
    TargetEntityResolver resolver = new TargetEntityResolver();
    resolver.lookup = entityAppIdLookup;
    filter.targetEntityResolver = resolver;

    when(provenanceConfigService.effectiveEnabled()).thenReturn(true);
    when(provenanceConfigService.effectiveCaptureReads()).thenReturn(false);
    when(request.getSecurityContext()).thenReturn(securityContext);
    when(securityContext.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn("alice");
    when(request.getUriInfo()).thenReturn(uriInfo);
    when(uriInfo.getPath()).thenReturn("v2/collections");
    when(request.getProperty(ProvenanceCaptureFilter.PROP_STARTED_AT_MILLIS)).thenReturn(1_700_000_000_000L);
    when(request.getHeaderString(ProvenanceCaptureFilter.HDR_SOURCE_USERNAME)).thenReturn(null);
    when(enrichmentCache.get(any(), any())).thenReturn(Optional.empty());

    responseHeaders = new MultivaluedHashMap<>();
    when(response.getHeaders()).thenReturn(responseHeaders);
  }

  // ── Test 1: X-AI-Agent absent → sourceMode="human", agentId=null ─────────

  @Test
  void aiAgentAbsent_sourceModeHuman_agentIdNull() throws IOException {
    when(request.getMethod()).thenReturn("POST");
    when(response.getStatus()).thenReturn(201);
    // No X-AI-Agent header — stashed property is empty string
    when(request.getProperty(ProvenanceCaptureFilter.PROP_AI_AGENT)).thenReturn("");

    filter.filter(request, response);

    verify(provenance).record(
      eq("CREATE"), isNull(), isNull(), eq("alice"),
      eq("POST /v2/collections"), eq("POST"), eq("v2/collections"),
      eq(201), anyLong(), anyLong(),
      isNull(),          // mirroredUserAppId
      eq("human"),       // sourceMode
      isNull()           // agentId
    );
  }

  // ── Test 2: X-AI-Agent present → sourceMode="ai", agentId=<value> ────────

  @Test
  void aiAgentPresent_sourceModeAi_agentIdSet() throws IOException {
    when(request.getMethod()).thenReturn("POST");
    when(response.getStatus()).thenReturn(201);
    when(request.getProperty(ProvenanceCaptureFilter.PROP_AI_AGENT)).thenReturn("claude-sonnet-4-6");

    filter.filter(request, response);

    verify(provenance).record(
      eq("CREATE"), isNull(), isNull(), eq("alice"),
      eq("POST /v2/collections"), eq("POST"), eq("v2/collections"),
      eq(201), anyLong(), anyLong(),
      isNull(),                // mirroredUserAppId
      eq("ai"),                // sourceMode
      eq("claude-sonnet-4-6") // agentId
    );
  }

  // ── Test 3: X-AI-Agent blank → treated as absent → sourceMode="human" ────

  @Test
  void aiAgentBlank_treatedAsAbsent_sourceModeHuman() throws IOException {
    when(request.getMethod()).thenReturn("POST");
    when(response.getStatus()).thenReturn(201);
    when(request.getProperty(ProvenanceCaptureFilter.PROP_AI_AGENT)).thenReturn("   ");

    filter.filter(request, response);

    verify(provenance).record(
      eq("CREATE"), isNull(), isNull(), eq("alice"),
      eq("POST /v2/collections"), eq("POST"), eq("v2/collections"),
      eq(201), anyLong(), anyLong(),
      isNull(),    // mirroredUserAppId
      eq("human"), // sourceMode
      isNull()     // agentId
    );
  }

  // ── Test 4: X-Provenance-Mode header injected on /v2/ responses ──────────

  @Test
  void responseHeader_xProvenanceMode_presentOnV2Path() throws IOException {
    when(request.getMethod()).thenReturn("POST");
    when(response.getStatus()).thenReturn(201);
    when(request.getProperty(ProvenanceCaptureFilter.PROP_AI_AGENT)).thenReturn("gpt-4o");

    filter.filter(request, response);

    assertEquals("ai", responseHeaders.getFirst(ProvenanceCaptureFilter.HDR_PROV_MODE_RESPONSE));
    assertEquals("gpt-4o", responseHeaders.getFirst(ProvenanceCaptureFilter.HDR_AI_AGENT_CAPTURED));
  }

  @Test
  void responseHeader_xProvenanceMode_humanWhenNoAiAgent() throws IOException {
    when(request.getMethod()).thenReturn("POST");
    when(response.getStatus()).thenReturn(201);
    when(request.getProperty(ProvenanceCaptureFilter.PROP_AI_AGENT)).thenReturn(null);

    filter.filter(request, response);

    assertEquals("human", responseHeaders.getFirst(ProvenanceCaptureFilter.HDR_PROV_MODE_RESPONSE));
    assertNull(responseHeaders.getFirst(ProvenanceCaptureFilter.HDR_AI_AGENT_CAPTURED));
  }

  // ── Test 6: Filter does NOT inject response headers on non-/v2/ paths ────

  @Test
  void responseHeaders_notInjectedOnNonV2Paths() throws IOException {
    when(uriInfo.getPath()).thenReturn("shepard/api/collections");
    when(request.getMethod()).thenReturn("POST");
    when(response.getStatus()).thenReturn(201);
    when(request.getProperty(ProvenanceCaptureFilter.PROP_AI_AGENT)).thenReturn("claude-sonnet-4-6");

    filter.filter(request, response);

    // PROV1j response headers must NOT appear on v1 paths
    assertNull(responseHeaders.getFirst(ProvenanceCaptureFilter.HDR_PROV_MODE_RESPONSE));
    assertNull(responseHeaders.getFirst(ProvenanceCaptureFilter.HDR_AI_AGENT_CAPTURED));
    // But provenance capture still happens (v1 capture is not suppressed)
    verify(provenance).record(
      any(), any(), any(), any(), any(), any(), any(), anyInt(), anyLong(), anyLong(),
      any(), eq("ai"), eq("claude-sonnet-4-6")
    );
  }

  // ── resolveAiAgentHeader unit tests ──────────────────────────────────────

  @Test
  void resolveAiAgentHeader_nullProperty_fallsBackToHeader() {
    when(request.getProperty(ProvenanceCaptureFilter.PROP_AI_AGENT)).thenReturn(null);
    when(request.getHeaderString(ProvenanceCaptureFilter.HDR_AI_AGENT)).thenReturn("llama3");

    String result = filter.resolveAiAgentHeader(request);

    assertEquals("llama3", result);
  }

  @Test
  void resolveAiAgentHeader_blankHeader_returnsNull() {
    when(request.getProperty(ProvenanceCaptureFilter.PROP_AI_AGENT)).thenReturn("  ");

    String result = filter.resolveAiAgentHeader(request);

    assertNull(result);
  }

  @Test
  void resolveAiAgentHeader_presentHeader_returnsValue() {
    when(request.getProperty(ProvenanceCaptureFilter.PROP_AI_AGENT)).thenReturn("mistral-large");

    String result = filter.resolveAiAgentHeader(request);

    assertEquals("mistral-large", result);
  }

  // ── Request-phase property stashing ───────────────────────────────────────

  @Test
  void requestFilter_stashesAiAgentHeader() throws IOException {
    when(request.getHeaderString(ProvenanceCaptureFilter.HDR_AI_AGENT)).thenReturn("claude-opus-4-5");

    filter.filter(request);

    verify(request).setProperty(eq(ProvenanceCaptureFilter.PROP_AI_AGENT), eq("claude-opus-4-5"));
  }

  @Test
  void requestFilter_stashesEmptyStringWhenHeaderAbsent() throws IOException {
    when(request.getHeaderString(ProvenanceCaptureFilter.HDR_AI_AGENT)).thenReturn(null);

    filter.filter(request);

    verify(request).setProperty(eq(ProvenanceCaptureFilter.PROP_AI_AGENT), eq(""));
  }
}
