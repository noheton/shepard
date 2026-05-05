package de.dlr.shepard.auth.permission.services;

import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.common.util.AccessType;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.neo4j.ogm.model.Result;

@ApplicationScoped
public class DefaultMostUsedEntityProvider implements MostUsedEntityProvider {

  @Override
  public List<EntityAccessTriple> findMostUsedEntities(int maxEntries) {
    if (maxEntries <= 0) return List.of();

    var session = NeoConnector.getInstance().getNeo4jSession();
    if (session == null) {
      Log.debug("Neo4j session not available; skipping most-used-entity lookup");
      return List.of();
    }

    // Owner-paired lookup ensures the cached entry is for a user that is
    // actually allowed Read access on the entity (owners always pass).
    String query =
      "MATCH (e:BasicEntity)-[:has_permissions]->(:Permissions)-[:owned_by]->(u:User) " +
      "WHERE coalesce(e.deleted, false) = false AND e.updatedAt IS NOT NULL " +
      "RETURN ID(e) AS entityId, u.username AS username " +
      "ORDER BY e.updatedAt DESC " +
      "LIMIT $limit";

    Result result = session.query(query, Map.of("limit", maxEntries));
    var triples = new ArrayList<EntityAccessTriple>();
    for (Map<String, Object> row : result) {
      Object entityIdObj = row.get("entityId");
      Object usernameObj = row.get("username");
      if (entityIdObj == null || usernameObj == null) continue;
      long entityId = ((Number) entityIdObj).longValue();
      String username = usernameObj.toString();
      triples.add(new EntityAccessTriple(entityId, AccessType.Read, username));
    }
    return Collections.unmodifiableList(triples);
  }
}
