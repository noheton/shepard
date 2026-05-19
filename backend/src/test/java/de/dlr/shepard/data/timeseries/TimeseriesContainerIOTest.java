package de.dlr.shepard.data.timeseries;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.data.timeseries.io.TimeseriesContainerIO;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import java.util.Date;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class TimeseriesContainerIOTest {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple().forClass(TimeseriesContainerIO.class).withIgnoredFields("revision").verify();
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
    assertEquals(1L, converted.getId());
    assertEquals("name", converted.getName());
    assertEquals(update, converted.getUpdatedAt());
    assertEquals("claus", converted.getUpdatedBy());
  }
}
