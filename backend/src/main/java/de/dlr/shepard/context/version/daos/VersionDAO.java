package de.dlr.shepard.context.version.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.common.util.CypherQueryHelper;
import de.dlr.shepard.common.util.CypherQueryHelper.Neighborhood;
import de.dlr.shepard.context.version.entities.Version;
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
    query = query + "MATCH (col:Collection)-[]->(ver:Version) WHERE col.shepardId = " + collectionId + " ";
    query = query + CypherQueryHelper.getReturnPart("ver", Neighborhood.EVERYTHING);

    var resultSet = findByQuery(query, paramsMap);
    Iterator<Version> it = resultSet.iterator();
    while (it.hasNext()) {
      Version next = it.next();
      resultHashSet.add(find(next.getUid()));
    }
    for (Version ver : resultHashSet) result.add(ver);
    return result;
  }

  public Version findHEADVersion(long collectionId) {
    Version ret = null;
    String query =
      "MATCH (c:Collection)-[:has_version]->(v:Version) WHERE c.shepardId = " +
      collectionId +
      " AND " +
      CypherQueryHelper.getVersionHeadPart("v") +
      " " +
      CypherQueryHelper.getReturnPart("v", Neighborhood.EVERYTHING);
    Map<String, Object> paramsMap = new HashMap<>();
    var resultSet = findByQuery(query, paramsMap);
    Iterator<Version> it = resultSet.iterator();
    if (it.hasNext()) {
      ret = it.next();
    }
    return ret;
  }

  public Version findVersionLightByNeo4jId(long neo4jId) {
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

  public void createLink(long versionableEntityId, UUID versionUID) {
    String query =
      "MATCH (ve:VersionableEntity), (v:Version) WHERE id(ve) = " +
      versionableEntityId +
      " AND v.uid = '" +
      versionUID +
      "' CREATE (ve)-[:has_version]->(v)";
    Map<String, Object> paramsMap = new HashMap<>();
    runQuery(query, paramsMap);
  }

  public boolean copyDataObjectsWithParentsAndPredecessors(UUID sourceVersionUID, UUID targetVersionUID) {
    copyDataObjects(sourceVersionUID, targetVersionUID);
    copyChildRelations(sourceVersionUID, targetVersionUID);
    copySuccessorRelations(sourceVersionUID, targetVersionUID);
    return true;
  }

  public void removeSuperflousHasDataObjects(UUID sourceVersionUID, UUID targetVersionUID) {
    StringBuffer queryBuffer = new StringBuffer();
    queryBuffer.append(
      "MATCH (v_target:Version)<-[:has_version]-(col_target:Collection)-[hd:has_dataobject]->(do_source:DataObject)-[:has_version]->(v_source:Version)"
    );
    queryBuffer.append(
      "WHERE v_source.uid = '" + sourceVersionUID + "' AND v_target.uid = '" + targetVersionUID + "' "
    );
    queryBuffer.append(" DELETE hd");
  }

  public void copyDataObjects(UUID sourceVersionUID, UUID targetVersionUID) {
    StringBuffer queryBuffer = new StringBuffer();
    queryBuffer.append(
      "MATCH (do_source:DataObject)-[:has_version]->(v_source:Version)-[:has_predecessor]->(v_target:Version)<-[:has_version]-(col_target:Collection)"
    );
    queryBuffer.append(
      " WHERE v_source.uid = '" + sourceVersionUID + "' AND v_target.uid = '" + targetVersionUID + "' "
    );
    queryBuffer.append(" CREATE (col_target)-[:has_dataobject]->(do_target:DataObject)-[:has_version]->(v_target) ");
    queryBuffer.append(" SET do_target = do_source");
    Map<String, Object> paramsMap = new HashMap<>();
    runQuery(queryBuffer.toString(), paramsMap);
  }

  public void copyChildRelations(UUID sourceVersionUID, UUID targetVersionUID) {
    StringBuffer queryBuffer = new StringBuffer();
    queryBuffer.append(
      "MATCH (do_source_parent:DataObject)-[:has_child]->(do_source_child:DataObject)-[:has_version]->(v_source:Version)-[:has_predecessor]->(v_target:Version)<-[:has_version]-(do_target_parent:DataObject), "
    );
    queryBuffer.append("(v_target)<-[:has_version]-(do_target_child:DataObject) ");
    queryBuffer.append(
      " WHERE v_source.uid = '" + sourceVersionUID + "' AND v_target.uid = '" + targetVersionUID + "' "
    );
    queryBuffer.append(
      " AND do_source_parent.shepardId=do_target_parent.shepardId AND do_source_child.shepardId=do_target_child.shepardId "
    );
    queryBuffer.append(" CREATE (do_target_parent)-[:has_child]->(do_target_child)");
    Map<String, Object> paramsMap = new HashMap<>();
    runQuery(queryBuffer.toString(), paramsMap);
  }

  public void copySuccessorRelations(UUID sourceVersionUID, UUID targetVersionUID) {
    StringBuffer queryBuffer = new StringBuffer();
    queryBuffer.append(
      "MATCH (do_source_predecessor:DataObject)-[:has_successor]->(do_source_successor:DataObject)-[:has_version]->(v_source:Version)-[:has_predecessor]->(v_target:Version)<-[:has_version]-(do_target_predecessor:DataObject), "
    );
    queryBuffer.append("(v_target)<-[:has_version]-(do_target_successor:DataObject) ");
    queryBuffer.append(
      " WHERE v_source.uid = '" + sourceVersionUID + "' AND v_target.uid = '" + targetVersionUID + "' "
    );
    queryBuffer.append(
      " AND do_source_predecessor.shepardId=do_target_predecessor.shepardId AND do_source_successor.shepardId=do_target_successor.shepardId "
    );
    queryBuffer.append(" CREATE (do_target_predecessor)-[:has_successor]->(do_target_successor)");
    Map<String, Object> paramsMap = new HashMap<>();
    runQuery(queryBuffer.toString(), paramsMap);
  }
}
