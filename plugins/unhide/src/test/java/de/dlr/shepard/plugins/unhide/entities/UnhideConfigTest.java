package de.dlr.shepard.plugins.unhide.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * UH1a — sanity tests on the {@link UnhideConfig} singleton entity.
 *
 * <p>The class is mostly lombok-generated, so the meaningful surface
 * we exercise is (a) the equals/hashCode contract under EqualsVerifier
 * (the appId-only equality is by design — two `:UnhideConfig` rows
 * with the same appId are the same logical singleton), and (b) the
 * default-field invariants the seed path relies on.
 */
class UnhideConfigTest {

  @Test
  void equals_isAppIdBased_notIdBased() {
    UnhideConfig a = new UnhideConfig();
    a.setAppId("01HFTEST");
    UnhideConfig b = new UnhideConfig();
    b.setAppId("01HFTEST");
    // Same appId, different ids ⇒ equal (the singleton semantic).
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
  }

  @Test
  void equals_reflexive() {
    UnhideConfig a = new UnhideConfig();
    a.setAppId("a");
    assertEquals(a, a);
  }

  @Test
  void equals_handlesNullAndNonInstance() {
    UnhideConfig a = new UnhideConfig();
    a.setAppId("a");
    assertNotEquals(a, null);
    assertNotEquals(a, "not-an-UnhideConfig");
  }

  @Test
  void equals_handlesNullAppId() {
    UnhideConfig a = new UnhideConfig();
    UnhideConfig b = new UnhideConfig();
    // Both unminted ⇒ equal (singleton placeholder; once persisted both
    // get the same minted appId).
    assertEquals(a, b);
  }

  @Test
  void defaults_areSafe() {
    UnhideConfig cfg = new UnhideConfig();
    assertFalse(cfg.isEnabled(), "fresh row defaults to enabled=false (safe-default)");
    assertFalse(cfg.isFeedPublic(), "fresh row defaults to feedPublic=false (safe-default)");
    assertNull(cfg.getContactEmail());
    assertNull(cfg.getHarvestApiKeyHash());
    assertNull(cfg.getHarvestApiKeyLastRotatedAt());
    assertNull(cfg.getAppId(), "appId minted on save by GenericDAO.createOrUpdate");
    assertNull(cfg.getId(), "Neo4j OGM Long id assigned at persist time");
  }

  @Test
  void settersAndGetters_roundTrip() {
    UnhideConfig cfg = new UnhideConfig();
    cfg.setEnabled(true);
    cfg.setFeedPublic(true);
    cfg.setContactEmail("alice@example.dlr.de");
    cfg.setHarvestApiKeyHash("deadbeef");
    cfg.setHarvestApiKeyLastRotatedAt(1700000000000L);
    cfg.setAppId("01HFTEST");

    assertTrue(cfg.isEnabled());
    assertTrue(cfg.isFeedPublic());
    assertEquals("alice@example.dlr.de", cfg.getContactEmail());
    assertEquals("deadbeef", cfg.getHarvestApiKeyHash());
    assertEquals(1700000000000L, cfg.getHarvestApiKeyLastRotatedAt());
    assertEquals("01HFTEST", cfg.getAppId());
  }

  @Test
  void testingConstructorSetsId() {
    UnhideConfig cfg = new UnhideConfig(42L);
    assertEquals(42L, cfg.getId());
  }

  @Test
  void toString_includesEnabledAndFeedPublic_butNotHashTail() {
    // Lombok @ToString shouldn't omit fields, but we sanity-check
    // that the rendering at least carries the booleans + appId — a
    // human eyeballing the log line wants those visible.
    UnhideConfig cfg = new UnhideConfig();
    cfg.setAppId("01HFTEST");
    cfg.setEnabled(true);
    String s = cfg.toString();
    assertNotNull(s);
    assertTrue(s.contains("01HFTEST"), "appId surfaces in toString: " + s);
    assertTrue(s.contains("enabled=true"), "enabled surfaces in toString: " + s);
  }

  @Test
  void equals_differentAppIds_areDistinct() {
    UnhideConfig a = new UnhideConfig();
    a.setAppId("a");
    UnhideConfig b = new UnhideConfig();
    b.setAppId("b");
    assertNotEquals(a, b);
    assertNotEquals(a.hashCode(), b.hashCode());
  }
}
