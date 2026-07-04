package de.dlr.shepard.auth.security;

/**
 * ROLE-GRANT-STALE-SESSION-02 — thrown by {@link JwtTokenAuthService} when a
 * presented JWT was issued before the most recent recorded role mutation for
 * the identified user. The {@link de.dlr.shepard.common.filters.JWTFilter}
 * catches this and translates it to a structured HTTP 401 carrying
 * {@code error: "role_changed"} so the frontend can surface a specific
 * "sign out + back in to refresh roles" prompt.
 *
 * <p>Distinct from "token expired" / "token invalid" because the token itself
 * is still cryptographically valid — it just carries a stale claim set. The
 * remediation is the same as expiry (re-auth), but the user-visible cause
 * differs.
 */
public class RoleChangedSinceTokenIssuedException extends RuntimeException {

  private final long tokenIssuedAtMillis;
  private final long roleChangedAtMillis;

  public RoleChangedSinceTokenIssuedException(long tokenIssuedAtMillis, long roleChangedAtMillis) {
    super(
      "JWT issued at " +
      tokenIssuedAtMillis +
      "ms but the user's role set changed at " +
      roleChangedAtMillis +
      "ms — re-auth required"
    );
    this.tokenIssuedAtMillis = tokenIssuedAtMillis;
    this.roleChangedAtMillis = roleChangedAtMillis;
  }

  public long getTokenIssuedAtMillis() {
    return tokenIssuedAtMillis;
  }

  public long getRoleChangedAtMillis() {
    return roleChangedAtMillis;
  }
}
