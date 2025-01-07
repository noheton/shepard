package de.dlr.shepard.common.neo4j.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.common.util.HasId;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class HasIdIOTest {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple().forClass(HasIdIO.class).verify();
  }

  @Test
  public void testConversion() {
    HasId hasId = new HasId() {
      @Override
      public String getUniqueId() {
        return "unique_id";
      }
    };

    var converted = new HasIdIO(hasId);
    assertEquals(hasId.getUniqueId(), converted.getUniqueId());
  }
}
