package de.dlr.shepard.provenance.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.provenance.entities.Activity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProvJsonRendererTest {

  ProvJsonRenderer renderer = new ProvJsonRenderer();

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

  @Test
  void emptyInputProducesPrefixOnly() {
    var out = renderer.render(List.of());
    assertNotNull(out.get("prefix"));
    assertFalse(out.containsKey("activity"));
    assertFalse(out.containsKey("agent"));
  }

  @Test
  void prefixBlockCarriesShepardAndProvNamespaces() {
    var out = renderer.render(List.of(make("a-1", "CREATE", "alice", "Collection", "c-1")));
    @SuppressWarnings("unchecked")
    Map<String, String> prefix = (Map<String, String>) out.get("prefix");
    assertEquals("http://www.w3.org/ns/prov#", prefix.get("prov"));
    assertTrue(prefix.get("shepard").startsWith("https://"));
  }

  @Test
  void singleCreateActivityYieldsAgentEntityAndWasGeneratedBy() {
    var out = renderer.render(List.of(make("a-1", "CREATE", "alice", "Collection", "c-1")));

    @SuppressWarnings("unchecked")
    Map<String, Object> activity = (Map<String, Object>) out.get("activity");
    assertEquals(1, activity.size());
    assertNotNull(activity.get("shepard:activity/a-1"));

    @SuppressWarnings("unchecked")
    Map<String, Object> agent = (Map<String, Object>) out.get("agent");
    assertEquals(1, agent.size());
    assertNotNull(agent.get("shepard:agent/alice"));

    @SuppressWarnings("unchecked")
    Map<String, Object> entity = (Map<String, Object>) out.get("entity");
    assertEquals(1, entity.size());
    assertNotNull(entity.get("shepard:entity/c-1"));

    assertTrue(out.containsKey("wasAssociatedWith"));
    assertTrue(out.containsKey("wasGeneratedBy"));
    assertFalse(out.containsKey("used")); // CREATE → generated, not used
  }

  @Test
  void readActivityYieldsUsedNotWasGeneratedBy() {
    var out = renderer.render(List.of(make("a-r", "READ", "alice", "Collection", "c-1")));
    assertTrue(out.containsKey("used"));
    assertFalse(out.containsKey("wasGeneratedBy"));
  }

  @Test
  void updateAndDeleteBothYieldWasGeneratedBy() {
    assertTrue(renderer.render(List.of(make("a-u", "UPDATE", "alice", "Coll", "x"))).containsKey("wasGeneratedBy"));
    assertTrue(renderer.render(List.of(make("a-d", "DELETE", "alice", "Coll", "x"))).containsKey("wasGeneratedBy"));
  }

  @Test
  void agentDedupedAcrossActivities() {
    var out = renderer.render(
      List.of(
        make("a-1", "CREATE", "alice", "Collection", "c-1"),
        make("a-2", "UPDATE", "alice", "Collection", "c-2"),
        make("a-3", "CREATE", "alice", "DataObject", "d-1")
      )
    );
    @SuppressWarnings("unchecked")
    Map<String, Object> agent = (Map<String, Object>) out.get("agent");
    // Only one agent block per username, regardless of activity count.
    assertEquals(1, agent.size());
    // But three activities + three wasGeneratedBy edges + three associations.
    @SuppressWarnings("unchecked")
    Map<String, Object> activity = (Map<String, Object>) out.get("activity");
    assertEquals(3, activity.size());
  }

  @Test
  void activityWithoutTargetSkipsEntityAndEdges() {
    Activity a = new Activity();
    a.setAppId("a-0");
    a.setActionKind("EXECUTE");
    a.setAgentUsername("alice");
    a.setStartedAtMillis(1_700_000_000_000L);
    var out = renderer.render(List.of(a));
    assertFalse(out.containsKey("entity"));
    assertFalse(out.containsKey("used"));
    assertFalse(out.containsKey("wasGeneratedBy"));
    // But the activity + agent + association still render.
    assertTrue(out.containsKey("activity"));
    assertTrue(out.containsKey("agent"));
    assertTrue(out.containsKey("wasAssociatedWith"));
  }

  @Test
  void startTimeAndEndTimeAreIso8601() {
    var out = renderer.render(List.of(make("a-1", "CREATE", "alice", "Collection", "c-1")));
    @SuppressWarnings("unchecked")
    Map<String, Object> activity = (Map<String, Object>) out.get("activity");
    @SuppressWarnings("unchecked")
    Map<String, Object> a1 = (Map<String, Object>) activity.get("shepard:activity/a-1");
    assertTrue(a1.get("prov:startTime").toString().contains("T"));
    assertTrue(a1.get("prov:startTime").toString().endsWith("Z"));
  }

  @Test
  void mediaTypeConstant() {
    assertEquals("application/prov+json", ProvJsonRenderer.MEDIA_TYPE);
  }

  @Test
  void readActionClassifier() {
    assertTrue(ProvJsonRenderer.isReadAction("READ"));
    assertFalse(ProvJsonRenderer.isReadAction("CREATE"));
    assertFalse(ProvJsonRenderer.isReadAction(null));
  }

  @Test
  void writeActionClassifier() {
    assertTrue(ProvJsonRenderer.isWriteAction("CREATE"));
    assertTrue(ProvJsonRenderer.isWriteAction("UPDATE"));
    assertTrue(ProvJsonRenderer.isWriteAction("DELETE"));
    assertTrue(ProvJsonRenderer.isWriteAction("EXECUTE"));
    assertFalse(ProvJsonRenderer.isWriteAction("READ"));
    assertFalse(ProvJsonRenderer.isWriteAction(null));
  }
}
