package de.dlr.shepard.v2.template.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import de.dlr.shepard.template.entities.ShepardTemplate;
import org.junit.jupiter.api.Test;

/** Unit tests for U1b2: DisplayNameResolver.redactUsername applied in ShepardTemplateIO.from(). */
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
}
