package de.dlr.shepard.template.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShepardTemplateTest {

  @Test
  void uniqueIdIsTheAppId() {
    var t = new ShepardTemplate();
    t.setAppId("018f9c5a-7e26-7000-a000-000000000050");
    assertEquals("018f9c5a-7e26-7000-a000-000000000050", t.getUniqueId());
  }

  @Test
  void constructorPopulatesCoreFields() {
    var t = new ShepardTemplate("LUMEN run recipe", "EXPERIMENT_RECIPE", "{\"foo\":1}");
    assertEquals("LUMEN run recipe", t.getName());
    assertEquals("EXPERIMENT_RECIPE", t.getTemplateKind());
    assertEquals("{\"foo\":1}", t.getBody());
    assertEquals(1, t.getVersion());
    assertFalse(t.isRetired());
  }

  @Test
  void retiredDefaultsFalse() {
    var t = new ShepardTemplate();
    assertFalse(t.isRetired());
  }

  @Test
  void tagsDefaultsEmptyList() {
    var t = new ShepardTemplate();
    assertNotNull(t.getTags());
    assertTrue(t.getTags().isEmpty());
  }

  private static void assertNotNull(Object o) {
    org.junit.jupiter.api.Assertions.assertNotNull(o);
  }

  @Test
  void equalsByAppId() {
    var a = new ShepardTemplate();
    a.setAppId("uuid-1");
    a.setName("first");
    var b = new ShepardTemplate();
    b.setAppId("uuid-1");
    b.setName("second"); // different field, same appId → equal
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
  }

  @Test
  void notEqualForDifferentAppId() {
    var a = new ShepardTemplate();
    a.setAppId("uuid-1");
    var b = new ShepardTemplate();
    b.setAppId("uuid-2");
    assertNotEquals(a, b);
  }

  @Test
  void noArgsConstructorYieldsBlank() {
    var t = new ShepardTemplate();
    assertNull(t.getName());
    assertNull(t.getTemplateKind());
    assertNull(t.getVersion());
    assertFalse(t.isRetired());
  }

  @Test
  void canFlipRetired() {
    var t = new ShepardTemplate();
    t.setRetired(true);
    assertTrue(t.isRetired());
  }
}
