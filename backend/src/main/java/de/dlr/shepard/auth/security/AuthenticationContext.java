package de.dlr.shepard.auth.security;

import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class AuthenticationContext {

  private JWTPrincipal principal;

  public String getCurrentUserName() {
    return principal == null ? null : principal.getUsername();
  }

  /**
   * BUG-USER-PROVISION-EMAIL-COLLISION — returns the {@code email} claim from
   * the JWT, or {@code null} when absent. Used as a fallback identity in
   * {@link de.dlr.shepard.auth.users.services.UserService#getCurrentUser()} when
   * the Neo4j :User lookup by username fails.
   */
  public String getCurrentUserEmail() {
    return principal == null ? null : principal.getEmail();
  }

  /** A0 — exposes the full principal so callers can read roles. */
  public JWTPrincipal getPrincipal() {
    return principal;
  }

  public void setPrincipal(JWTPrincipal principal) {
    this.principal = principal;
  }
}
