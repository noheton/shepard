package de.dlr.shepard.neo4Core.dao;

import de.dlr.shepard.util.CypherQueryHelper;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public abstract class VersionableEntityDAO<T> extends GenericDAO<T> {

  protected VersionableEntityDAO() {
    super();
  }

  public T findByShepardId(Long shepardId) {
    return findByShepardId(shepardId, false, 1);
  }

  public T findByShepardId(Long shepardId, int depth) {
    return findByShepardId(shepardId, false, depth);
  }

  public T findByShepardId(Long shepardId, UUID versionUID) {
    return findByShepardId(shepardId, versionUID, false);
  }

  public T findLightByShepardId(Long shepardId) {
    return findByShepardId(shepardId, true, 1);
  }

  public T findLightByShepardId(Long shepardId, int depth) {
    return findByShepardId(shepardId, true, depth);
  }

  private T findByShepardId(Long shepardId, boolean light) {
    return findByShepardId(shepardId, light, 1);
  }

  private T findByShepardId(Long shepardId, boolean light, int depth) {
    Map<String, Object> paramsMap = new HashMap<>();
    var returnPart = light ? CypherQueryHelper.getReturnPartLight("o") : CypherQueryHelper.getReturnPart("o", depth);
    String query = String.format(
      "MATCH (o {deleted: FALSE})-[:has_version]->(v:Version) WHERE %s AND %s WITH o ",
      CypherQueryHelper.getShepardIdPart("o", shepardId),
      CypherQueryHelper.getVersionHeadPart("v")
    );
    query += returnPart;
    Iterable<T> result = findByQuery(query, paramsMap);
    if (!result.iterator().hasNext()) return null;
    return result.iterator().next();
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
