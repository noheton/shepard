package de.dlr.shepard.context.references.basicreference.daos;

import de.dlr.shepard.common.util.CypherQueryHelper;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.context.version.daos.VersionableEntityDAO;
import jakarta.enterprise.context.RequestScoped;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

@RequestScoped
public class BasicReferenceDAO extends VersionableEntityDAO<BasicReference> {

  @Override
  public Class<BasicReference> getEntityType() {
    return BasicReference.class;
  }

  /**
   * Searches the database for references.
   *
   * @param dataObjectId identifies the dataObject
   * @param params       encapsulates possible parameters
   * @return a List of references
   */
  public List<BasicReference> findByDataObjectNeo4jId(long dataObjectId, QueryParamHelper params) {
    String query;
    Map<String, Object> paramsMap = new HashMap<>();
    paramsMap.put("name", params.getName());
    if (params.hasPagination()) {
      paramsMap.put("offset", params.getPagination().getOffset());
      paramsMap.put("size", params.getPagination().getSize());
    }
    query = String.format(
      "MATCH (d:DataObject)-[hr:has_reference]->%s WHERE ID(d)=%d WITH r",
      CypherQueryHelper.getObjectPart("r", "BasicReference", params.hasName()),
      dataObjectId
    );
    if (params.hasOrderByAttribute()) {
      query += " " + CypherQueryHelper.getOrderByPart("r", params.getOrderByAttribute(), params.getOrderDesc());
    }
    if (params.hasPagination()) {
      query += " " + CypherQueryHelper.getPaginationPart();
    }
    query += " " + CypherQueryHelper.getReturnPart("r");
    var result = new ArrayList<BasicReference>();
    for (var ref : findByQuery(query, paramsMap)) {
      if (matchDataObject(ref, dataObjectId) && matchName(ref, params.getName())) {
        result.add(ref);
      }
    }
    return result;
  }

  /**
   * Searches the database for references.
   *
   * @param dataObjectShepardId identifies the dataObject
   * @param params              encapsulates possible parameters
   * @return a List of references
   */
  public List<BasicReference> findByDataObjectShepardId(long dataObjectShepardId, QueryParamHelper params) {
    String query;
    Map<String, Object> paramsMap = new HashMap<>();
    paramsMap.put("name", params.getName());
    if (params.hasPagination()) {
      paramsMap.put("offset", params.getPagination().getOffset());
      paramsMap.put("size", params.getPagination().getSize());
    }
    query = String.format(
      "MATCH (d:DataObject)-[hr:has_reference]->%s WHERE d.shepardId=%d WITH br",
      CypherQueryHelper.getObjectPart("br", "BasicReference", params.hasName()),
      dataObjectShepardId
    );
    if (params.hasOrderByAttribute()) {
      query += " " + CypherQueryHelper.getOrderByPart("br", params.getOrderByAttribute(), params.getOrderDesc());
    }
    if (params.hasPagination()) {
      query += " " + CypherQueryHelper.getPaginationPart();
    }
    query += " " + CypherQueryHelper.getReturnPart("br");
    var result = new ArrayList<BasicReference>();
    for (var ref : findByQuery(query, paramsMap)) {
      if (matchDataObjectByShepardId(ref, dataObjectShepardId) && matchName(ref, params.getName())) {
        result.add(ref);
      }
    }
    return result;
  }

  private boolean matchDataObject(BasicReference ref, long dataObjectId) {
    return ref.getDataObject() != null && ref.getDataObject().getId().equals(dataObjectId);
  }

  private boolean matchDataObjectByShepardId(BasicReference ref, long dataObjectShepardId) {
    return ref.getDataObject() != null && ref.getDataObject().getShepardId().equals(dataObjectShepardId);
  }

  private boolean matchName(BasicReference ref, String name) {
    return name == null || ref.getName().equalsIgnoreCase(name);
  }

  public List<BasicReference> getBasicReferencesByQuery(String query) {
    var queryResult = findByQuery(query, Collections.emptyMap());
    List<BasicReference> ret = StreamSupport.stream(queryResult.spliterator(), false).toList();
    return ret;
  }
}
