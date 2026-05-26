package de.dlr.shepard.v2.provenance.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import de.dlr.shepard.provenance.entities.Activity;
import org.junit.jupiter.api.Test;

/**
 * PROV1j — unit tests for ActivityIO.from() serialisation of sourceMode + agentId.
 *
 * <p>Test #5 from the PROV1j spec: "ActivityIO.from(activity) correctly serialises
 * sourceMode + agentId."
 */
class Prov1jActivityIOTest {

  private Activity buildActivity(String sourceMode, String agentId) {
    Activity a = new Activity();
    a.setAppId("act-prov1j");
    a.setActionKind("CREATE");
    a.setTargetKind("Collection");
    a.setTargetAppId("col-1");
    a.setAgentUsername("alice");
    a.setSummary("test");
    a.setStartedAtMillis(1_700_000_000_000L);
    a.setEndedAtMillis(1_700_000_000_500L);
    a.setOriginInstance("local");
    a.setSourceMode(sourceMode);
    a.setAgentId(agentId);
    return a;
  }

  @Test
  void from_humanActivity_sourceModeHumanAgentIdNull() {
    Activity a = buildActivity("human", null);
    ActivityIO io = ActivityIO.from(a);

    assertEquals("human", io.getSourceMode());
    assertNull(io.getAgentId());
  }

  @Test
  void from_aiActivity_sourceModeAiAgentIdSet() {
    Activity a = buildActivity("ai", "claude-sonnet-4-6");
    ActivityIO io = ActivityIO.from(a);

    assertEquals("ai", io.getSourceMode());
    assertEquals("claude-sonnet-4-6", io.getAgentId());
  }

  @Test
  void from_preProvActivity_sourceModeNull() {
    // Pre-PROV1j activities have no sourceMode property — reads back as null.
    Activity a = buildActivity(null, null);
    ActivityIO io = ActivityIO.from(a);

    assertNull(io.getSourceMode());
    assertNull(io.getAgentId());
  }

  @Test
  void metadataOnly_preservesSourceModeAndAgentId() {
    Activity a = buildActivity("ai", "gpt-4o");
    ActivityIO io = ActivityIO.from(a).metadataOnly();

    assertEquals("ai", io.getSourceMode());
    assertEquals("gpt-4o", io.getAgentId());
    // Request-shape fields must be cleared
    assertNull(io.getMethod());
    assertNull(io.getPath());
    assertNull(io.getStatus());
  }

  @Test
  void relationsOnly_preservesSourceModeAndAgentId() {
    Activity a = buildActivity("human", null);
    ActivityIO io = ActivityIO.from(a).relationsOnly();

    assertEquals("human", io.getSourceMode());
    assertNull(io.getAgentId());
  }
}
