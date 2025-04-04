package de.dlr.shepard.auth.security;

import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class AuthenticationContext {

  private JWTPrincipal principal;

  public String getCurrentUserName() {
    return principal.getUsername();
  }

  public void setPrincipal(JWTPrincipal principal) {
    this.principal = principal;
  }
}
