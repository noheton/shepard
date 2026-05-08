package de.dlr.shepard.auth.role.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class RoleTest {

  @Test
  void uniqueIdIsTheRoleName() {
    var r = new Role("instance-admin", "Instance Admin");
    assertEquals("instance-admin", r.getUniqueId());
  }

  @Test
  void equalsByName() {
    var a = new Role("instance-admin", "Instance Admin");
    var b = new Role("instance-admin", "Different Display");
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
  }

  @Test
  void notEqualForDifferentName() {
    var a = new Role("instance-admin", "Instance Admin");
    var b = new Role("viewer", "Viewer");
    assertNotEquals(a, b);
  }
}
