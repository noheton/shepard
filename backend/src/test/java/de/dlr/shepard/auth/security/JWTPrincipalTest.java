package de.dlr.shepard.auth.security;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
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

  // A0 — JWTPrincipal.roles dedup + filter contract.

  @Test
  public void rolesIterableConstructor_dedupsAndPreservesOrder() {
    var p = new JWTPrincipal(
      "aud",
      "azp",
      "alice",
      "kid",
      List.of("instance-admin", "instance-admin", "viewer", " ", "auditor")
    );
    assertArrayEquals(new String[] { "instance-admin", "viewer", "auditor" }, p.getRoles());
  }

  @Test
  public void hasRole_caseSensitive() {
    var p = new JWTPrincipal("a", "z", "alice", "k", List.of("instance-admin"));
    assertTrue(p.hasRole("instance-admin"));
    assertFalse(p.hasRole("Instance-Admin"));
    assertFalse(p.hasRole("admin"));
  }

  @Test
  public void rolesIterableNull_yieldsEmptyArray() {
    var p = new JWTPrincipal("a", "z", "alice", "k", (Iterable<String>) null);
    assertArrayEquals(new String[0], p.getRoles());
  }

  @Test
  public void apiKeyConstructor_yieldsEmptyRoles() {
    var p = new JWTPrincipal("alice", "kid");
    assertEquals(0, p.getRoles().length);
    assertFalse(p.hasRole("anything"));
  }
}
