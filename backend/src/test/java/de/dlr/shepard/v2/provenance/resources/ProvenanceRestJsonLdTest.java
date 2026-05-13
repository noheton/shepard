package de.dlr.shepard.v2.provenance.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.output.OutputProfile;
import de.dlr.shepard.common.output.OutputProfileResolver;
import de.dlr.shepard.provenance.entities.Activity;
import de.dlr.shepard.provenance.services.ProvJsonLdRenderer;
import de.dlr.shepard.provenance.services.ProvJsonRenderer;
import de.dlr.shepard.provenance.services.ProvenanceService;
import de.dlr.shepard.v2.provenance.io.ActivityIO;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Resource-layer tests for PROV1h content-negotiation on
 * {@code /v2/provenance/*}. The renderer-level shape is covered in
 * {@link de.dlr.shepard.provenance.services.ProvJsonLdRendererTest};
 * this class asserts dispatch behaviour (Accept-header precedence,
 * 406 on unknown profile, plain-JSON unchanged regression).
 */
class ProvenanceRestJsonLdTest {

  static final String CALLER = "alice";

  @Mock
  ProvenanceService provenance;

  @Mock
  SecurityContext securityContext;

  @Mock
  Principal principal;

  ProvenanceRest resource;
  OutputProfileResolver outputProfile;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new ProvenanceRest();
    resource.provenance = provenance;
    resource.provJsonRenderer = new ProvJsonRenderer();
    resource.provJsonLdRenderer = new ProvJsonLdRenderer();
    outputProfile = new OutputProfileResolver();
    outputProfile.setProfile(OutputProfile.ALL);
    resource.outputProfile = outputProfile;

    when(securityContext.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
  }

  private Activity row(String appId, String actionKind, String targetKind, String targetAppId) {
    Activity a = new Activity();
    a.setAppId(appId);
    a.setActionKind(actionKind);
    a.setAgentUsername(CALLER);
    a.setTargetKind(targetKind);
    a.setTargetAppId(targetAppId);
    a.setStartedAtMillis(1_700_000_000_000L);
    a.setEndedAtMillis(1_700_000_000_500L);
    a.setSummary("test " + actionKind);
    a.setOriginInstance("local");
    return a;
  }

  // --- /activities ---------------------------------------------------------

  @Test
  void listActivitiesPlainJsonShapeUnchangedRegression() {
    when(provenance.list(eq(CALLER), any(), any(), any(), any(), anyInt()))
      .thenReturn(List.of(row("a-1", "CREATE", "Collection", "c-1")));

    Response r = resource.listActivities(null, null, null, null, null, null, securityContext);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    List<ActivityIO> rows = (List<ActivityIO>) r.getEntity();
    assertEquals(1, rows.size());
    assertEquals("a-1", rows.get(0).getAppId());
    // Plain-JSON path does NOT carry @context / @graph.
    assertFalse(rows.get(0) instanceof Map);
  }

  @Test
  void listActivitiesJsonLdReturnsProvOShape() {
    when(provenance.list(eq(CALLER), any(), any(), any(), any(), anyInt()))
      .thenReturn(List.of(row("a-1", "CREATE", "Collection", "c-1")));

    Response r = resource.listActivitiesJsonLd(
      null,
      null,
      null,
      null,
      null,
      null,
      "application/ld+json",
      securityContext
    );

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    Map<String, Object> body = (Map<String, Object>) r.getEntity();
    assertNotNull(body.get("@context"));
    @SuppressWarnings("unchecked")
    Map<String, String> ctx = (Map<String, String>) body.get("@context");
    assertEquals("http://www.w3.org/ns/prov#", ctx.get("prov"));
    assertFalse(ctx.containsKey("m4i"));

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> graph = (List<Map<String, Object>>) body.get("@graph");
    Map<String, Object> activity = graph
      .stream()
      .filter(n -> "shepard:activity/a-1".equals(n.get("@id")))
      .findFirst()
      .orElseThrow();
    @SuppressWarnings("unchecked")
    List<String> types = (List<String>) activity.get("@type");
    assertEquals(List.of("prov:Activity"), types);
    assertNotNull(activity.get("prov:startedAtTime"));

    // Content-Type echoes plain ld+json (no profile parameter).
    assertEquals("application/ld+json", r.getMediaType().toString());
  }

  @Test
  void listActivitiesJsonLdReturnsMetadata4ingShapeWithFullUri() {
    when(provenance.list(eq(CALLER), any(), any(), any(), any(), anyInt()))
      .thenReturn(List.of(row("a-1", "CREATE", "Collection", "c-1")));

    Response r = resource.listActivitiesJsonLd(
      null,
      null,
      null,
      null,
      null,
      null,
      "application/ld+json; profile=\"https://w3id.org/nfdi4ing/metadata4ing/\"",
      securityContext
    );

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    Map<String, Object> body = (Map<String, Object>) r.getEntity();
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> graph = (List<Map<String, Object>>) body.get("@graph");
    Map<String, Object> activity = graph
      .stream()
      .filter(n -> "shepard:activity/a-1".equals(n.get("@id")))
      .findFirst()
      .orElseThrow();
    @SuppressWarnings("unchecked")
    List<String> types = (List<String>) activity.get("@type");
    assertEquals(List.of("m4i:ProcessingStep", "prov:Activity"), types);

    // Content-Type echoes the profile parameter so caching proxies can
    // Vary cleanly.
    String mt = r.getMediaType().toString();
    assertTrue(mt.startsWith("application/ld+json"));
    assertTrue(mt.contains("profile="));
    assertTrue(mt.contains("metadata4ing"));
  }

  @Test
  void listActivitiesJsonLdAcceptsShortFormProfile() {
    when(provenance.list(eq(CALLER), any(), any(), any(), any(), anyInt()))
      .thenReturn(List.of(row("a-1", "CREATE", "Collection", "c-1")));

    Response r = resource.listActivitiesJsonLd(
      null,
      null,
      null,
      null,
      null,
      null,
      "application/ld+json; profile=metadata4ing",
      securityContext
    );

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    Map<String, Object> body = (Map<String, Object>) r.getEntity();
    @SuppressWarnings("unchecked")
    Map<String, String> ctx = (Map<String, String>) body.get("@context");
    assertTrue(ctx.containsKey("m4i"));
  }

  @Test
  void listActivitiesJsonLdReturns406OnUnknownProfile() {
    Response r = resource.listActivitiesJsonLd(
      null,
      null,
      null,
      null,
      null,
      null,
      "application/ld+json; profile=unknown",
      securityContext
    );

    assertEquals(406, r.getStatus());
    assertEquals(MediaType.APPLICATION_JSON_TYPE, r.getMediaType());
    ProblemJson body = (ProblemJson) r.getEntity();
    assertNotNull(body);
    assertEquals(406, body.status());
    assertTrue(body.type().contains("provenance.unsupported-profile"));
    assertTrue(body.detail().contains("unknown"));
  }

  @Test
  void listActivitiesJsonLdReturns401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);

    Response r = resource.listActivitiesJsonLd(
      null,
      null,
      null,
      null,
      null,
      null,
      "application/ld+json",
      securityContext
    );

    assertEquals(401, r.getStatus());
  }

  @Test
  void listActivitiesJsonLdReturns403WhenAskingForOtherUsersRowsAsCasual() {
    when(securityContext.isUserInRole("instance-admin")).thenReturn(false);
    Response r = resource.listActivitiesJsonLd(
      "bob",
      null,
      null,
      null,
      null,
      null,
      "application/ld+json",
      securityContext
    );
    assertEquals(403, r.getStatus());
  }

  @Test
  void listActivitiesJsonLdEmptyResultStillRendersContext() {
    when(provenance.list(eq(CALLER), any(), any(), any(), any(), anyInt())).thenReturn(List.of());

    Response r = resource.listActivitiesJsonLd(
      null,
      null,
      null,
      null,
      null,
      null,
      "application/ld+json",
      securityContext
    );

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    Map<String, Object> body = (Map<String, Object>) r.getEntity();
    assertNotNull(body.get("@context"));
    @SuppressWarnings("unchecked")
    List<Object> graph = (List<Object>) body.get("@graph");
    assertTrue(graph.isEmpty());
  }

  // --- /entity/{appId} -----------------------------------------------------

  @Test
  void listEntityActivitiesJsonLdReturnsProvOShape() {
    when(provenance.list(eq(CALLER), any(), eq("c-1"), any(), any(), anyInt()))
      .thenReturn(List.of(row("a-1", "UPDATE", "Collection", "c-1")));

    Response r = resource.listEntityActivitiesJsonLd(
      "c-1",
      null,
      null,
      null,
      "application/ld+json",
      securityContext
    );

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    Map<String, Object> body = (Map<String, Object>) r.getEntity();
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> graph = (List<Map<String, Object>>) body.get("@graph");
    Map<String, Object> activity = graph
      .stream()
      .filter(n -> "shepard:activity/a-1".equals(n.get("@id")))
      .findFirst()
      .orElseThrow();
    @SuppressWarnings("unchecked")
    List<String> types = (List<String>) activity.get("@type");
    assertEquals(List.of("prov:Activity"), types);
  }

  @Test
  void listEntityActivitiesJsonLdReturnsM4iShape() {
    when(provenance.list(eq(CALLER), any(), eq("c-1"), any(), any(), anyInt()))
      .thenReturn(List.of(row("a-1", "UPDATE", "Collection", "c-1")));

    Response r = resource.listEntityActivitiesJsonLd(
      "c-1",
      null,
      null,
      null,
      "application/ld+json; profile=metadata4ing",
      securityContext
    );

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    Map<String, Object> body = (Map<String, Object>) r.getEntity();
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> graph = (List<Map<String, Object>>) body.get("@graph");
    Map<String, Object> activity = graph
      .stream()
      .filter(n -> "shepard:activity/a-1".equals(n.get("@id")))
      .findFirst()
      .orElseThrow();
    @SuppressWarnings("unchecked")
    List<String> types = (List<String>) activity.get("@type");
    assertTrue(types.contains("m4i:ProcessingStep"));
  }

  @Test
  void listEntityActivitiesJsonLdReturns406OnUnknownProfile() {
    Response r = resource.listEntityActivitiesJsonLd(
      "c-1",
      null,
      null,
      null,
      "application/ld+json; profile=\"https://example.com/foo\"",
      securityContext
    );
    assertEquals(406, r.getStatus());
  }

  @Test
  void listEntityActivitiesJsonLdReturns401Unauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    Response r = resource.listEntityActivitiesJsonLd(
      "c-1",
      null,
      null,
      null,
      "application/ld+json",
      securityContext
    );
    assertEquals(401, r.getStatus());
  }

  // --- /count --------------------------------------------------------------

  @Test
  void countPlainJsonShapeUnchangedRegression() {
    when(provenance.count(eq(CALLER), any(), any(), any(), any())).thenReturn(42L);

    Response r = resource.countActivities(null, null, null, null, null, securityContext);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    Map<String, Object> body = (Map<String, Object>) r.getEntity();
    assertEquals(42L, body.get("count"));
    assertFalse(body.containsKey("@context"));
  }

  @Test
  void countJsonLdReturnsProvOWrapper() {
    when(provenance.count(eq(CALLER), any(), any(), any(), any())).thenReturn(42L);

    Response r = resource.countActivitiesJsonLd(
      null,
      null,
      null,
      null,
      null,
      "application/ld+json",
      securityContext
    );

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    Map<String, Object> body = (Map<String, Object>) r.getEntity();
    assertNotNull(body.get("@context"));
    @SuppressWarnings("unchecked")
    Map<String, Object> typed = (Map<String, Object>) body.get("shepard:numberOfActivities");
    assertEquals("xsd:nonNegativeInteger", typed.get("@type"));
    assertEquals("42", typed.get("@value"));
  }

  @Test
  void countJsonLdReturnsM4iWrapperOnShortProfile() {
    when(provenance.count(eq(CALLER), any(), any(), any(), any())).thenReturn(0L);

    Response r = resource.countActivitiesJsonLd(
      null,
      null,
      null,
      null,
      null,
      "application/ld+json; profile=metadata4ing",
      securityContext
    );

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    Map<String, Object> body = (Map<String, Object>) r.getEntity();
    @SuppressWarnings("unchecked")
    Map<String, String> ctx = (Map<String, String>) body.get("@context");
    assertTrue(ctx.containsKey("m4i"));
  }

  @Test
  void countJsonLdReturns406OnUnknownProfile() {
    Response r = resource.countActivitiesJsonLd(
      null,
      null,
      null,
      null,
      null,
      "application/ld+json; profile=garbage",
      securityContext
    );
    assertEquals(406, r.getStatus());
  }

  @Test
  void countJsonLdReturns401Unauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    Response r = resource.countActivitiesJsonLd(
      null,
      null,
      null,
      null,
      null,
      "application/ld+json",
      securityContext
    );
    assertEquals(401, r.getStatus());
  }

  @Test
  void countJsonLdReturns403WhenAskingForOtherUsersAsCasual() {
    when(securityContext.isUserInRole("instance-admin")).thenReturn(false);
    Response r = resource.countActivitiesJsonLd(
      "bob",
      null,
      null,
      null,
      null,
      "application/ld+json",
      securityContext
    );
    assertEquals(403, r.getStatus());
  }

  // --- listActivities (plain) regression ------------------------------------

  @Test
  void listActivitiesPlainJson401Unauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    Response r = resource.listActivities(null, null, null, null, null, null, securityContext);
    assertEquals(401, r.getStatus());
  }

  @Test
  void listActivitiesProvJsonStillRendersProvJsonSubset() {
    // Sanity that the prior PROV1g endpoint isn't disturbed by PROV1h.
    when(provenance.list(eq(CALLER), any(), any(), any(), any(), anyInt()))
      .thenReturn(List.of(row("a-1", "CREATE", "Collection", "c-1")));

    Response r = resource.listActivitiesProvJson(null, null, null, null, null, null, securityContext);

    assertEquals(200, r.getStatus());
    assertEquals(ProvJsonRenderer.MEDIA_TYPE, r.getMediaType().toString());
    @SuppressWarnings("unchecked")
    Map<String, Object> body = (Map<String, Object>) r.getEntity();
    assertNotNull(body.get("prefix"));
    assertNotNull(body.get("activity"));
    // The W3C PROV-JSON subset carries no @context.
    assertNull(body.get("@context"));
  }
}
