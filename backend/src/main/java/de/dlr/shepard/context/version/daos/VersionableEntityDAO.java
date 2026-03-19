package de.dlr.shepard.context.version.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.common.util.CypherQueryHelper;
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
    return list.isEmpty() ? null : list.getFirst();
  }

  public T findByShepardId(Long shepardId, boolean light) {
    List<T> list = findByShepardIds(List.of(shepardId), light);
    return list.isEmpty() ? null : list.getFirst();
  }

  public T findByShepardId(Long shepardId, UUID versionUID) {
    return findByShepardId(shepardId, versionUID, false);
  }

  public T findByShepardId(Long shepardId, UUID versionUID, boolean light) {
    Map<String, Object> paramsMap = new HashMap<>();
    var returnPart = light ? CypherQueryHelper.getReturnPartLight("o") : CypherQueryHelper.getReturnPart("o");
    String versionPart = "";
    if (versionUID != null) versionPart = CypherQueryHelper.getVersionPart("v", versionUID);
    else versionPart = CypherQueryHelper.getVersionHeadPart("v");
    String query =
      "MATCH (o:%s {deleted: FALSE})-[:has_version]->(v:Version) WHERE %s AND %s WITH o ".formatted(
          getEntityType().getSimpleName(),
          CypherQueryHelper.getShepardIdPart("o", shepardId),
          versionPart
        );
    query += returnPart;
    Iterable<T> result = findByQuery(query, paramsMap);
    if (!result.iterator().hasNext()) return null;
    return result.iterator().next();
  }

  public List<T> findByShepardIds(List<Long> shepardIds) {
    return findByShepardIds(shepardIds, false);
  }

  private List<T> findByShepardIds(List<Long> shepardIds, boolean light) {
    Map<String, Object> paramsMap = new HashMap<>();
    var returnPart = light ? CypherQueryHelper.getReturnPartLight("o") : CypherQueryHelper.getReturnPart("o");

    String query =
      "MATCH (o:%s {deleted: FALSE})-[:has_version]->(v:Version) WHERE %s AND %s WITH o ".formatted(
          getEntityType().getSimpleName(),
          CypherQueryHelper.getShepardIdsPart("o", shepardIds),
          CypherQueryHelper.getVersionHeadPart("v")
        );
    query += returnPart;
    Iterable<T> result = findByQuery(query, paramsMap);
    return StreamSupport.stream(result.spliterator(), false).collect(Collectors.toList());
  }

  @Override
  public abstract Class<T> getEntityType();
}
