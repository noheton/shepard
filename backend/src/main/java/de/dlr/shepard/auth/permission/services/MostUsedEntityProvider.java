package de.dlr.shepard.auth.permission.services;

import de.dlr.shepard.common.util.AccessType;
import java.util.List;

/**
 * Provides the (entityId, accessType, userSub) triples used by
 * {@link PermissionsCacheWarmer} to pre-populate the permission cache at
 * startup. Implementations must be safe to call before any HTTP traffic.
 */
public interface MostUsedEntityProvider {
  record EntityAccessTriple(long entityId, AccessType accessType, String userSub) {}

  List<EntityAccessTriple> findMostUsedEntities(int maxEntries);
}
