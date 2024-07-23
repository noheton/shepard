package de.dlr.shepard.neo4Core.dao;

import de.dlr.shepard.neo4j.NeoConnector;
import de.dlr.shepard.util.CypherQueryHelper;
import java.util.HashMap;
import java.util.Map;
import org.neo4j.ogm.session.Session;

public abstract class VersionableEntityDAO<T> extends GenericDAO<T> {

  protected Session session = null;

  protected VersionableEntityDAO() {
    session = NeoConnector.getInstance().getNeo4jSession();
  }

  public T findByShepardId(Long shepardId) {
    return findByShepardId(shepardId, false);
  }

  public T findByShepardId(Long shepardId, String versionUID) {
    return findByShepardId(shepardId, versionUID, false);
  }

  public T findLightByShepardId(Long shepardId) {
    return findByShepardId(shepardId, true);
  }

  private T findByShepardId(Long shepardId, boolean light) {
    Map<String, Object> paramsMap = new HashMap<>();
    var returnPart = light ? CypherQueryHelper.getReturnPartLight("o") : CypherQueryHelper.getReturnPart("o");
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

  private T findByShepardId(Long shepardId, String versionUID, boolean light) {
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
