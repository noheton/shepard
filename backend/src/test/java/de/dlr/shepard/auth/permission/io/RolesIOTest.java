package de.dlr.shepard.auth.permission.io;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class RolesIOTest {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple().forClass(RolesIO.class).verify();
  }
}
