package de.dlr.shepard.neo4Core.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.entities.Version;
import java.util.Date;
import java.util.UUID;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class VersionIOTest extends BaseTestCase {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple().forClass(VersionIO.class).verify();
  }

  @Test
  public void testConversionWithPredecessor() {
    Version version = new Version();
    Date date = new Date(100L);
    User user = new User("david");
    String description = "description";
    String name = "name";
    Version predecessor = new Version(new UUID(1L, 2L));
    UUID uid = new UUID(2L, 3L);
    version.setCreatedAt(date);
    version.setCreatedBy(user);
    version.setDescription(description);
    version.setName(name);
    version.setPredecessor(predecessor);
    version.setUid(uid);
    VersionIO versionIO = new VersionIO(version);
    assertEquals(versionIO.getCreatedAt(), date);
    assertEquals(versionIO.getCreatedBy(), user.getUsername());
    assertEquals(versionIO.getDescription(), description);
    assertEquals(versionIO.getName(), name);
    assertEquals(versionIO.getPredecessorUUID(), predecessor.getUid());
    assertEquals(versionIO.getUid(), uid);
  }

  @Test
  public void testConversionWithoutPredecessor() {
    Version version = new Version();
    Date date = new Date(100L);
    User user = new User("david");
    String description = "description";
    String name = "name";
    UUID uid = new UUID(2L, 3L);
    version.setCreatedAt(date);
    version.setCreatedBy(user);
    version.setDescription(description);
    version.setName(name);
    version.setUid(uid);
    VersionIO versionIO = new VersionIO(version);
    assertEquals(versionIO.getCreatedAt(), date);
    assertEquals(versionIO.getCreatedBy(), user.getUsername());
    assertEquals(versionIO.getDescription(), description);
    assertEquals(versionIO.getName(), name);
    assertEquals(versionIO.getPredecessorUUID(), null);
    assertEquals(versionIO.getUid(), uid);
  }
}
