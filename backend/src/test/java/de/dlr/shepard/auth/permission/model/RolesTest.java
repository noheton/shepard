package de.dlr.shepard.auth.permission.model;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class RolesTest {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple().forClass(Roles.class).verify();
  }
}
