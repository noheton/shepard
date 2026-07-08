package de.dlr.shepard.v2.admin.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class PermissionAuditEntryIOTest {

  @Test
  void allArgsConstructor_setsAllFields() {
    var io = new PermissionAuditEntryIO(42L, "uuid-7", List.of("Collection", "BasicEntity"), "thing");
    assertEquals(42L, io.getNeo4jNodeId());
    assertEquals("uuid-7", io.getAppId());
    assertEquals(List.of("Collection", "BasicEntity"), io.getLabels());
    assertEquals("thing", io.getName());
  }

  @Test
  void noArgsConstructor_yieldsDefaults() {
    var io = new PermissionAuditEntryIO();
    org.junit.jupiter.api.Assertions.assertNull(io.getNeo4jNodeId());
    org.junit.jupiter.api.Assertions.assertNull(io.getAppId());
    org.junit.jupiter.api.Assertions.assertNull(io.getLabels());
    org.junit.jupiter.api.Assertions.assertNull(io.getName());
  }

  @Test
  void neo4jNodeId_schemaAnnotation_isDeprecated() throws NoSuchFieldException {
    var field = PermissionAuditEntryIO.class.getDeclaredField("neo4jNodeId");
    var schema = field.getAnnotation(org.eclipse.microprofile.openapi.annotations.media.Schema.class);
    org.junit.jupiter.api.Assertions.assertNotNull(schema, "neo4jNodeId must have @Schema annotation");
    org.junit.jupiter.api.Assertions.assertTrue(schema.deprecated(), "neo4jNodeId @Schema must be deprecated=true (will be removed post-L2 migration)");
  }
}
