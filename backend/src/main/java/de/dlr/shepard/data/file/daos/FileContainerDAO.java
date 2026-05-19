package de.dlr.shepard.data.file.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.common.util.CypherQueryHelper;
import de.dlr.shepard.common.util.CypherQueryHelper.Neighborhood;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.data.file.entities.FileContainer;
import jakarta.enterprise.context.RequestScoped;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RequestScoped
public class FileContainerDAO extends GenericDAO<FileContainer> {

  public List<FileContainer> findAllFileContainers(QueryParamHelper params, String username) {
    String query;
    Map<String, Object> paramsMap = new HashMap<>();
    paramsMap.put("name", params.getName());
    if (params.hasPagination()) {
      paramsMap.put("offset", params.getPagination().getOffset());
      paramsMap.put("size", params.getPagination().getSize());
    }
    query = "MATCH %s WHERE %s WITH c".formatted(
        CypherQueryHelper.getObjectPart("c", "FileContainer", params.hasName()),
        CypherQueryHelper.getReadableByQuery("c", username)
      );
    if (params.hasOrderByAttribute()) {
      query += " " + CypherQueryHelper.getOrderByPart("c", params.getOrderByAttribute(), params.getOrderDesc());
    }
    if (params.hasPagination()) {
      query += " " + CypherQueryHelper.getPaginationPart();
    }
    query += " " + CypherQueryHelper.getReturnPart("c", Neighborhood.ESSENTIAL);
    var result = new ArrayList<FileContainer>();
    for (var container : findByQuery(query, paramsMap)) {
      if (matchName(container, params.getName())) {
        result.add(container);
      }
    }
    return result;
  }

  private boolean matchName(FileContainer container, String name) {
    return name == null || container.getName().equalsIgnoreCase(name);
  }

  public Optional<FileContainer> findByAppId(String appId) {
    String query = "MATCH (c:FileContainer {appId: $appId}) " + CypherQueryHelper.getReturnPart("c");
    var iter = findByQuery(query, Map.of("appId", appId));
    var it = iter.iterator();
    return it.hasNext() ? Optional.of(it.next()) : Optional.empty();
  }

  /**
   * CC1b — find all DataObjects that reference this FileContainer via a
   * FileBundleReference (the only reference type that links to a FileContainer).
   * SingletonFileReference stores files in a shared Mongo namespace without a
   * container node, so it never appears in this traversal.
   *
   * <p>The relationship path is:
   * {@code DataObject -[:has_reference]-> FileBundleReference -[:is_in_container]-> FileContainer}
   *
   * @param containerAppId the appId of the FileContainer
   * @return distinct non-deleted DataObjects linked to this container
   */
  public List<DataObject> findLinkedDataObjectsByContainerAppId(String containerAppId) {
    String query =
      "MATCH (do:DataObject)-[:has_reference]->()-[:is_in_container]->(c:FileContainer) " +
      "WHERE c.appId = $containerAppId " +
      "  AND (do.deleted IS NULL OR do.deleted = false) " +
      "RETURN DISTINCT id(do) AS neo4jId";
    var result = new ArrayList<DataObject>();
    for (var row : session.query(query, Map.of("containerAppId", containerAppId))) {
      Long neo4jId = (Long) row.get("neo4jId");
      if (neo4jId == null) continue;
      DataObject loaded = session.load(DataObject.class, neo4jId, DEPTH_ENTITY);
      if (loaded != null) result.add(loaded);
    }
    return result;
  }

  @Override
  public Class<FileContainer> getEntityType() {
    return FileContainer.class;
  }
}
