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
 *
 * <p>F4 — {@link #iat} is the JWT {@code iat} (issued-at) claim in
 * seconds-since-epoch. It is included as a 4th dimension in the
 * permissions-cache key so that a new session (after a role change,
 * group-membership flip, or token rotation) always misses the cache
 * rather than serving a stale allow/deny from a previous session.
 * API-key-authenticated principals carry {@code iat = 0L} (no JWT
 * {@code iat} claim).
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

  /**
   * F4 — JWT {@code iat} claim (seconds-since-epoch). {@code 0L} for
   * API-key principals that have no {@code iat} claim.
   */
  private long iat;

  public JWTPrincipal(String username, String keyId) {
    this.audience = null;
    this.issuedFor = null;
    this.username = username;
    this.keyId = keyId;
    this.roles = new String[0];
    this.iat = 0L;
  }

  /**
   * Convenience constructor used by the filter when populating the
   * roles list from the dual-source resolution. Iterable is
   * deduplicated in insertion order; nulls are filtered out.
   * {@code iat} defaults to {@code 0L} — use the 7-argument form when
   * the JWT carries an {@code iat} claim.
   */
  public JWTPrincipal(
    String audience,
    String issuedFor,
    String username,
    String keyId,
    Iterable<String> roles
  ) {
    this(audience, issuedFor, username, keyId, roles, 0L);
  }

  /**
   * Full constructor carrying all fields including the JWT {@code iat}
   * claim (F4). Used by {@code JWTFilter.parsePrincipalFromAccessToken}
   * when the token carries an {@code iat} claim.
   */
  public JWTPrincipal(
    String audience,
    String issuedFor,
    String username,
    String keyId,
    Iterable<String> roles,
    long iat
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
    this.iat = iat;
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
