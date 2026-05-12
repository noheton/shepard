package de.dlr.shepard.v2.admin.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class PermissionAuditEntryIOTest {

  @Test
  void allArgsConstructor_setsAllFields() {
    var io = new PermissionAuditEntryIO(42L, "uuid-7", List.of("Collection", "BasicEntity"), "thing");
    assertEquals(42L, io.getId());
    assertEquals("uuid-7", io.getAppId());
    assertEquals(List.of("Collection", "BasicEntity"), io.getLabels());
    assertEquals("thing", io.getName());
  }

  @Test
  void noArgsConstructor_yieldsDefaults() {
    var io = new PermissionAuditEntryIO();
    assertEquals(0L, io.getId());
    org.junit.jupiter.api.Assertions.assertNull(io.getAppId());
    org.junit.jupiter.api.Assertions.assertNull(io.getLabels());
    org.junit.jupiter.api.Assertions.assertNull(io.getName());
  }
}
