package de.dlr.shepard.mongoDB;

import de.dlr.shepard.BaseTestCase;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class NamedInputStreamTest extends BaseTestCase {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple().forClass(NamedInputStream.class).verify();
  }
}
