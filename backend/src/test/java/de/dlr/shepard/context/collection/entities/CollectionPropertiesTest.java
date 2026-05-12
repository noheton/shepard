package de.dlr.shepard.context.collection.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CollectionPropertiesTest {

  @Test
  void uniqueIdIsTheAppId() {
    var p = new CollectionProperties("appId-123");
    assertEquals("appId-123", p.getUniqueId());
  }

  @Test
  void webdavVisibleDefaultsTrue() {
    var p = new CollectionProperties();
    assertTrue(p.isWebdavVisible());
  }

  @Test
  void canFlipWebdavVisibility() {
    var p = new CollectionProperties();
    p.setWebdavVisible(false);
    assertFalse(p.isWebdavVisible());
  }

  @Test
  void defaultOntologyUriIsNullByDefault() {
    var p = new CollectionProperties();
    assertNull(p.getDefaultOntologyUri());
  }

  @Test
  void equalsByAppId() {
    var a = new CollectionProperties("appId-1");
    a.setWebdavVisible(true);
    var b = new CollectionProperties("appId-1");
    b.setWebdavVisible(false); // different field, same appId → equal
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
  }

  @Test
  void notEqualForDifferentAppId() {
    var a = new CollectionProperties("appId-1");
    var b = new CollectionProperties("appId-2");
    assertNotEquals(a, b);
  }
}
