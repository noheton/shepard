package de.dlr.shepard.neo4Core.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.util.RequestMethod;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class EventIOTest {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple().forClass(EventIO.class).verify();
  }

  @Test
  public void constructorTest() {
    var event = new EventIO("my url");
    event.setRequestMethod(RequestMethod.GET);

    var actual = new EventIO("my url", RequestMethod.GET);
    assertEquals(event, actual);
  }

  @Test
  public void copyConstructorTest() {
    var event = new EventIO("my url");
    event.setRequestMethod(RequestMethod.GET);

    var actual = new EventIO(event);
    assertEquals(event, actual);
  }
}
