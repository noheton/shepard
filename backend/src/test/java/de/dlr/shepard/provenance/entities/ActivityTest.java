package de.dlr.shepard.provenance.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ActivityTest {

  @Test
  void uniqueIdIsTheAppId() {
    var a = new Activity();
    a.setAppId("018f9c5a-7e26-7000-a000-000000000001");
    assertEquals("018f9c5a-7e26-7000-a000-000000000001", a.getUniqueId());
  }

  @Test
  void equalsByAppId() {
    var a = new Activity();
    a.setAppId("uuid-1");
    a.setSummary("first");
    var b = new Activity();
    b.setAppId("uuid-1");
    b.setSummary("second"); // different summary, same appId → equal
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
  }

  @Test
  void notEqualForDifferentAppId() {
    var a = new Activity();
    a.setAppId("uuid-1");
    var b = new Activity();
    b.setAppId("uuid-2");
    assertNotEquals(a, b);
  }

  @Test
  void constructorPopulatesCoreFields() {
    long ts = 1_700_000_000_000L;
    var a = new Activity("CREATE", "Collection", "coll-app-id", "alice", "POST /v2/collections", ts);
    assertEquals("CREATE", a.getActionKind());
    assertEquals("Collection", a.getTargetKind());
    assertEquals("coll-app-id", a.getTargetAppId());
    assertEquals("alice", a.getAgentUsername());
    assertEquals("POST /v2/collections", a.getSummary());
    assertEquals(ts, a.getStartedAtMillis());
    assertNull(a.getEndedAtMillis());
  }
}
