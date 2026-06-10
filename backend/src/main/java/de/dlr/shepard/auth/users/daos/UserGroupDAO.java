package de.dlr.shepard.auth.users.daos;

import de.dlr.shepard.auth.users.entities.UserGroup;
import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.common.util.CypherQueryHelper;
import de.dlr.shepard.common.util.QueryParamHelper;
import jakarta.enterprise.context.RequestScoped;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

@RequestScoped
public class UserGroupDAO extends GenericDAO<UserGroup> {

  @Override
  public Class<UserGroup> getEntityType() {
    return UserGroup.class;
  }

  /** V2-SWEEP-002 — look up a UserGroup by its UUID v7 appId. Returns {@code null} when not found or deleted. */
  public UserGroup findByAppId(String appId) {
    if (appId == null || appId.isBlank()) return null;
    String cypher = "MATCH (g:UserGroup {appId: $appId}) WHERE NOT g.deleted RETURN g";
    var hits = session.query(UserGroup.class, cypher, Map.of("appId", appId));
    var stub = StreamSupport.stream(hits.spliterator(), false).findFirst().orElse(null);
    if (stub == null) return null;
    return session.load(UserGroup.class, stub.getId(), DEPTH_ENTITY);
  }

  public List<UserGroup> findAllUserGroups(QueryParamHelper params, String username) {
    Map<String, Object> paramsMap = new HashMap<>();
    if (params.hasPagination()) {
      paramsMap.put("offset", params.getPagination().getOffset());
      paramsMap.put("size", params.getPagination().getSize());
    }
    var query =
      "MATCH %s WHERE %s WITH ug".formatted(
          CypherQueryHelper.getObjectPart("ug", "UserGroup", false),
          CypherQueryHelper.getReadableByQuery("ug", username)
        );
    if (params.hasOrderByAttribute()) {
      query += " " + CypherQueryHelper.getOrderByPart("ug", params.getOrderByAttribute(), params.getOrderDesc());
    }
    if (params.hasPagination()) {
      query += " " + CypherQueryHelper.getPaginationPart();
    }
    query += " " + CypherQueryHelper.getReturnPart("ug");
    var result = new ArrayList<UserGroup>();
    for (var userGroup : findByQuery(query, paramsMap)) {
      result.add(userGroup);
    }
    return result;
  }
}
