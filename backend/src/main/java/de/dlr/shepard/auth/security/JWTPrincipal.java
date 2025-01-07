package de.dlr.shepard.auth.security;

import java.security.Principal;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
@Getter
public class JWTPrincipal implements Principal {

  private String audience;
  private String issuedFor;
  private String username;
  private String keyId;
  private String[] roles;

  public JWTPrincipal(String username, String keyId) {
    this.audience = null;
    this.issuedFor = null;
    this.username = username;
    this.keyId = keyId;
    this.roles = new String[0];
  }

  @Override
  public String getName() {
    return username;
  }
}
