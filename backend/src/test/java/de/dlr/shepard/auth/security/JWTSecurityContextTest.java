package de.dlr.shepard.auth.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.Test;

public class JWTSecurityContextTest {

  private List<String> roles = List.of("role1", "role2");
  private JWTPrincipal principal = new JWTPrincipal("MyAudience", "MyIssuedFor", "MyUsername", "MyKeyId", roles);

  private SecurityContext sc = new SecurityContext() {
    @Override
    public boolean isUserInRole(String role) {
      return false;
    }

    @Override
    public boolean isSecure() {
      return false;
    }

    @Override
    public Principal getUserPrincipal() {
      return null;
    }

    @Override
    public String getAuthenticationScheme() {
      return null;
    }
  };

  @Test
  public void testGetUserPrincipal() {
    var context = new JWTSecurityContext(sc, principal);
    assertEquals(principal, context.getUserPrincipal());
  }

  @Test
  public void testUserInRole() {
    var context = new JWTSecurityContext(sc, principal);
    assertTrue(context.isUserInRole("role1"));
    assertFalse(context.isUserInRole("role5"));
  }

  @Test
  public void testIsSecure() {
    var context = new JWTSecurityContext(sc, principal);
    assertEquals(sc.isSecure(), context.isSecure());
  }

  @Test
  public void testAuthenticationScheme() {
    var context = new JWTSecurityContext(sc, principal);
    assertEquals("Token-Based-Auth-Scheme", context.getAuthenticationScheme());
  }
}
