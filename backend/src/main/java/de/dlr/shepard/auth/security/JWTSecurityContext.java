package de.dlr.shepard.auth.security;

import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.Arrays;

public class JWTSecurityContext implements SecurityContext {

  private SecurityContext context;
  private JWTPrincipal userPrincipal;

  public JWTSecurityContext(SecurityContext context, JWTPrincipal userPrincipal) {
    this.context = context;
    this.userPrincipal = userPrincipal;
  }

  @Override
  public Principal getUserPrincipal() {
    return userPrincipal;
  }

  @Override
  public boolean isUserInRole(String role) {
    return Arrays.stream(userPrincipal.getRoles()).anyMatch(role::equals);
  }

  @Override
  public boolean isSecure() {
    return context.isSecure();
  }

  @Override
  public String getAuthenticationScheme() {
    return "Token-Based-Auth-Scheme";
  }
}
