package de.dlr.shepard.auth.permission.daos;

import de.dlr.shepard.auth.permission.entities.Permissions;
import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.CypherQueryHelper;
import jakarta.enterprise.context.RequestScoped;
import java.util.Collections;

@RequestScoped
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
    Permissions ret = null;
    if (permissions.iterator().hasNext()) ret = permissions.iterator().next();
    return ret;
  }

  public Permissions findByCollectionShepardId(long collectionShepardId) {
    String query =
      String.format(
        "MATCH (c:Collection)-[:has_permissions]->(p:Permissions) WHERE c." + Constants.SHEPARD_ID + " = %d ",
        collectionShepardId
      ) +
      CypherQueryHelper.getReturnPart("p");
    var permissions = findByQuery(query, Collections.emptyMap());
    Permissions ret = null;
    if (permissions.iterator().hasNext()) ret = permissions.iterator().next();
    return ret;
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
