package de.dlr.shepard.auth.security;

import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class AuthenticationContext {

  private JWTPrincipal principal;

  public String getCurrentUserName() {
    return principal == null ? null : principal.getUsername();
  }

  /** A0 — exposes the full principal so callers can read roles. */
  public JWTPrincipal getPrincipal() {
    return principal;
  }

  public void setPrincipal(JWTPrincipal principal) {
    this.principal = principal;
  }
}
