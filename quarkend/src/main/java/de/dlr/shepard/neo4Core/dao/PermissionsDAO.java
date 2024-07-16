package de.dlr.shepard.neo4Core.dao;

import de.dlr.shepard.neo4Core.entities.Permissions;
import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.CypherQueryHelper;
import java.util.Collections;

public class PermissionsDAO extends GenericDAO<Permissions> {

  public Permissions findByEntityNeo4jId(long entityId) {
    String query =
      String.format("MATCH (e)-[:has_permissions]->(p:Permissions) WHERE ID(e) = %d ", entityId) +
      CypherQueryHelper.getReturnPart("p");
    var permissions = findByQuery(query, Collections.emptyMap());
    if (permissions.iterator().hasNext()) return permissions.iterator().next();
    return null;
  }

  public Permissions findByEntityShepardId(long entityShepardId) {
    String query =
      String.format(
        "MATCH (e)-[:has_permissions]->(p:Permissions) WHERE e." + Constants.SHEPARD_ID + " = %d ",
        entityShepardId
      ) +
      CypherQueryHelper.getReturnPart("p");
    var permissions = findByQuery(query, Collections.emptyMap());
    if (permissions.iterator().hasNext()) return permissions.iterator().next();
    return null;
  }

  public Permissions createWithEntityNeo4jId(Permissions permissions, long entityId) {
    var created = createOrUpdate(permissions);
    String query = String.format(
      "MATCH (e) WHERE ID(e) = %d MATCH (p:Permissions) WHERE ID(p) = %d CREATE path = (e)-[r:has_permissions]->(p)",
      entityId,
      created.getId()
    );
    runQuery(query, Collections.emptyMap());
    return findByNeo4jId(created.getId());
  }

  public Permissions createWithEntityShepardId(Permissions permissions, long entityShepardId) {
    var created = createOrUpdate(permissions);
    String query = String.format(
      "MATCH (e) WHERE e." +
      Constants.SHEPARD_ID +
      " = %d MATCH (p:Permissions) WHERE ID(p) = %d CREATE path = (e)-[r:has_permissions]->(p)",
      entityShepardId,
      created.getId()
    );
    runQuery(query, Collections.emptyMap());
    return findByNeo4jId(created.getId());
  }

  @Override
  public Class<Permissions> getEntityType() {
    return Permissions.class;
  }
}
