package de.dlr.shepard.neo4Core.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.entities.TimeseriesContainer;
import de.dlr.shepard.neo4Core.entities.User;
import java.util.Date;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class TimeseriesContainerIOTest extends BaseTestCase {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple().forClass(TimeseriesContainerIO.class).verify();
  }

  @Test
  public void testConversion() {
    var user = new User("bob");
    var date = new Date();
    var update = new Date();
    var updateUser = new User("claus");

    var obj = new TimeseriesContainer(1L);
    obj.setCreatedAt(date);
    obj.setCreatedBy(user);
    obj.setDatabase("Database");
    obj.setName("name");
    obj.setUpdatedAt(update);
    obj.setUpdatedBy(updateUser);

    var converted = new TimeseriesContainerIO(obj);
    assertEquals(date, converted.getCreatedAt());
    assertEquals("bob", converted.getCreatedBy());
    assertEquals("Database", converted.getDatabase());
    assertEquals(1L, converted.getId());
    assertEquals("name", converted.getName());
    assertEquals(update, converted.getUpdatedAt());
    assertEquals("claus", converted.getUpdatedBy());
  }
}
