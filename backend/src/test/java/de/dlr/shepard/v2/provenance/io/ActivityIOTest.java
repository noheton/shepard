package de.dlr.shepard.v2.provenance.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.provenance.entities.Activity;
import org.junit.jupiter.api.Test;

/** Unit tests for U1b2: DisplayNameResolver.redactUsername applied in ActivityIO.from(). */
class ActivityIOTest {

  private Activity buildActivity(String agentUsername) {
    Activity a = new Activity();
    a.setAppId("act-1");
    a.setActionKind("CREATE");
    a.setTargetKind("Collection");
    a.setTargetAppId("col-1");
    a.setAgentUsername(agentUsername);
    a.setSummary("test");
    a.setStartedAtMillis(1_700_000_000_000L);
    a.setEndedAtMillis(1_700_000_000_500L);
    a.setOriginInstance("local");
    return a;
  }

  @Test
  void plainUsernamePassesThrough() {
    ActivityIO io = ActivityIO.from(buildActivity("alice"));
    assertEquals("alice", io.getAgentUsername());
  }

  @Test
  void uuidShapedKeycloakSubjectIsRedacted() {
    // Keycloak uses UUID subjects like "f2b5a4c3-1234-5678-abcd-1234567890ab"
    ActivityIO io = ActivityIO.from(buildActivity("f2b5a4c3-1234-5678-abcd-1234567890ab"));
    assertEquals("f2b5a4c3…", io.getAgentUsername());
  }

  @Test
  void colonPrefixedSubjectUsesTrailingSegmentAndRedacts() {
    // Keycloak sometimes emits "realm:f2b5a4c3-1234-5678-abcd-1234567890ab"
    ActivityIO io = ActivityIO.from(buildActivity("realm:f2b5a4c3-1234-5678-abcd-1234567890ab"));
    assertEquals("f2b5a4c3…", io.getAgentUsername());
  }

  @Test
  void shortReadableUsernameUnchanged() {
    ActivityIO io = ActivityIO.from(buildActivity("jdoe"));
    assertEquals("jdoe", io.getAgentUsername());
  }

  @Test
  void nullAgentUsernameBecomesAnonymous() {
    ActivityIO io = ActivityIO.from(buildActivity(null));
    assertEquals("(anonymous)", io.getAgentUsername());
  }
}
