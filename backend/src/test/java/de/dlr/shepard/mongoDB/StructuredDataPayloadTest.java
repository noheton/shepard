package de.dlr.shepard.mongoDB;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class StructuredDataPayloadTest {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple().forClass(StructuredDataPayload.class).verify();
  }
}
