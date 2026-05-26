package de.dlr.shepard.auth.permission.services;

import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Default CDI implementation of {@link PolicyDecisionPoint} that delegates to
 * Shepard's existing Neo4j graph-based permission checks.
 *
 * <p>Routing logic:
 * <ol>
 *   <li>Attempt to resolve {@code entityAppId} → Neo4j id via {@link EntityIdResolver}.
 *       If found, delegate to {@link PermissionsService#isAccessTypeAllowedForUser(long, AccessType, String)}.
 *   <li>If the resolver throws (entity not found or session unavailable), fall back to
 *       {@link PermissionsService#isAccessAllowedForDataObjectAppId} — which handles the
 *       common case of a DataObject whose permissions are inherited from its parent Collection.
 *   <li>Return {@code false} on any remaining error (fail-closed, matching F5 policy).
 * </ol>
 *
 * <p>F1 will introduce {@code @Authz(action, resource)} annotations that call this
 * bean directly, eliminating the path-segment switch in
 * {@link PermissionsService#isAllowed}. Until then this class is wired but unused
 * on the hot path.
 */
@ApplicationScoped
public class GraphPolicyDecisionPoint implements PolicyDecisionPoint {

  @Inject
  PermissionsService permissionsService;

  @Inject
  EntityIdResolver entityIdResolver;

  /**
   * {@inheritDoc}
   *
   * <p>Translates {@code action} → {@link AccessType} via {@link AccessType#valueOf(String)}.
   * Passes {@code false} (fail-closed) if {@code action} does not map to a known value.
   */
  @Override
  public boolean isAllowed(String username, String action, String entityAppId) {
    if (username == null || action == null || entityAppId == null) return false;

    AccessType accessType;
    try {
      accessType = AccessType.valueOf(action);
    } catch (IllegalArgumentException e) {
      Log.warnf("F6/PolicyDecisionPoint: unknown action '%s'; failing closed", action);
      return false;
    }

    // Fast path: try to resolve the appId → Neo4j id and delegate to the
    // standard permission check which covers all entity kinds with Permissions nodes.
    try {
      long entityId = entityIdResolver.resolveLong(entityAppId);
      return permissionsService.isAccessTypeAllowedForUser(entityId, accessType, username);
    } catch (jakarta.ws.rs.NotFoundException ignored) {
      // Entity not found via direct lookup — may be a DataObject (which inherits
      // permissions from its parent Collection and has no own Permissions node).
    } catch (RuntimeException e) {
      Log.warnf(e, "F6/PolicyDecisionPoint: entityId resolution failed for appId=%s; trying DataObject walk", entityAppId);
    }

    // Fallback: DataObject permission walk (appId → parent Collection → check).
    try {
      return permissionsService.isAccessAllowedForDataObjectAppId(entityAppId, accessType, username);
    } catch (RuntimeException e) {
      Log.warnf(e, "F6/PolicyDecisionPoint: DataObject fallback failed for appId=%s; failing closed", entityAppId);
      return false;
    }
  }
}
