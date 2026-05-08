package de.dlr.shepard.context.version.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.common.util.CypherQueryHelper;
import de.dlr.shepard.common.util.CypherQueryHelper.Neighborhood;
import de.dlr.shepard.context.version.entities.Version;
import jakarta.enterprise.context.RequestScoped;
import java.util.ArrayList;
import java.util.Collections;
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
    // L2c read-path swap: query by appId rather than the deprecated id() function.
    // Public method signature stays long for caller-compat until L2d.
    Version ret = null;
    String query = "MATCH (ve:VersionableEntity {appId: $appId})-[:has_version]->(v) RETURN v";
    Map<String, Object> paramsMap = Map.of("appId", resolveAppIdOrEmpty(neo4jId));
    var resultSet = findByQuery(query, paramsMap);
    Iterator<Version> it = resultSet.iterator();
    if (it.hasNext()) {
      ret = it.next();
    }
    return ret;
  }

  public void createLink(long versionableEntityId, UUID versionUID) {
    // L2c read-path swap: query by appId rather than the deprecated id() function.
    HashMap<String, Object> params = new HashMap<String, Object>();
    params.put("versionableEntityAppId", resolveAppIdOrEmpty(versionableEntityId));
    params.put("versionUID", versionUID);
    String query =
      """
      MATCH (ve:VersionableEntity {appId: $versionableEntityAppId}), (v:Version) WHERE v.uid = $versionUID
       CREATE (ve)-[:has_version]->(v)
      """;
    runQuery(query, params);
  }

  public void copyDataObjectsWithParentsAndPredecessors(UUID sourceVersionUID, UUID targetVersionUID) {
    copyDataObjects(sourceVersionUID, targetVersionUID);
    copyChildRelations(sourceVersionUID, targetVersionUID);
    copySuccessorRelations(sourceVersionUID, targetVersionUID);
    copyDataObjectCreatedByRelations(sourceVersionUID, targetVersionUID);
  }

  private void copyDataObjectCreatedByRelations(UUID sourceVersionUID, UUID targetVersionUID) {
    HashMap<String, Object> params = new HashMap<String, Object>();
    params.put("sourceVersionUID", sourceVersionUID);
    params.put("targetVersionUID", targetVersionUID);
    String query =
      """
      MATCH (u_creator:User)<-[:created_by]-(do_source:DataObject)-[:has_version]->(v_source:Version),(do_target:DataObject)-[:has_version]->(v_target:Version)
      WHERE v_source.uid = $sourceVersionUID AND v_target.uid = $targetVersionUID AND do_source.shepardId = do_target.shepardId
      CREATE (do_target)-[:created_by]->(u_creator)
      """;
    runQuery(query, params);
  }

  private void copyDataObjects(UUID sourceVersionUID, UUID targetVersionUID) {
    HashMap<String, Object> params = new HashMap<String, Object>();
    params.put("sourceVersionUID", sourceVersionUID);
    params.put("targetVersionUID", targetVersionUID);
    String query =
      """
      MATCH (do_source:DataObject)-[:has_version]->(v_source:Version)-[:has_predecessor]->(v_target:Version)<-[:has_version]-(col_target:Collection)
      WHERE v_source.uid = $sourceVersionUID AND v_target.uid = $targetVersionUID
      CREATE (col_target)-[:has_dataobject]->(do_target:DataObject:VersionableEntity:BasicEntity)-[:has_version]->(v_target)
      SET do_target = do_source
      """;
    runQuery(query, params);
  }

  private void copyChildRelations(UUID sourceVersionUID, UUID targetVersionUID) {
    HashMap<String, Object> params = new HashMap<String, Object>();
    params.put("sourceVersionUID", sourceVersionUID);
    params.put("targetVersionUID", targetVersionUID);
    String query =
      """
      MATCH (do_source_parent:DataObject)-[:has_child]->(do_source_child:DataObject)-[:has_version]->(v_source:Version)-[:has_predecessor]->(v_target:Version)<-[:has_version]-(do_target_parent:DataObject),
      (v_target)<-[:has_version]-(do_target_child:DataObject)
      WHERE v_source.uid = $sourceVersionUID AND v_target.uid = $targetVersionUID AND do_source_parent.shepardId=do_target_parent.shepardId AND do_source_child.shepardId=do_target_child.shepardId
      CREATE (do_target_parent)-[:has_child]->(do_target_child)
      """;
    runQuery(query, params);
  }

  private void copySuccessorRelations(UUID sourceVersionUID, UUID targetVersionUID) {
    HashMap<String, Object> params = new HashMap<String, Object>();
    params.put("sourceVersionUID", sourceVersionUID);
    params.put("targetVersionUID", targetVersionUID);
    String query =
      """
      MATCH (do_source_predecessor:DataObject)-[:has_successor]->(do_source_successor:DataObject)-[:has_version]->(v_source:Version)-[:has_predecessor]->(v_target:Version)<-[:has_version]-(do_target_predecessor:DataObject),
      (v_target)<-[:has_version]-(do_target_successor:DataObject)
      WHERE v_source.uid = $sourceVersionUID AND v_target.uid = $targetVersionUID
      AND do_source_predecessor.shepardId=do_target_predecessor.shepardId AND do_source_successor.shepardId=do_target_successor.shepardId
      CREATE (do_target_predecessor)-[:has_successor]->(do_target_successor)
      """;
    runQuery(query, params);
  }

  public void copyDataObjectReferences(UUID sourceVersionUID, UUID targetVersionUID) {
    copyInternalDataObjectReferences(sourceVersionUID, targetVersionUID);
    copyExternalDataObjectReferences(sourceVersionUID, targetVersionUID);
  }

  public void copyCollectionReferences(UUID sourceVersionUID, UUID targetVersionUID) {
    HashMap<String, Object> params = new HashMap<String, Object>();
    params.put("sourceVersionUID", sourceVersionUID);
    params.put("targetVersionUID", targetVersionUID);
    String query =
      """
      MATCH (v_source:Version)<-[has_version]-(do_source:DataObject)-[:has_reference]->(cr_source:CollectionReference)-[:points_to]->(c_pointed:Collection),
      (cr_source)-[:created_by]->(u_creator:User),
      (v_target:Version)<-[:has_version]-(do_target:DataObject)
      WHERE v_source.uid = $sourceVersionUID AND v_target.uid = $targetVersionUID
      AND do_source.shepardId = do_target.shepardId
      CREATE (v_target)<-[:has_version]-(cr_target:CollectionReference:BasicReference)<-[:has_reference]-(do_target),
      (c_pointed)<-[:points_to]-(cr_target)-[:created_by]->(u_creator)
      SET cr_target = cr_source
      """;
    runQuery(query, params);
  }

  public void copyExternalDataObjectReferences(UUID sourceVersionUID, UUID targetVersionUID) {
    HashMap<String, Object> params = new HashMap<String, Object>();
    params.put("sourceVersionUID", sourceVersionUID);
    params.put("targetVersionUID", targetVersionUID);
    String query =
      """
      MATCH (v_source_pointer:Version)<-[:has_version]-(c_pointer:Collection)-[:has_dataobject]->(do_source_pointer:DataObject)-[:has_reference]->(dor_source:DataObjectReference)-[:points_to]->(do_pointed_to:DataObject)<-[:has_dataobject]-(c_pointed_to:Collection)-[:has_version]->(v_source_pointed_to:Version),
      (dor_source:DataObjectReference)-[:created_by]->(u_creator:User),
      (v_target_pointer:Version)<-[:has_version]-(do_target_pointer:DataObject)
      WHERE v_source_pointer.uid = $sourceVersionUID AND v_target_pointer.uid = $targetVersionUID
      AND do_source_pointer.shepardId = do_target_pointer.shepardId AND NOT(c_pointer.shepardId = c_pointed_to.shepardId)
      CREATE (do_target_pointer)-[:has_reference]->(dor_target:DataObjectReference:BasicReference:VersionableEntity:BasicEntity)-[:points_to]->(do_pointed_to),
      (v_target_pointer)<-[:has_version]-(dor_target)-[:created_by]->(u_creator)
      SET dor_target=dor_source
      """;
    runQuery(query, params);
  }

  public void copyInternalDataObjectReferences(UUID sourceVersionUID, UUID targetVersionUID) {
    HashMap<String, Object> params = new HashMap<String, Object>();
    params.put("sourceVersionUID", sourceVersionUID);
    params.put("targetVersionUID", targetVersionUID);
    String query =
      """
      MATCH (v_source:Version)<-[:has_version]-(c_pointer:Collection)-[:has_dataobject]->(do_source_pointer:DataObject)-[:has_reference]->(dor_source:DataObjectReference)-[:points_to]->(do_source_pointed_to:DataObject)<-[:has_dataobject]-(c_pointed_to:Collection),
      (do_target_pointed_to:DataObject)-[:has_version]->(v_target:Version)<-[:has_version]-(do_target_pointer:DataObject)
      WHERE v_source.uid = $sourceVersionUID AND v_target.uid = $targetVersionUID
      AND do_source_pointer.shepardId = do_target_pointer.shepardId AND do_source_pointed_to.shepardId = do_target_pointed_to.shepardId AND c_pointer.appId = c_pointed_to.appId
      CREATE (do_target_pointer)-[:has_reference]->(dor_target:DataObjectReference:BasicReference:VersionableEntity:BasicEntity)-[:points_to]->(do_target_pointed_to)
      SET dor_target = dor_source
      """;
    runQuery(query, params);
    attachVersionAndCreatedByToDataObjectReferences(sourceVersionUID, targetVersionUID);
  }

  public void attachVersionAndCreatedByToDataObjectReferences(UUID sourceVersionUID, UUID targetVersionUID) {
    HashMap<String, Object> params = new HashMap<String, Object>();
    params.put("sourceVersionUID", sourceVersionUID);
    params.put("targetVersionUID", targetVersionUID);
    String query =
      """
      MATCH (v_source:Version)<-[:has_version]-(dor_source:DataObjectReference)-[:created_by]->(u_creator:User), (v_target:Version)<-[:has_version]-(do_target_pointer:DataObject)-[:has_reference]->(dor_target:DataObjectReference)
      WHERE v_source.uid = $sourceVersionUID AND v_target.uid = $targetVersionUID
      AND dor_source.shepardId = dor_target.shepardId
      CREATE (v_target)<-[:has_version]-(dor_target)-[:created_by]->(u_creator)
      """;
    runQuery(query, params);
  }

  public void copyFileReferences(UUID sourceVersionUID, UUID targetVersionUID) {
    HashMap<String, Object> params = new HashMap<String, Object>();
    params.put("sourceVersionUID", sourceVersionUID);
    params.put("targetVersionUID", targetVersionUID);
    String query =
      """
      MATCH (v_source:Version)<-[has_version]-(do_source:DataObject)-[:has_reference]->(fr_source:FileReference)-[:is_in_container]->(fc_pointed:FileContainer),
      (fr_source)-[:created_by]->(u_creator:User), (v_target:Version)<-[:has_version]-(do_target:DataObject)
      WHERE v_source.uid = $sourceVersionUID AND v_target.uid = $targetVersionUID AND do_source.shepardId = do_target.shepardId
      CREATE (v_target)<-[:has_version]-(fr_target:FileReference:BasicReference:VersionableEntity:BasicEntity)<-[:has_reference]-(do_target),
      (fc_pointed)<-[:is_in_container]-(fr_target)-[:created_by]->(u_creator)
      SET fr_target = fr_source
      """;
    runQuery(query, params);
    query = """
    MATCH (v_source:Version)<-[:has_version]-(fr_source:FileReference)-[:has_payload]->(sf:ShepardFile),
    (v_target:Version)<-[:has_version]-(fr_target:FileReference)
    WHERE v_source.uid = $sourceVersionUID AND v_target.uid = $targetVersionUID AND fr_source.shepardId = fr_target.shepardId
    CREATE (fr_target)-[:has_payload]->(sf)
    """;
    runQuery(query, params);
  }

  public void copyStructuredDataReferences(UUID sourceVersionUID, UUID targetVersionUID) {
    HashMap<String, Object> params = new HashMap<String, Object>();
    params.put("sourceVersionUID", sourceVersionUID);
    params.put("targetVersionUID", targetVersionUID);
    String query =
      """
      MATCH (v_source:Version)<-[has_version]-(do_source:DataObject)-[:has_reference]->(sdr_source:StructuredDataReference)-[:is_in_container]->(sdc_pointed:StructuredDataContainer),
      (sdr_source)-[:created_by]->(u_creator:User),
      (v_target:Version)<-[:has_version]-(do_target:DataObject)
      WHERE v_source.uid = $sourceVersionUID AND v_target.uid = $targetVersionUID
      AND do_source.shepardId = do_target.shepardId
      CREATE (v_target)<-[:has_version]-(sdr_target:StructuredDataReference:BasicReference:VersionableEntity:BasicEntity)<-[:has_reference]-(do_target),
      (sdc_pointed)<-[:is_in_container]-(sdr_target)-[:created_by]->(u_creator)
      SET sdr_target = sdr_source
      """;
    runQuery(query, params);
    query = """
    MATCH (v_source:Version)<-[:has_version]-(sdr_source:StructuredDataReference)-[:has_payload]->(sd:StructuredData),
    (v_target:Version)<-[:has_version]-(sdr_target:StructuredDataReference)
    WHERE v_source.uid = $sourceVersionUID AND v_target.uid = $targetVersionUID
    AND sdr_source.shepardId = sdr_target.shepardId
    CREATE (sdr_target)-[:has_payload]->(sd)
    """;
    runQuery(query, params);
  }

  public void copyTimeseriesReferences(UUID sourceVersionUID, UUID targetVersionUID) {
    HashMap<String, Object> params = new HashMap<String, Object>();
    params.put("sourceVersionUID", sourceVersionUID);
    params.put("targetVersionUID", targetVersionUID);
    String query =
      """
      MATCH (v_source:Version)<-[has_version]-(do_source:DataObject)-[:has_reference]->(tsr_source:TimeseriesReference)-[:is_in_container]->(tsc_pointed:TimeseriesContainer),
      (tsr_source)-[:created_by]->(u_creator:User),
      (v_target:Version)<-[:has_version]-(do_target:DataObject)
      WHERE v_source.uid = $sourceVersionUID AND v_target.uid = $targetVersionUID
      AND do_source.shepardId = do_target.shepardId
      CREATE (v_target)<-[:has_version]-(tsr_target:TimeseriesReference:BasicReference:VersionableEntity:BasicEntity)<-[:has_reference]-(do_target),
      (tsc_pointed)<-[:is_in_container]-(tsr_target)-[:created_by]->(u_creator)
      SET tsr_target = tsr_source
      """;
    runQuery(query, params);
    query = """
    MATCH (v_source:Version)<-[:has_version]-(tsr_source:TimeseriesReference)-[:has_payload]->(ts:Timeseries),
    (v_target:Version)<-[:has_version]-(tsr_target:TimeseriesReference)
    WHERE v_source.uid = $sourceVersionUID AND v_target.uid = $targetVersionUID
    AND tsr_source.shepardId = tsr_target.shepardId
    CREATE (tsr_target)-[:has_payload]->(ts)
    """;
    runQuery(query, params);
  }

  public void copyURIReferences(UUID sourceVersionUID, UUID targetVersionUID) {
    HashMap<String, Object> params = new HashMap<String, Object>();
    params.put("sourceVersionUID", sourceVersionUID);
    params.put("targetVersionUID", targetVersionUID);
    String query =
      """
      MATCH (v_source:Version)<-[:has_version]-(do_source:DataObject)-[:has_reference]->(ur_source:URIReference)-[:created_by]->(u_creator:User),
      (v_target:Version)<-[:has_version]-(do_target:DataObject)
      WHERE v_source.uid = $sourceVersionUID AND v_target.uid = $targetVersionUID
      AND do_source.shepardId = do_target.shepardId
      CREATE (v_target)<-[:has_version]-(ur_target:URIReference:BasicReference:VersionableEntity:BasicEntity)<-[:has_reference]-(do_target),
      (ur_target)-[:created_by]->(u_creator)
      SET ur_target=ur_source
      """;
    runQuery(query, params);
  }

  public void addAnnotations(UUID sourceVersionUID, UUID targetVersionUID) {
    HashMap<String, Object> params = new HashMap<String, Object>();
    params.put("sourceVersionUID", sourceVersionUID);
    params.put("targetVersionUID", targetVersionUID);
    String query =
      """
      MATCH (v_source:Version)<-[:has_version]-(sourceEntity)-[:has_annotation]->(a:SemanticAnnotation),
      (v_target:Version)<-[:has_version]-(targetEntity)
      WHERE v_source.uid = $sourceVersionUID AND v_target.uid = $targetVersionUID
      AND sourceEntity.shepardId = targetEntity.shepardId
      CREATE (targetEntity)-[:has_annotation]->(a)
      """;
    runQuery(query, params);
  }

  public void removeHasPredecessor(UUID sourceVersionUID, UUID targetVersionUID) {
    HashMap<String, Object> params = new HashMap<String, Object>();
    params.put("sourceVersionUID", sourceVersionUID);
    params.put("targetVersionUID", targetVersionUID);
    String query =
      """
      MATCH (v_source:Version)-[hp:has_predecessor]->(v_target:Version)
      WHERE v_source.uid = $sourceVersionUID AND v_target.uid = $targetVersionUID
      DELETE hp
      """;
    runQuery(query, params);
  }

  public Iterable<Version> findByQuery(String query) {
    return findByQuery(query, Collections.emptyMap());
  }
}
