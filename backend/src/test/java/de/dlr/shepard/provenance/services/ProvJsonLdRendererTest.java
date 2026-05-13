package de.dlr.shepard.provenance.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.provenance.entities.Activity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProvJsonLdRendererTest {

  ProvJsonLdRenderer renderer = new ProvJsonLdRenderer();

  private Activity make(String appId, String actionKind, String agent, String targetKind, String targetAppId) {
    Activity a = new Activity();
    a.setAppId(appId);
    a.setActionKind(actionKind);
    a.setAgentUsername(agent);
    a.setTargetKind(targetKind);
    a.setTargetAppId(targetAppId);
    a.setStartedAtMillis(1_700_000_000_000L);
    a.setEndedAtMillis(1_700_000_000_500L);
    a.setSummary("test " + actionKind);
    a.setOriginInstance("local");
    return a;
  }

  // --- PROV-O flavour -----------------------------------------------------

  @Test
  void provOEmptyInputProducesContextAndEmptyGraph() {
    var out = renderer.renderProvO(List.of());
    assertNotNull(out.get("@context"));
    @SuppressWarnings("unchecked")
    List<Object> graph = (List<Object>) out.get("@graph");
    assertNotNull(graph);
    assertTrue(graph.isEmpty());
  }

  @Test
  void provOContextDeclaresProvAndShepardNamespacesButNotM4i() {
    var out = renderer.renderProvO(List.of(make("a-1", "CREATE", "alice", "Collection", "c-1")));
    @SuppressWarnings("unchecked")
    Map<String, String> ctx = (Map<String, String>) out.get("@context");
    assertEquals("http://www.w3.org/ns/prov#", ctx.get("prov"));
    assertTrue(ctx.get("shepard").startsWith("https://"));
    assertFalse(ctx.containsKey("m4i"));
  }

  @Test
  void provOSingleCreateActivityCarriesProvActivityType() {
    var out = renderer.renderProvO(List.of(make("a-1", "CREATE", "alice", "Collection", "c-1")));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> graph = (List<Map<String, Object>>) out.get("@graph");
    // Agent + entity + activity nodes — three.
    assertEquals(3, graph.size());
    // Find the activity node (the one ending in `/a-1`).
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
  void provOStartedAtTimeIsTypedXsdDateTime() {
    var out = renderer.renderProvO(List.of(make("a-1", "CREATE", "alice", "Collection", "c-1")));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> graph = (List<Map<String, Object>>) out.get("@graph");
    Map<String, Object> activity = graph
      .stream()
      .filter(n -> "shepard:activity/a-1".equals(n.get("@id")))
      .findFirst()
      .orElseThrow();
    @SuppressWarnings("unchecked")
    Map<String, Object> startedAt = (Map<String, Object>) activity.get("prov:startedAtTime");
    assertEquals("xsd:dateTime", startedAt.get("@type"));
    assertTrue(startedAt.get("@value").toString().endsWith("Z"));
  }

  @Test
  void provOEndedAtTimePresentWhenSet() {
    var out = renderer.renderProvO(List.of(make("a-1", "CREATE", "alice", "Collection", "c-1")));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> graph = (List<Map<String, Object>>) out.get("@graph");
    Map<String, Object> activity = graph
      .stream()
      .filter(n -> "shepard:activity/a-1".equals(n.get("@id")))
      .findFirst()
      .orElseThrow();
    assertNotNull(activity.get("prov:endedAtTime"));
  }

  @Test
  void provOCreateActivityHasProvGeneratedAndNoProvUsed() {
    var out = renderer.renderProvO(List.of(make("a-1", "CREATE", "alice", "Collection", "c-1")));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> graph = (List<Map<String, Object>>) out.get("@graph");
    Map<String, Object> activity = graph
      .stream()
      .filter(n -> "shepard:activity/a-1".equals(n.get("@id")))
      .findFirst()
      .orElseThrow();
    assertNotNull(activity.get("prov:generated"));
    assertNull(activity.get("prov:used"));
  }

  @Test
  void provOReadActivityHasProvUsedAndNoProvGenerated() {
    var out = renderer.renderProvO(List.of(make("a-r", "READ", "alice", "Collection", "c-1")));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> graph = (List<Map<String, Object>>) out.get("@graph");
    Map<String, Object> activity = graph
      .stream()
      .filter(n -> "shepard:activity/a-r".equals(n.get("@id")))
      .findFirst()
      .orElseThrow();
    assertNotNull(activity.get("prov:used"));
    assertNull(activity.get("prov:generated"));
  }

  @Test
  void provOAgentNodeHasProvAgentAndProvPersonTypes() {
    var out = renderer.renderProvO(List.of(make("a-1", "CREATE", "alice", "Collection", "c-1")));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> graph = (List<Map<String, Object>>) out.get("@graph");
    Map<String, Object> agent = graph
      .stream()
      .filter(n -> "shepard:agent/alice".equals(n.get("@id")))
      .findFirst()
      .orElseThrow();
    @SuppressWarnings("unchecked")
    List<String> types = (List<String>) agent.get("@type");
    assertTrue(types.contains("prov:Agent"));
    assertTrue(types.contains("prov:Person"));
    assertFalse(types.contains("m4i:Person"));
  }

  @Test
  void provOEntityNodeHasProvEntityType() {
    var out = renderer.renderProvO(List.of(make("a-1", "CREATE", "alice", "Collection", "c-1")));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> graph = (List<Map<String, Object>>) out.get("@graph");
    Map<String, Object> entity = graph
      .stream()
      .filter(n -> "shepard:entity/c-1".equals(n.get("@id")))
      .findFirst()
      .orElseThrow();
    @SuppressWarnings("unchecked")
    List<String> types = (List<String>) entity.get("@type");
    assertEquals(List.of("prov:Entity"), types);
    assertEquals("Collection", entity.get("shepard:kind"));
  }

  @Test
  void provOWasAssociatedWithReferencesAgentById() {
    var out = renderer.renderProvO(List.of(make("a-1", "CREATE", "alice", "Collection", "c-1")));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> graph = (List<Map<String, Object>>) out.get("@graph");
    Map<String, Object> activity = graph
      .stream()
      .filter(n -> "shepard:activity/a-1".equals(n.get("@id")))
      .findFirst()
      .orElseThrow();
    @SuppressWarnings("unchecked")
    Map<String, Object> assoc = (Map<String, Object>) activity.get("prov:wasAssociatedWith");
    assertEquals("shepard:agent/alice", assoc.get("@id"));
  }

  @Test
  void provOActivityWithoutAppIdGetsIxFallback() {
    Activity a = new Activity();
    a.setActionKind("CREATE");
    a.setAgentUsername("bob");
    a.setStartedAtMillis(1_700_000_000_000L);
    var out = renderer.renderProvO(List.of(a));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> graph = (List<Map<String, Object>>) out.get("@graph");
    // The activity node `@id` must include "ix-0" when no appId is set.
    boolean found = graph
      .stream()
      .anyMatch(n -> "shepard:activity/ix-0".equals(n.get("@id")));
    assertTrue(found);
  }

  @Test
  void provOActivityWithoutAgentSkipsAssociation() {
    Activity a = new Activity();
    a.setAppId("a-noagent");
    a.setActionKind("EXECUTE");
    a.setStartedAtMillis(1_700_000_000_000L);
    var out = renderer.renderProvO(List.of(a));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> graph = (List<Map<String, Object>>) out.get("@graph");
    Map<String, Object> activity = graph
      .stream()
      .filter(n -> "shepard:activity/a-noagent".equals(n.get("@id")))
      .findFirst()
      .orElseThrow();
    assertNull(activity.get("prov:wasAssociatedWith"));
  }

  @Test
  void provOActivityWithoutTargetSkipsUsedAndGenerated() {
    Activity a = new Activity();
    a.setAppId("a-notarget");
    a.setActionKind("EXECUTE");
    a.setAgentUsername("bob");
    a.setStartedAtMillis(1_700_000_000_000L);
    var out = renderer.renderProvO(List.of(a));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> graph = (List<Map<String, Object>>) out.get("@graph");
    Map<String, Object> activity = graph
      .stream()
      .filter(n -> "shepard:activity/a-notarget".equals(n.get("@id")))
      .findFirst()
      .orElseThrow();
    assertNull(activity.get("prov:used"));
    assertNull(activity.get("prov:generated"));
  }

  @Test
  void provOSummaryAndOriginInstancePreserved() {
    var out = renderer.renderProvO(List.of(make("a-1", "CREATE", "alice", "Collection", "c-1")));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> graph = (List<Map<String, Object>>) out.get("@graph");
    Map<String, Object> activity = graph
      .stream()
      .filter(n -> "shepard:activity/a-1".equals(n.get("@id")))
      .findFirst()
      .orElseThrow();
    assertEquals("test CREATE", activity.get("shepard:summary"));
    assertEquals("local", activity.get("shepard:originInstance"));
  }

  // --- m4i flavour --------------------------------------------------------

  @Test
  void m4iContextDeclaresAllNamespaces() {
    var out = renderer.renderMetadata4ing(List.of(make("a-1", "CREATE", "alice", "Collection", "c-1")));
    @SuppressWarnings("unchecked")
    Map<String, String> ctx = (Map<String, String>) out.get("@context");
    assertEquals("http://www.w3.org/ns/prov#", ctx.get("prov"));
    assertEquals("http://w3id.org/nfdi4ing/metadata4ing#", ctx.get("m4i"));
    assertTrue(ctx.get("shepard").startsWith("https://"));
  }

  @Test
  void m4iActivityCarriesDualTypeM4iProcessingStepAndProvActivity() {
    var out = renderer.renderMetadata4ing(List.of(make("a-1", "CREATE", "alice", "Collection", "c-1")));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> graph = (List<Map<String, Object>>) out.get("@graph");
    Map<String, Object> activity = graph
      .stream()
      .filter(n -> "shepard:activity/a-1".equals(n.get("@id")))
      .findFirst()
      .orElseThrow();
    @SuppressWarnings("unchecked")
    List<String> types = (List<String>) activity.get("@type");
    assertEquals(List.of("m4i:ProcessingStep", "prov:Activity"), types);
  }

  @Test
  void m4iAgentCarriesM4iPersonAndProvAgent() {
    var out = renderer.renderMetadata4ing(List.of(make("a-1", "CREATE", "alice", "Collection", "c-1")));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> graph = (List<Map<String, Object>>) out.get("@graph");
    Map<String, Object> agent = graph
      .stream()
      .filter(n -> "shepard:agent/alice".equals(n.get("@id")))
      .findFirst()
      .orElseThrow();
    @SuppressWarnings("unchecked")
    List<String> types = (List<String>) agent.get("@type");
    assertTrue(types.contains("m4i:Person"));
    assertTrue(types.contains("prov:Agent"));
    assertTrue(types.contains("prov:Person"));
  }

  @Test
  void m4iEntityCarriesM4iInvestigatedObjectAndProvEntity() {
    var out = renderer.renderMetadata4ing(List.of(make("a-1", "CREATE", "alice", "Collection", "c-1")));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> graph = (List<Map<String, Object>>) out.get("@graph");
    Map<String, Object> entity = graph
      .stream()
      .filter(n -> "shepard:entity/c-1".equals(n.get("@id")))
      .findFirst()
      .orElseThrow();
    @SuppressWarnings("unchecked")
    List<String> types = (List<String>) entity.get("@type");
    assertEquals(List.of("m4i:InvestigatedObject", "prov:Entity"), types);
  }

  @Test
  void m4iHasInputPresentForReadAndHasOutputForWrite() {
    // READ → hasInput
    var readOut = renderer.renderMetadata4ing(List.of(make("a-r", "READ", "alice", "Collection", "c-1")));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> readGraph = (List<Map<String, Object>>) readOut.get("@graph");
    Map<String, Object> readActivity = readGraph
      .stream()
      .filter(n -> "shepard:activity/a-r".equals(n.get("@id")))
      .findFirst()
      .orElseThrow();
    assertNotNull(readActivity.get("m4i:hasInput"));
    assertNull(readActivity.get("m4i:hasOutput"));

    // CREATE → hasOutput
    var writeOut = renderer.renderMetadata4ing(List.of(make("a-w", "CREATE", "alice", "Collection", "c-1")));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> writeGraph = (List<Map<String, Object>>) writeOut.get("@graph");
    Map<String, Object> writeActivity = writeGraph
      .stream()
      .filter(n -> "shepard:activity/a-w".equals(n.get("@id")))
      .findFirst()
      .orElseThrow();
    assertNotNull(writeActivity.get("m4i:hasOutput"));
    assertNull(writeActivity.get("m4i:hasInput"));
  }

  @Test
  void m4iHasMethodPlaceholderPresentWhenActionKindKnown() {
    var out = renderer.renderMetadata4ing(List.of(make("a-1", "CREATE", "alice", "Collection", "c-1")));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> graph = (List<Map<String, Object>>) out.get("@graph");
    Map<String, Object> activity = graph
      .stream()
      .filter(n -> "shepard:activity/a-1".equals(n.get("@id")))
      .findFirst()
      .orElseThrow();
    // Pragmatic m4i:hasMethod mapping ships as a shepard:method/<kind>
    // IRI; PROV-O readers see the parallel prov:type carrying the same
    // value.
    assertEquals("shepard:method/CREATE", activity.get("m4i:hasMethod"));
    assertEquals("shepard:CREATE", activity.get("prov:type"));
  }

  // --- dispatch -----------------------------------------------------------

  @Test
  void renderDispatchSelectsByProfile() {
    var provOOut = renderer.render(List.of(make("a-1", "CREATE", "alice", "Collection", "c-1")), ProvJsonLdRenderer.ProfileChoice.PROV_O);
    var m4iOut = renderer.render(List.of(make("a-1", "CREATE", "alice", "Collection", "c-1")), ProvJsonLdRenderer.ProfileChoice.M4I);
    @SuppressWarnings("unchecked")
    Map<String, String> provOCtx = (Map<String, String>) provOOut.get("@context");
    @SuppressWarnings("unchecked")
    Map<String, String> m4iCtx = (Map<String, String>) m4iOut.get("@context");
    assertFalse(provOCtx.containsKey("m4i"));
    assertTrue(m4iCtx.containsKey("m4i"));
  }

  // --- profile param parsing ---------------------------------------------

  @Test
  void extractProfileParamReturnsNullForNullHeader() {
    assertNull(ProvJsonLdRenderer.extractProfileParam(null));
  }

  @Test
  void extractProfileParamReturnsNullForJsonHeaderOnly() {
    assertNull(ProvJsonLdRenderer.extractProfileParam("application/json"));
  }

  @Test
  void extractProfileParamReturnsNullForLdJsonNoProfile() {
    assertNull(ProvJsonLdRenderer.extractProfileParam("application/ld+json"));
  }

  @Test
  void extractProfileParamHandlesQuotedValue() {
    assertEquals(
      "https://w3id.org/nfdi4ing/metadata4ing/",
      ProvJsonLdRenderer.extractProfileParam("application/ld+json; profile=\"https://w3id.org/nfdi4ing/metadata4ing/\"")
    );
  }

  @Test
  void extractProfileParamHandlesUnquotedShortForm() {
    assertEquals("metadata4ing", ProvJsonLdRenderer.extractProfileParam("application/ld+json; profile=metadata4ing"));
  }

  @Test
  void extractProfileParamHandlesCommaSeparatedMultipleMediaTypes() {
    String header = "text/html, application/ld+json; profile=metadata4ing, */*;q=0.1";
    assertEquals("metadata4ing", ProvJsonLdRenderer.extractProfileParam(header));
  }

  @Test
  void resolveProfileReturnsProvOWhenNoProfileGiven() {
    assertEquals(ProvJsonLdRenderer.ProfileChoice.PROV_O, ProvJsonLdRenderer.resolveProfile("application/ld+json"));
    assertEquals(ProvJsonLdRenderer.ProfileChoice.PROV_O, ProvJsonLdRenderer.resolveProfile(null));
  }

  @Test
  void resolveProfileReturnsM4iForFullUri() {
    assertEquals(
      ProvJsonLdRenderer.ProfileChoice.M4I,
      ProvJsonLdRenderer.resolveProfile("application/ld+json; profile=\"https://w3id.org/nfdi4ing/metadata4ing/\"")
    );
  }

  @Test
  void resolveProfileReturnsM4iForShortForm() {
    assertEquals(
      ProvJsonLdRenderer.ProfileChoice.M4I,
      ProvJsonLdRenderer.resolveProfile("application/ld+json; profile=metadata4ing")
    );
  }

  @Test
  void resolveProfileReturnsM4iForUriWithoutTrailingSlash() {
    assertEquals(
      ProvJsonLdRenderer.ProfileChoice.M4I,
      ProvJsonLdRenderer.resolveProfile("application/ld+json; profile=\"https://w3id.org/nfdi4ing/metadata4ing\"")
    );
  }

  @Test
  void resolveProfileIsCaseInsensitive() {
    assertEquals(
      ProvJsonLdRenderer.ProfileChoice.M4I,
      ProvJsonLdRenderer.resolveProfile("application/ld+json; profile=METADATA4ING")
    );
  }

  @Test
  void resolveProfileReturnsNullForUnknownProfile() {
    assertNull(ProvJsonLdRenderer.resolveProfile("application/ld+json; profile=unknown"));
    assertNull(ProvJsonLdRenderer.resolveProfile("application/ld+json; profile=\"https://example.com/foo\""));
  }

  @Test
  void mediaTypeConstantUsesLdJson() {
    assertEquals("application/ld+json", ProvJsonLdRenderer.MEDIA_TYPE);
  }

  // --- count wrapper ------------------------------------------------------

  @Test
  void renderCountProvOWrapsLongUnderShepardKey() {
    var out = renderer.renderCount(42L, ProvJsonLdRenderer.ProfileChoice.PROV_O);
    assertNotNull(out.get("@context"));
    @SuppressWarnings("unchecked")
    Map<String, Object> typed = (Map<String, Object>) out.get("shepard:numberOfActivities");
    assertEquals("xsd:nonNegativeInteger", typed.get("@type"));
    assertEquals("42", typed.get("@value"));
    @SuppressWarnings("unchecked")
    Map<String, String> ctx = (Map<String, String>) out.get("@context");
    assertFalse(ctx.containsKey("m4i"));
  }

  @Test
  void renderCountM4iAddsM4iNamespaceToContext() {
    var out = renderer.renderCount(0L, ProvJsonLdRenderer.ProfileChoice.M4I);
    @SuppressWarnings("unchecked")
    Map<String, String> ctx = (Map<String, String>) out.get("@context");
    assertEquals("http://w3id.org/nfdi4ing/metadata4ing#", ctx.get("m4i"));
    @SuppressWarnings("unchecked")
    Map<String, Object> typed = (Map<String, Object>) out.get("shepard:numberOfActivities");
    assertEquals("0", typed.get("@value"));
  }

  // --- renderActivityAsM4iNode (UH1b — single-activity m4i body) ----------

  @Test
  void renderActivityAsM4iNode_nullInput_yieldsEmptyMap() {
    Map<String, Object> node = renderer.renderActivityAsM4iNode(null);
    assertNotNull(node);
    assertTrue(node.isEmpty(),
      "null activity must not NPE — return empty map and let the caller decide whether to embed it");
  }

  @Test
  void renderActivityAsM4iNode_carriesDualType() {
    Activity a = make("a-1", "CREATE", "alice", "Collection", "c-1");
    Map<String, Object> node = renderer.renderActivityAsM4iNode(a);
    @SuppressWarnings("unchecked")
    List<String> types = (List<String>) node.get("@type");
    assertEquals(List.of("m4i:ProcessingStep", "prov:Activity"), types,
      "single-activity m4i node carries the same dual-type as the full-graph m4i path");
  }

  @Test
  void renderActivityAsM4iNode_carriesIdFromAppId() {
    Activity a = make("a-42", "UPDATE", "bob", "Collection", "c-99");
    Map<String, Object> node = renderer.renderActivityAsM4iNode(a);
    assertEquals("shepard:activity/a-42", node.get("@id"));
  }

  @Test
  void renderActivityAsM4iNode_withoutAppId_carriesAnonSuffix() {
    Activity a = new Activity();
    a.setActionKind("CREATE");
    a.setStartedAtMillis(1L);
    Map<String, Object> node = renderer.renderActivityAsM4iNode(a);
    assertEquals("shepard:activity/anon", node.get("@id"),
      "no appId means there's nothing stable to cite — use a clear 'anon' sentinel");
  }

  @Test
  void renderActivityAsM4iNode_startedAtTime_isTypedXsdDateTime() {
    Activity a = make("a-1", "CREATE", "alice", "Collection", "c-1");
    Map<String, Object> node = renderer.renderActivityAsM4iNode(a);
    @SuppressWarnings("unchecked")
    Map<String, Object> startedAt = (Map<String, Object>) node.get("prov:startedAtTime");
    assertEquals("xsd:dateTime", startedAt.get("@type"));
    assertTrue(startedAt.get("@value").toString().endsWith("Z"));
  }

  @Test
  void renderActivityAsM4iNode_createMapsToHasOutput() {
    Activity a = make("a-1", "CREATE", "alice", "Collection", "c-1");
    Map<String, Object> node = renderer.renderActivityAsM4iNode(a);
    assertNotNull(node.get("m4i:hasOutput"));
    assertNotNull(node.get("prov:generated"));
    assertNull(node.get("m4i:hasInput"));
  }

  @Test
  void renderActivityAsM4iNode_readMapsToHasInput() {
    Activity a = make("a-r", "READ", "alice", "Collection", "c-1");
    Map<String, Object> node = renderer.renderActivityAsM4iNode(a);
    assertNotNull(node.get("m4i:hasInput"));
    assertNotNull(node.get("prov:used"));
    assertNull(node.get("m4i:hasOutput"));
  }

  @Test
  void renderActivityAsM4iNode_agent_collapsesToIdStub() {
    Activity a = make("a-1", "CREATE", "alice", "Collection", "c-1");
    Map<String, Object> node = renderer.renderActivityAsM4iNode(a);
    @SuppressWarnings("unchecked")
    Map<String, Object> assoc = (Map<String, Object>) node.get("prov:wasAssociatedWith");
    assertEquals("shepard:agent/alice", assoc.get("@id"),
      "single-activity m4i node references the agent by @id only — no sibling agent node " +
      "(the wrapping feed's @context covers the namespace expansion)");
  }

  @Test
  void renderActivityAsM4iNode_hasMethod_setOnActionKind() {
    Activity a = make("a-1", "CREATE", "alice", "Collection", "c-1");
    Map<String, Object> node = renderer.renderActivityAsM4iNode(a);
    assertEquals("shepard:method/CREATE", node.get("m4i:hasMethod"));
    assertEquals("shepard:CREATE", node.get("prov:type"));
  }

  @Test
  void renderActivityAsM4iNode_summaryAndOriginInstancePropagate() {
    Activity a = make("a-1", "UPDATE", "alice", "Collection", "c-1");
    Map<String, Object> node = renderer.renderActivityAsM4iNode(a);
    assertEquals("test UPDATE", node.get("shepard:summary"));
    assertEquals("local", node.get("shepard:originInstance"));
  }

  @Test
  void renderActivityAsM4iNode_omitsTargetFields_whenNoTargetAppId() {
    Activity a = new Activity();
    a.setAppId("a-1");
    a.setActionKind("EXECUTE");
    a.setStartedAtMillis(1L);
    Map<String, Object> node = renderer.renderActivityAsM4iNode(a);
    assertNull(node.get("m4i:hasInput"));
    assertNull(node.get("m4i:hasOutput"));
    assertNull(node.get("prov:used"));
    assertNull(node.get("prov:generated"));
  }

  @Test
  void renderActivityAsM4iNode_returnsFreshMap_onEachCall() {
    Activity a = make("a-1", "CREATE", "alice", "Collection", "c-1");
    Map<String, Object> first = renderer.renderActivityAsM4iNode(a);
    Map<String, Object> second = renderer.renderActivityAsM4iNode(a);
    first.put("scratch", "value");
    assertNull(second.get("scratch"),
      "caller-owned maps — each call returns a fresh LinkedHashMap");
  }
}
