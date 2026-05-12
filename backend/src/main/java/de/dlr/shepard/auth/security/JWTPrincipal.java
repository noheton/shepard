package de.dlr.shepard.auth.security;

import java.security.Principal;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * The authenticated principal exposed to the request scope by
 * {@link JWTSecurityContext}. Carries the authenticated username plus
 * the deduped set of roles the principal holds.
 *
 * <p>Roles are populated by {@code JWTFilter} from the dual-source
 * resolution shape (aidocs/51 §3.3): IdP claim AND/OR Neo4j
 * {@code :HAS_ROLE} edge, deduped. Caller code should consult
 * {@link jakarta.ws.rs.core.SecurityContext#isUserInRole(String)} —
 * which routes through {@link JWTSecurityContext#isUserInRole(String)}
 * — rather than reading {@link #getRoles()} directly.
 */
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

  /**
   * Convenience constructor used by the filter when populating the
   * roles list from the dual-source resolution. Iterable is
   * deduplicated in insertion order; nulls are filtered out.
   */
  public JWTPrincipal(
    String audience,
    String issuedFor,
    String username,
    String keyId,
    Iterable<String> roles
  ) {
    this.audience = audience;
    this.issuedFor = issuedFor;
    this.username = username;
    this.keyId = keyId;
    Set<String> dedup = new LinkedHashSet<>();
    if (roles != null) {
      for (String r : roles) {
        if (r != null && !r.isBlank()) dedup.add(r);
      }
    }
    this.roles = dedup.toArray(new String[0]);
  }

  @Override
  public String getName() {
    return username;
  }

  /**
   * @return true iff the principal holds the named role (case-sensitive).
   */
  public boolean hasRole(String role) {
    return roles != null && Arrays.stream(roles).anyMatch(r -> r.equals(role));
  }
}
