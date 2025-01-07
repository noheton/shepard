package de.dlr.shepard.data.structureddata.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.data.structureddata.entities.StructuredDataContainer;
import java.util.Date;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class StructuredDataContainerIOTest {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple().forClass(StructuredDataContainerIO.class).verify();
  }

  @Test
  public void testConversion() {
    var user = new User("bob");
    var date = new Date();
    var update = new Date();
    var updateUser = new User("claus");

    var obj = new StructuredDataContainer(1L);
    obj.setCreatedAt(date);
    obj.setCreatedBy(user);
    obj.setName("name");
    obj.setMongoId("mongoid");
    obj.setUpdatedAt(update);
    obj.setUpdatedBy(updateUser);

    var converted = new StructuredDataContainerIO(obj);
    assertEquals(converted.getCreatedAt(), date);
    assertEquals("bob", converted.getCreatedBy());
    assertEquals(1L, converted.getId());
    assertEquals("name", converted.getName());
    assertEquals("mongoid", converted.getOid());
    assertEquals(update, converted.getUpdatedAt());
    assertEquals("claus", converted.getUpdatedBy());
  }
}
