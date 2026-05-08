package de.dlr.shepard.auth.permission.daos;

import de.dlr.shepard.auth.permission.model.Permissions;
import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.common.neo4j.entities.BasicEntity;
import de.dlr.shepard.common.util.CypherQueryHelper;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@RequestScoped
public class PermissionsDAO extends GenericDAO<Permissions> {

  public Permissions findByEntityNeo4jId(long entityId) {
    // L2c read-path swap: query by appId rather than the deprecated id() function.
    // The OGM Long is translated to its appId via the request-scoped
    // EntityIdResolver; the public method signature stays long for caller-compat
    // until L2d flips the public surface.
    String appId;
    try {
      appId = entityIdResolver.resolveAppId(entityId);
    } catch (NotFoundException e) {
      // Match the prior null-return contract for "no permissions on a missing
      // entity" — callers (PermissionsService) treat null as "legacy entity
      // without permissions", which is the same observed behaviour.
      return null;
    }
    String query =
      "MATCH (e:BasicEntity {appId: $appId})-[:has_permissions]->(p:Permissions) " +
      CypherQueryHelper.getReturnPart("p");
    var permissions = findByQuery(query, Map.of("appId", appId));
    if (permissions.iterator().hasNext()) return permissions.iterator().next();
    return null;
  }

  /**
   * Resolve permissions for many entity ids in a single Cypher round-trip.
   *
   * Entities without a Permissions relation (legacy entities) are absent from the returned map,
   * matching the single-id contract where {@link #findByEntityNeo4jId(long)} returns null.
   */
  public Map<Long, Permissions> findByEntityNeo4jIds(Collection<Long> entityIds) {
    if (entityIds == null || entityIds.isEmpty()) return Collections.emptyMap();
    Set<Long> deduped = new HashSet<>(entityIds);
    // Resolve every requested OGM id to its appId; build a parallel map so we
    // can project the response back to long-keyed entries without re-querying.
    Map<String, Long> ogmIdByAppId = new LinkedHashMap<>();
    for (Long id : deduped) {
      if (id == null) continue;
      try {
        String appId = entityIdResolver.resolveAppId(id);
        ogmIdByAppId.put(appId, id);
      } catch (NotFoundException e) {
        // Skip entities that have no node — they implicitly have no
        // permissions (mirrors the single-id null-return contract).
      }
    }
    if (ogmIdByAppId.isEmpty()) return Collections.emptyMap();
    Map<String, Object> params = new HashMap<>();
    params.put("appIds", new ArrayList<>(ogmIdByAppId.keySet()));
    String query =
      "MATCH (e:BasicEntity)-[:has_permissions]->(p:Permissions) WHERE e.appId IN $appIds " +
      CypherQueryHelper.getReturnPart("p");
    Map<Long, Permissions> result = new HashMap<>();
    for (Permissions p : findByQuery(query, params)) {
      if (p.getEntities() == null) continue;
      for (BasicEntity entity : p.getEntities()) {
        Long id = entity.getId();
        if (id != null && deduped.contains(id)) {
          result.put(id, p);
        }
      }
    }
    return result;
  }

  @Override
  public Class<Permissions> getEntityType() {
    return Permissions.class;
  }
}
