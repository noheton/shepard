package de.dlr.shepard.context.collection.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class CollectionIOTest {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple().forClass(CollectionIO.class).withIgnoredFields("revision").verify();
  }

  @Test
  public void testConversion() {
    var dataObject = new DataObject(2L);
    dataObject.setShepardId(4L);
    var date = new Date();
    var user = new User("bob");
    var update = new Date();
    var updateUser = new User("claus");

    var obj = new Collection(1L);
    obj.setShepardId(2L);
    obj.setAttributes(Map.of("a", "b", "c", "1"));
    obj.setCreatedAt(date);
    obj.setCreatedBy(user);
    obj.setDataObjects(List.of(dataObject));
    obj.setDescription("My Description");
    obj.setName("MyName");
    obj.setUpdatedAt(update);
    obj.setUpdatedBy(updateUser);

    var converted = new CollectionIO(obj);
    assertEquals(obj.getShepardId(), converted.getId());
    assertEquals(obj.getAttributes(), converted.getAttributes());
    assertEquals(obj.getCreatedAt(), converted.getCreatedAt());
    assertEquals("bob", converted.getCreatedBy());
    assertEquals("[4]", Arrays.toString(converted.getDataObjectIds()));
    assertEquals(obj.getDescription(), converted.getDescription());
    assertEquals(obj.getName(), converted.getName());
    assertEquals(obj.getUpdatedAt(), converted.getUpdatedAt());
    assertEquals("claus", converted.getUpdatedBy());
  }
}
