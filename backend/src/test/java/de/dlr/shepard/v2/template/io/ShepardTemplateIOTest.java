package de.dlr.shepard.v2.template.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import de.dlr.shepard.template.entities.ShepardTemplate;
import org.junit.jupiter.api.Test;

/** Unit tests for U1b2 and APISIMP-MULTI-IO-EPOCH-MS-TO-ISO applied in ShepardTemplateIO.from(). */
class ShepardTemplateIOTest {

  private ShepardTemplate buildTemplate(String createdBy) {
    ShepardTemplate t = new ShepardTemplate();
    t.setAppId("tpl-1");
    t.setName("My Template");
    t.setTemplateKind("DATAOBJECT_RECIPE");
    t.setVersion(1);
    t.setBody("{}");
    t.setCreatedBy(createdBy);
    return t;
  }

  // ────────────────────────────────────────────────────────────────────
  // APISIMP-MULTI-IO-EPOCH-MS-TO-ISO — createdAt/updatedAt as ISO 8601
  // ────────────────────────────────────────────────────────────────────

  @Test
  void createdAtIsRenderedAsIso8601() {
    ShepardTemplate t = buildTemplate("alice");
    t.setCreatedAt(1751328000000L); // 2025-07-01T00:00:00Z
    ShepardTemplateIO io = ShepardTemplateIO.from(t);
    assertEquals("2025-07-01T00:00:00Z", io.getCreatedAt());
  }

  @Test
  void updatedAtIsRenderedAsIso8601() {
    ShepardTemplate t = buildTemplate("alice");
    t.setUpdatedAt(1751414400000L); // 2025-07-02T00:00:00Z
    ShepardTemplateIO io = ShepardTemplateIO.from(t);
    assertEquals("2025-07-02T00:00:00Z", io.getUpdatedAt());
  }

  @Test
  void createdAtIsNullWhenNotSet() {
    ShepardTemplateIO io = ShepardTemplateIO.from(buildTemplate("alice"));
    assertNull(io.getCreatedAt());
  }

  @Test
  void updatedAtIsNullWhenNotSet() {
    ShepardTemplateIO io = ShepardTemplateIO.from(buildTemplate("alice"));
    assertNull(io.getUpdatedAt());
  }

  @Test
  void plainUsernamePassesThrough() {
    ShepardTemplateIO io = ShepardTemplateIO.from(buildTemplate("alice"));
    assertEquals("alice", io.getCreatedBy());
  }

  @Test
  void uuidShapedKeycloakSubjectIsRedacted() {
    ShepardTemplateIO io = ShepardTemplateIO.from(buildTemplate("f2b5a4c3-1234-5678-abcd-1234567890ab"));
    assertEquals("f2b5a4c3…", io.getCreatedBy());
  }

  @Test
  void colonPrefixedSubjectUsesTrailingSegmentAndRedacts() {
    ShepardTemplateIO io = ShepardTemplateIO.from(buildTemplate("realm:f2b5a4c3-1234-5678-abcd-1234567890ab"));
    assertEquals("f2b5a4c3…", io.getCreatedBy());
  }

  @Test
  void nullCreatedByIsReturnedAsAnonymous() {
    ShepardTemplateIO io = ShepardTemplateIO.from(buildTemplate(null));
    assertEquals("(anonymous)", io.getCreatedBy());
  }

  // ────────────────────────────────────────────────────────────────────
  // TEMPLATE-ICONS-1 — iconKey passes through ShepardTemplateIO.from()
  // ────────────────────────────────────────────────────────────────────

  @Test
  void iconKeyIsPassedThroughWhenSet() {
    ShepardTemplate t = buildTemplate("alice");
    t.setIconKey("mdi-layers");
    ShepardTemplateIO io = ShepardTemplateIO.from(t);
    assertEquals("mdi-layers", io.getIconKey());
  }

  @Test
  void iconKeyDefaultsToNullWhenUnset() {
    ShepardTemplateIO io = ShepardTemplateIO.from(buildTemplate("alice"));
    assertNull(io.getIconKey());
  }
}
