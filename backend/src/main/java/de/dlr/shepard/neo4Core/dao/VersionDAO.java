package de.dlr.shepard.neo4Core.dao;

import de.dlr.shepard.neo4Core.entities.Version;
import de.dlr.shepard.util.CypherQueryHelper;
import jakarta.enterprise.context.RequestScoped;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RequestScoped
public class VersionDAO extends GenericDAO<Version> {

  /**
   * Find a version by collectionId and uid
   *
   * @param collectionId Identifies the collection
   * @param versionUID   Identifies the version
   * @return the found version
   */
  public Version find(long collectionId, String versionUID) {
    Version ver = null;
    Map<String, Object> paramsMap = new HashMap<>();
    String query = "";
    query = query +
    "MATCH (col:Collection)-[]->(ver:Version) WHERE col.shepardId = " +
    collectionId +
    " AND ver.uid = '" +
    versionUID +
    "' AND col.deleted = false";
    query = query + " RETURN ver";
    var resultSet = findByQuery(query, paramsMap);
    Iterator<Version> it = resultSet.iterator();
    if (it.hasNext()) {
      ver = find(it.next().getUid());
    }
    return ver;
  }

  /**
   * Find a version by uid
   *
   * @param id Identifies the version
   * @return the found version
   */
  public Version find(UUID id) {
    Version version = session.load(getEntityType(), id, DEPTH_ENTITY);
    return version;
  }

  @Override
  public Class<Version> getEntityType() {
    return Version.class;
  }

  public List<Version> findAllVersions(long collectionId) {
    ArrayList<Version> result = new ArrayList<Version>();
    HashSet<Version> resultHashSet = new HashSet<Version>();
    Map<String, Object> paramsMap = new HashMap<>();
    String query = "";
    query = query +
    "MATCH (col:Collection)-[]->(ver:Version) WHERE col.shepardId = " +
    collectionId +
    " AND col.deleted = false";
    query = query + " RETURN ver";
    var resultSet = findByQuery(query, paramsMap);
    Iterator<Version> it = resultSet.iterator();
    while (it.hasNext()) {
      Version next = it.next();
      resultHashSet.add(find(next.getUid()));
    }
    for (Version ver : resultHashSet) result.add(ver);
    return result;
  }

  public UUID findHEADVersionUUID(long collectionId) {
    UUID ret = null;
    String query =
      "MATCH (c:Collection)-[:has_version]->(v:Version) WHERE c.shepardId = " +
      collectionId +
      " AND " +
      CypherQueryHelper.getVersionHeadPart("v") +
      " RETURN v";
    Map<String, Object> paramsMap = new HashMap<>();
    var resultSet = findByQuery(query, paramsMap);
    Iterator<Version> it = resultSet.iterator();
    if (it.hasNext()) {
      ret = it.next().getUid();
    }
    return ret;
  }

  public Version findHEADVersion(long collectionId) {
    Version ret = null;
    String query =
      "MATCH (c:Collection)-[:has_version]->(v:Version) WHERE c.shepardId = " +
      collectionId +
      " AND " +
      CypherQueryHelper.getVersionHeadPart("v") +
      " RETURN v";
    Map<String, Object> paramsMap = new HashMap<>();
    var resultSet = findByQuery(query, paramsMap);
    Iterator<Version> it = resultSet.iterator();
    if (it.hasNext()) {
      ret = it.next();
    }
    return ret;
  }

  public void createLink(long versionableEntityId, String versionUID) {
    String query =
      "MATCH (ve:VersionableEntity), (v:Version) WHERE id(ve) = " +
      versionableEntityId +
      " AND v.uid = '" +
      versionUID +
      "' CREATE (ve)-[:has_version]->(v)";
    Map<String, Object> paramsMap = new HashMap<>();
    runQuery(query, paramsMap);
  }

  public Version findVersionByNeo4jId(long neo4jId) {
    Version ret = null;
    String query = "MATCH (ve:VersionableEntity)-[:has_version]->(v) WHERE id(ve) = " + neo4jId + " RETURN v";
    Map<String, Object> paramsMap = new HashMap<>();
    var resultSet = findByQuery(query, paramsMap);
    Iterator<Version> it = resultSet.iterator();
    if (it.hasNext()) {
      ret = it.next();
    }
    return ret;
  }
}
