package de.dlr.shepard.auth.permission.daos;

import de.dlr.shepard.auth.permission.model.Permissions;
import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.common.neo4j.entities.BasicEntity;
import de.dlr.shepard.common.util.CypherQueryHelper;
import jakarta.enterprise.context.RequestScoped;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@RequestScoped
public class PermissionsDAO extends GenericDAO<Permissions> {

  public Permissions findByEntityNeo4jId(long entityId) {
    // C5 fix: bind entityId as a Cypher parameter rather than concatenating
    // it into the query string. Today entityId is a Java long so the prior
    // .formatted(...) shape was structurally safe, but parametrising now
    // also keeps the call site safe under L2c when ids become UUID strings.
    String query =
      "MATCH (e:BasicEntity)-[:has_permissions]->(p:Permissions) WHERE ID(e) = $entityId " +
      CypherQueryHelper.getReturnPart("p");
    var permissions = findByQuery(query, Map.of("entityId", entityId));
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
    Map<String, Object> params = new HashMap<>();
    params.put("ids", new ArrayList<>(deduped));
    String query =
      "MATCH (e:BasicEntity)-[:has_permissions]->(p:Permissions) WHERE ID(e) IN $ids " +
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
