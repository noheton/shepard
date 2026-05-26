package de.dlr.shepard.auth.permission.services;

/**
 * Future seam for an external policy engine (OPA, Cedar, etc.).
 *
 * <p>The default implementation ({@link GraphPolicyDecisionPoint}) delegates to
 * {@link PermissionsService}'s Neo4j graph-based checks. A future OPA or Cedar
 * adapter implements this interface and replaces the CDI bean — no call-site
 * changes needed.
 *
 * <p><strong>F6 seam:</strong> interface added here; F1 will wire the
 * {@code @Authz(action, resource)} annotation that dispatches through this
 * point, replacing the path-segment switch in {@link PermissionsService#isAllowed}.
 */
public interface PolicyDecisionPoint {

  /**
   * Returns {@code true} if the given subject (username) may perform the given
   * action on the given resource identified by its {@code entityAppId}.
   *
   * <p>The {@code action} string corresponds to the name of an
   * {@link de.dlr.shepard.common.util.AccessType} value (e.g. {@code "Read"},
   * {@code "Write"}, {@code "Manage"}). Callers must pass a value that maps
   * cleanly to {@code AccessType.valueOf(action)}.
   *
   * @param username    the authenticated username
   * @param action      the access type name (must match an {@link de.dlr.shepard.common.util.AccessType})
   * @param entityAppId the target entity's {@code appId} (UUID v7 canonical form)
   * @return {@code true} if the access is permitted; {@code false} otherwise
   */
  boolean isAllowed(String username, String action, String entityAppId);
}
