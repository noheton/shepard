package de.dlr.shepard.neo4Core.entities;

import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.BaseTestCase;
import java.util.Date;
import java.util.UUID;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class VersionTest extends BaseTestCase {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple()
      .forClass(Version.class)
      .withPrefabValues(User.class, new User("bob"), new User("claus"))
      .withPrefabValues(Version.class, new Version(new UUID(1L, 2L)), new Version(new UUID(2L, 3L)))
      .withPrefabValues(UUID.class, new UUID(1L, 2L), new UUID(2L, 3L))
      .verify();
  }

  @Test
  public void testNameDescriptionCreatedAtCreatedByPredecessor() {
    String name = "name";
    String description = "description";
    Date date = new Date(100L);
    User user = new User("bob");
    Version predecessor = new Version(new UUID(1L, 2L));
    Version version = new Version(name, description, date, user, predecessor);
    assertEquals(version.getName(), name);
    assertEquals(version.getDescription(), description);
    assertEquals(version.getCreatedAt(), date);
    assertEquals(version.getCreatedBy(), user);
    assertEquals(version.getPredecessor(), predecessor);
  }

  @Test
  public void testNameDescriptionCreatedAtCreatedBy() {
    String name = "name";
    String description = "description";
    Date date = new Date(100L);
    User user = new User("bob");
    Version version = new Version(name, description, date, user);
    assertEquals(version.getName(), name);
    assertEquals(version.getDescription(), description);
    assertEquals(version.getCreatedAt(), date);
    assertEquals(version.getCreatedBy(), user);
    assertNull(version.getPredecessor());
  }

  @Test
  public void testUID() {
    UUID uid = new UUID(1L, 2L);
    Version version = new Version(uid);
    assertEquals(version.getUid(), uid);
  }

  @Test
  public void testUniqueId() {
    UUID uid = new UUID(1L, 2L);
    Version version = new Version(uid);
    assertEquals(version.getUniqueId(), uid.toString());
  }
}
