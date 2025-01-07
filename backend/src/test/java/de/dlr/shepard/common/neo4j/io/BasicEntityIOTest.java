package de.dlr.shepard.common.neo4j.io;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.neo4j.entities.BasicEntity;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.context.version.entities.VersionableEntity;
import de.dlr.shepard.data.file.entities.FileContainer;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;

public class BasicEntityIOTest {

  private static class EntityIO extends BasicEntityIO {

    public EntityIO(long id) {
      this.setId(id);
    }

    public EntityIO(BasicEntity entity) {
      super(entity);
    }

    public EntityIO(VersionableEntity entity) {
      super(entity);
    }
  }

  @Test
  public void testConversion() {
    var date = new Date();
    var user = new User("bob");
    var update = new Date();
    var updateUser = new User("claus");

    var obj = new FileContainer(1L);
    obj.setCreatedAt(date);
    obj.setCreatedBy(user);
    obj.setUpdatedAt(update);
    obj.setUpdatedBy(updateUser);
    obj.setName("test");

    var converted = new EntityIO(obj);
    assertEquals(obj.getId(), converted.getId());
    assertEquals(obj.getCreatedAt(), converted.getCreatedAt());
    assertEquals("bob", converted.getCreatedBy());
    assertEquals(obj.getUpdatedAt(), converted.getUpdatedAt());
    assertEquals("claus", converted.getUpdatedBy());
    assertEquals("test", converted.getName());
  }

  @Test
  public void testConversionVersionable() {
    var date = new Date();
    var user = new User("bob");
    var update = new Date();
    var updateUser = new User("claus");

    var obj = new BasicReference(1L);
    obj.setShepardId(2L);
    obj.setCreatedAt(date);
    obj.setCreatedBy(user);
    obj.setName("MyName");
    obj.setUpdatedAt(update);
    obj.setUpdatedBy(updateUser);

    var converted = new EntityIO(obj);
    assertEquals(obj.getShepardId(), converted.getId());
    assertEquals(obj.getCreatedAt(), converted.getCreatedAt());
    assertEquals(obj.getCreatedBy().getUsername(), converted.getCreatedBy());
    assertEquals(obj.getUpdatedAt(), converted.getUpdatedAt());
    assertEquals(obj.getUpdatedBy().getUsername(), converted.getUpdatedBy());
    assertEquals(obj.getName(), converted.getName());
  }

  @Test
  public void testConversion_userNull() {
    var obj = new FileContainer(1L);

    var converted = new EntityIO(obj);
    assertEquals(obj.getId(), converted.getId());
    assertNull(converted.getCreatedBy());
    assertNull(converted.getUpdatedBy());
  }

  @Test
  public void extractIdsTest() {
    var input = List.of(new Collection(2L), new Collection(5L));
    var actual = BasicEntityIO.extractIds(input);

    assertArrayEquals(new long[] { 2, 5 }, actual);
  }

  @Test
  public void extractShepardIdsTest() {
    var col = new Collection(1L);
    col.setShepardId(2L);
    var input = List.of(col);
    var actual = BasicEntityIO.extractShepardIds(input);

    assertArrayEquals(new long[] { 2 }, actual);
  }

  @Test
  public void getUniqueIdTest() {
    var entity = new EntityIO(2L);
    var actual = entity.getUniqueId();

    assertEquals("2", actual);
  }
}
