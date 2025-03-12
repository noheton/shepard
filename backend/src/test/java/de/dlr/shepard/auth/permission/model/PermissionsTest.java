package de.dlr.shepard.auth.permission.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.entities.UserGroup;
import de.dlr.shepard.common.neo4j.entities.BasicEntity;
import de.dlr.shepard.common.util.PermissionType;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.data.semantic.entities.SemanticAnnotation;
import java.util.ArrayList;
import java.util.List;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class PermissionsTest extends BaseTestCase {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple()
      .forClass(Permissions.class)
      .withPrefabValues(User.class, new User("bob"), new User("claus"))
      .withPrefabValues(UserGroup.class, new UserGroup(1L), new UserGroup(2L))
      .withPrefabValues(BasicEntity.class, new Collection(5L), new Collection(6L))
      .withPrefabValues(SemanticAnnotation.class, new SemanticAnnotation(1L), new SemanticAnnotation(2L))
      .verify();
  }

  @Test
  public void constructorTest() {
    User owner = new User("bob");
    List<User> reader = List.of(new User("reader"));
    List<User> writer = List.of(new User("reader"));
    List<User> manager = List.of(new User("reader"));
    List<UserGroup> readerGroups = List.of(new UserGroup(1L));
    List<UserGroup> writerGroups = List.of(new UserGroup(2L));
    Permissions expected = new Permissions() {
      {
        setOwner(owner);
        setReader(reader);
        setWriter(writer);
        setReaderGroups(readerGroups);
        setWriterGroups(writerGroups);
        setManager(manager);
        setPermissionType(PermissionType.PublicReadable);
      }
    };
    Permissions actual = new Permissions(
      owner,
      reader,
      writer,
      readerGroups,
      writerGroups,
      manager,
      PermissionType.PublicReadable
    );

    assertEquals(expected, actual);
  }

  @Test
  public void entityConstructorTest() {
    var entity = new Collection(1L);
    ArrayList<BasicEntity> entities = new ArrayList<BasicEntity>();
    entities.add(entity);
    var user = new User("bob");
    var expected = new Permissions() {
      {
        setEntities(entities);
        setOwner(user);
        setPermissionType(PermissionType.Public);
      }
    };
    var actual = new Permissions(entity, user, PermissionType.Public);

    assertEquals(expected, actual);
  }

  @Test
  public void getUniqueIdTest() {
    var perm = new Permissions(5L);
    assertEquals("5", perm.getUniqueId());
  }

  @Test
  public void simpleConstructorTest() {
    var perm1 = new Permissions();
    perm1.setId(5L);
    var perm2 = new Permissions(5L);
    assertEquals(perm1, perm2);
  }
}
