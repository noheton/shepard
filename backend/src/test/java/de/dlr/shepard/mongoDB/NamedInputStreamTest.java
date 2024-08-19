package de.dlr.shepard.mongoDB;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class NamedInputStreamTest {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple().forClass(NamedInputStream.class).verify();
  }
}
