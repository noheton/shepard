package de.dlr.shepard.auth.permission.daos;

import de.dlr.shepard.auth.permission.entities.Permissions;
import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.common.util.CypherQueryHelper;
import jakarta.enterprise.context.RequestScoped;
import java.util.Collections;

@RequestScoped
public class PermissionsDAO extends GenericDAO<Permissions> {

  public Permissions findByEntityNeo4jId(long entityId) {
    String query =
      String.format("MATCH (e:BasicEntity)-[:has_permissions]->(p:Permissions) WHERE ID(e) = %d ", entityId) +
      CypherQueryHelper.getReturnPart("p");
    var permissions = findByQuery(query, Collections.emptyMap());
    if (permissions.iterator().hasNext()) return permissions.iterator().next();
    return null;
  }

  @Override
  public Class<Permissions> getEntityType() {
    return Permissions.class;
  }
}
