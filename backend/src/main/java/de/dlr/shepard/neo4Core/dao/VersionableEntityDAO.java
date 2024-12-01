package de.dlr.shepard.neo4Core.dao;

import de.dlr.shepard.util.CypherQueryHelper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public abstract class VersionableEntityDAO<T> extends GenericDAO<T> {

  protected VersionableEntityDAO() {
    super();
  }

  public T findByShepardId(Long shepardId) {
    List<T> list = findByShepardIds(List.of(shepardId), false);
    return list.isEmpty() ? null : list.get(0);
  }

  public List<T> findByShepardIds(List<Long> shepardIds) {
    return findByShepardIds(shepardIds, false);
  }

  public T findByShepardId(Long shepardId, UUID versionUID) {
    return findByShepardId(shepardId, versionUID, false);
  }

  public T findLightByShepardId(Long shepardId) {
    List<T> list = findByShepardIds(List.of(shepardId), true);
    return list.isEmpty() ? null : list.get(0);
  }

  private List<T> findByShepardIds(List<Long> shepardIds, boolean light) {
    Map<String, Object> paramsMap = new HashMap<>();
    var returnPart = light ? CypherQueryHelper.getReturnPartLight("o") : CypherQueryHelper.getReturnPart("o");
    String query = String.format(
      "MATCH (o {deleted: FALSE})-[:has_version]->(v:Version) WHERE %s AND %s WITH o ",
      CypherQueryHelper.getShepardIdsPart("o", shepardIds),
      CypherQueryHelper.getVersionHeadPart("v")
    );
    query += returnPart;
    Iterable<T> result = findByQuery(query, paramsMap);
    return StreamSupport.stream(result.spliterator(), false).collect(Collectors.toList());
  }

  private T findByShepardId(Long shepardId, UUID versionUID, boolean light) {
    Map<String, Object> paramsMap = new HashMap<>();
    var returnPart = light ? CypherQueryHelper.getReturnPartLight("o") : CypherQueryHelper.getReturnPart("o");
    String query = String.format(
      "MATCH (o {deleted: FALSE})-[:has_version]->(v:Version) WHERE %s AND %s WITH o ",
      CypherQueryHelper.getShepardIdPart("o", shepardId),
      CypherQueryHelper.getVersionPart("v", versionUID)
    );
    query += returnPart;

    Iterable<T> result = findByQuery(query, paramsMap);
    if (!result.iterator().hasNext()) return null;
    return result.iterator().next();
  }

  @Override
  public abstract Class<T> getEntityType();
}
