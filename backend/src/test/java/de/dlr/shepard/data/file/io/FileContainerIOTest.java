package de.dlr.shepard.data.file.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.data.file.entities.FileContainer;
import java.util.Date;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class FileContainerIOTest {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple().forClass(FileContainerIO.class).withIgnoredFields("revision").verify();
  }

  @Test
  public void testConversion() {
    var user = new User("bob");
    var date = new Date();
    var update = new Date();
    var updateUser = new User("claus");

    var obj = new FileContainer(1L);
    obj.setCreatedAt(date);
    obj.setCreatedBy(user);
    obj.setName("name");
    obj.setMongoId("oid");
    obj.setUpdatedAt(update);
    obj.setUpdatedBy(updateUser);

    var converted = new FileContainerIO(obj);
    assertEquals(date, converted.getCreatedAt());
    assertEquals("bob", converted.getCreatedBy());
    assertEquals(1L, converted.getId());
    assertEquals("name", converted.getName());
    assertEquals("oid", converted.getOid());
    assertEquals(update, converted.getUpdatedAt());
    assertEquals("claus", converted.getUpdatedBy());
  }
}
