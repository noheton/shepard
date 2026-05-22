package de.dlr.shepard.plugins.v1compat.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * V1COMPAT.0 — sanity tests on the {@link LegacyV1Config} singleton
 * entity. The class is mostly lombok-generated, so the meaningful
 * surface we exercise is (a) the equals/hashCode contract — appId-
 * only equality is by design, two rows sharing an appId ARE the
 * same logical singleton — and (b) the default-field invariants the
 * seed path and the gate filter rely on.
 */
class LegacyV1ConfigTest {

  @Test
  void equals_isAppIdBased_notIdBased() {
    LegacyV1Config a = new LegacyV1Config();
    a.setAppId("01HFTEST");
    LegacyV1Config b = new LegacyV1Config();
    b.setAppId("01HFTEST");
    // Same appId, different ids ⇒ equal (the singleton semantic).
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
  }

  @Test
  void equals_reflexive() {
    LegacyV1Config a = new LegacyV1Config();
    a.setAppId("a");
    assertEquals(a, a);
  }

  @Test
  void equals_differentAppId_notEqual() {
    LegacyV1Config a = new LegacyV1Config();
    a.setAppId("a");
    LegacyV1Config b = new LegacyV1Config();
    b.setAppId("b");
    assertNotEquals(a, b);
  }

  @Test
  void equals_nullAppIds_treatedAsEqualSingleton() {
    // Pre-save rows (both appIds null) — equality on null appIds is
    // not load-bearing in production (every save mints an appId), but
    // Objects.hash(null) and Objects.equals(null, null) both work,
    // so the contract should hold.
    LegacyV1Config a = new LegacyV1Config();
    LegacyV1Config b = new LegacyV1Config();
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
  }

  @Test
  void equals_nullVsNotNull_notEqual() {
    LegacyV1Config a = new LegacyV1Config();
    LegacyV1Config b = new LegacyV1Config();
    b.setAppId("b");
    assertNotEquals(a, b);
    assertNotEquals(b, a);
  }

  @Test
  void equals_differentType_notEqual() {
    LegacyV1Config a = new LegacyV1Config();
    a.setAppId("x");
    assertNotEquals(a, new Object());
    assertNotEquals(a, null);
  }

  @Test
  void defaultEnabled_isTrue_perSunsetPhilosophy() {
    // The v1 sunset philosophy is "default-on, no fork-imposed
    // timeline; operator decides when to flip". A fresh entity must
    // come up enabled.
    LegacyV1Config fresh = new LegacyV1Config();
    assertTrue(fresh.isEnabled());
  }

  @Test
  void defaultAuditFields_areNull() {
    LegacyV1Config fresh = new LegacyV1Config();
    // The seed path stamps createdAt/updatedAt; a fresh ctor leaves
    // both null so the seed can detect "this row hasn't been
    // persisted yet" without relying on Long.MIN_VALUE sentinels.
    assertNull(fresh.getCreatedAt());
    assertNull(fresh.getUpdatedAt());
    assertNull(fresh.getUpdatedBy());
  }

  @Test
  void setters_roundTrip() {
    LegacyV1Config c = new LegacyV1Config();
    c.setEnabled(false);
    c.setAppId("01HF-AAA");
    c.setCreatedAt(1_700_000_000_000L);
    c.setUpdatedAt(1_700_000_001_000L);
    c.setUpdatedBy("admin@example");
    assertFalse(c.isEnabled());
    assertEquals("01HF-AAA", c.getAppId());
    assertEquals(1_700_000_000_000L, c.getCreatedAt());
    assertEquals(1_700_000_001_000L, c.getUpdatedAt());
    assertEquals("admin@example", c.getUpdatedBy());
  }

  @Test
  void testIdCtor_setsId() {
    LegacyV1Config c = new LegacyV1Config(42L);
    assertEquals(42L, c.getId());
    // appId is still null until persistence layer mints it
    assertNull(c.getAppId());
    // Default enabled invariant survives the test-only ctor
    assertTrue(c.isEnabled());
  }

  @Test
  void hasAppIdContract_returnsAppId() {
    LegacyV1Config c = new LegacyV1Config();
    assertNull(c.getAppId());
    c.setAppId("abc");
    assertEquals("abc", c.getAppId());
  }

  @Test
  void toStringSafetyNet_includesEnabled() {
    LegacyV1Config c = new LegacyV1Config();
    c.setAppId("01HF-AAA");
    String s = c.toString();
    // Lombok-generated toString — light sanity that key fields appear,
    // so audit-log grep of WARN/INFO messages stays useful.
    assertNotNull(s);
    assertTrue(s.contains("01HF-AAA"));
    assertTrue(s.contains("enabled"));
  }
}
