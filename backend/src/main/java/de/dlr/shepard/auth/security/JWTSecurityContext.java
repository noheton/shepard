package de.dlr.shepard.auth.security;

import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;

/**
 * JAX-RS {@link SecurityContext} that wraps a {@link JWTPrincipal} and
 * routes {@link #isUserInRole(String)} through the principal's
 * dual-source-resolved roles (aidocs/51 §3.3 / §7).
 *
 * <p>Standard JAX-RS {@code @RolesAllowed("instance-admin")}
 * gates work via this hook — once the principal carries the role,
 * the existing JAX-RS authorization plumbing accepts the call.
 */
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
    return userPrincipal != null && userPrincipal.hasRole(role);
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
