package de.dlr.shepard.auth.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.auth.security.JWTPrincipal;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class JWTPrincipalTest {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple().forClass(JWTPrincipal.class).verify();
  }

  @Test
  public void getNameTest() {
    var obj = new JWTPrincipal("bob", "key");
    assertEquals("bob", obj.getName());
  }
}
