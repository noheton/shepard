package de.dlr.shepard.context.version.daos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.common.configuration.feature.toggles.VersioningFeatureToggle;
import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.common.util.CypherQueryHelper;
import de.dlr.shepard.common.util.CypherQueryHelper.Neighborhood;
import de.dlr.shepard.context.version.entities.Version;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.model.QueryStatistics;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.Session;

@EnabledIf(VersioningFeatureToggle.IS_ENABLED_METHOD_ID)
public class VersionDAOTest extends BaseTestCase {

  @Mock
  private Session session;

  @Mock
  private GenericDAO<?> genericDao;

  @Mock
  private Result result;

  @Mock
  private Result successorResult;

  @Mock
  private Result dataObjectResult;

  @Mock
  private Result createdByResult;

  @Mock
  private Result copyExternalDORResult;

  @Mock
  private Result copyFileReferencesResult;

  @Mock
  private Result copyFilePayloadResult;

  @Mock
  private Result copyTimeseriesReferencesResult;

  @Mock
  private Result copyCollectionReferencesResult;

  @Mock
  private Result copyCollectionPayloadResult;

  @Mock
  private Result copyURIReferencesResult;

  @Mock
  private Result copyURIPayloadResult;

  @Mock
  private Result copyTimeseriesPayloadResult;

  @Mock
  private Result copyStructuredDataReferencesResult;

  @Mock
  private Result copyStructuredDataPayloadResult;

  @Mock
  private Result copyInternalDORResult;

  @Mock
  private Result copyVersionAndCreatedByResult;

  @Mock
  private Result childResult;

  @Mock
  private QueryStatistics queryStatistics;

  @InjectMocks
  private VersionDAO dao;

  @Test
  public void getEntityTypeTest() {
    var type = dao.getEntityType();
    assertEquals(Version.class, type);
  }

  @Test
  public void findByUUIDTest() {
    Version ver = new Version();
    ver.setDescription("version");
    UUID id = new UUID(1L, 2L);
    when(session.load(Version.class, id, 1)).thenReturn(ver);
    Version found = dao.find(id);
    assertEquals(ver, found);
  }

  @Test
  public void findByCollectionIdVersionUIDTest() {
    Version ver = new Version();
    ver.setDescription("version");
    UUID id = new UUID(1L, 2L);
    ver.setUid(id);
    when(session.load(Version.class, id, 1)).thenReturn(ver);
    Version found = dao.find(id);
    assertEquals(ver, found);
  }

  @Test
  public void findAllVersionsTest() {
    Version ver1 = new Version();
    Version ver2 = new Version();
    ver1.setName("name1");
    ver2.setName("ver2");
    long collectionId = 5;
    String query = "";
    query = query + "MATCH (col:Collection)-[]->(ver:Version) WHERE col.shepardId = " + collectionId + " ";
    query = query + CypherQueryHelper.getReturnPart("ver", Neighborhood.EVERYTHING);
    Map<String, Object> paramsMap = new HashMap<>();
    when(session.query(Version.class, query, paramsMap)).thenReturn(List.of(ver1, ver2));
    List<Version> allVersions = dao.findAllVersions(collectionId);
    assertThat(allVersions.contains(ver1));
    assertThat(allVersions.contains(ver2));
  }

  @Test
  public void findHEADVersionTest() {
    Version ver = new Version();
    UUID uid = new UUID(1L, 2L);
    ver.setUid(uid);
    long collectionId = 5L;
    Map<String, Object> paramsMap = new HashMap<>();
    String query =
      "MATCH (c:Collection)-[:has_version]->(v:Version) WHERE c.shepardId = " +
      collectionId +
      " AND " +
      CypherQueryHelper.getVersionHeadPart("v") +
      " " +
      CypherQueryHelper.getReturnPart("v", Neighborhood.EVERYTHING);
    when(session.query(Version.class, query, paramsMap)).thenReturn(List.of(ver));
    Version headVersion = dao.findHEADVersion(collectionId);
    assertEquals(ver, headVersion);
  }

  @Test
  public void findVersionLigthByNeo4jIdTest() {
    Version ver = new Version();
    ver.setName("name");
    long neo4jId = 10L;
    String query = "MATCH (ve:VersionableEntity)-[:has_version]->(v) WHERE id(ve) = " + neo4jId + " RETURN v";
    Map<String, Object> paramsMap = new HashMap<>();
    when(session.query(Version.class, query, paramsMap)).thenReturn(List.of(ver));
    Version found = dao.findVersionLightByNeo4jId(neo4jId);
    assertEquals(ver, found);
  }

  //@Test
  public void createLinkTest() {
    long versionableEntityId = 15L;
    UUID versionUID = new UUID(3L, 4L);
    String query =
      "MATCH (ve:VersionableEntity), (v:Version) WHERE id(ve) = " +
      versionableEntityId +
      " AND v.uid = '" +
      versionUID +
      "' CREATE (ve)-[:has_version]->(v)";
    Map<String, Object> paramsMap = new HashMap<>();
    when(session.query(query, paramsMap)).thenReturn(result);
    when(result.queryStatistics()).thenReturn(queryStatistics);
    when(queryStatistics.containsUpdates()).thenReturn(true);
    dao.createLink(versionableEntityId, versionUID);
    verify(session).query(query, paramsMap);
  }

  //@Test
  public void copyDataObjectsWithParentsAndPredecessorsTest() {
    UUID sourceVersionUID = new UUID(0L, 1L);
    UUID targetVersionUID = new UUID(2L, 3L);
    StringBuffer successorQueryBuffer = new StringBuffer();
    successorQueryBuffer.append(
      "MATCH (do_source_predecessor:DataObject)-[:has_successor]->(do_source_successor:DataObject)-[:has_version]->(v_source:Version)-[:has_predecessor]->(v_target:Version)<-[:has_version]-(do_target_predecessor:DataObject), "
    );
    successorQueryBuffer.append("(v_target)<-[:has_version]-(do_target_successor:DataObject) ");
    successorQueryBuffer.append(
      " WHERE v_source.uid = '" + sourceVersionUID + "' AND v_target.uid = '" + targetVersionUID + "' "
    );
    successorQueryBuffer.append(
      " AND do_source_predecessor.shepardId=do_target_predecessor.shepardId AND do_source_successor.shepardId=do_target_successor.shepardId "
    );
    successorQueryBuffer.append(" CREATE (do_target_predecessor)-[:has_successor]->(do_target_successor)");
    String successorsQuery = successorQueryBuffer.toString();
    Map<String, Object> paramsMap = new HashMap<>();
    when(session.query(successorsQuery, paramsMap)).thenReturn(successorResult);
    when(successorResult.queryStatistics()).thenReturn(queryStatistics);
    StringBuffer childQueryBuffer = new StringBuffer();
    childQueryBuffer.append(
      "MATCH (do_source_parent:DataObject)-[:has_child]->(do_source_child:DataObject)-[:has_version]->(v_source:Version)-[:has_predecessor]->(v_target:Version)<-[:has_version]-(do_target_parent:DataObject), "
    );
    childQueryBuffer.append("(v_target)<-[:has_version]-(do_target_child:DataObject) ");
    childQueryBuffer.append(
      " WHERE v_source.uid = '" + sourceVersionUID + "' AND v_target.uid = '" + targetVersionUID + "' "
    );
    childQueryBuffer.append(
      " AND do_source_parent.shepardId=do_target_parent.shepardId AND do_source_child.shepardId=do_target_child.shepardId "
    );
    childQueryBuffer.append(" CREATE (do_target_parent)-[:has_child]->(do_target_child)");
    String childQuery = childQueryBuffer.toString();
    when(session.query(childQuery, paramsMap)).thenReturn(childResult);
    when(childResult.queryStatistics()).thenReturn(queryStatistics);
    StringBuffer dataObjectQueryBuffer = new StringBuffer();
    dataObjectQueryBuffer.append(
      "MATCH (do_source:DataObject)-[:has_version]->(v_source:Version)-[:has_predecessor]->(v_target:Version)<-[:has_version]-(col_target:Collection)"
    );
    dataObjectQueryBuffer.append(
      " WHERE v_source.uid = '" + sourceVersionUID + "' AND v_target.uid = '" + targetVersionUID + "' "
    );
    dataObjectQueryBuffer.append(
      " CREATE (col_target)-[:has_dataobject]->(do_target:DataObject:VersionableEntity:BasicEntity)-[:has_version]->(v_target) "
    );
    dataObjectQueryBuffer.append(" SET do_target = do_source");
    String dataObjectQuery = dataObjectQueryBuffer.toString();
    when(session.query(dataObjectQuery, paramsMap)).thenReturn(dataObjectResult);
    when(dataObjectResult.queryStatistics()).thenReturn(queryStatistics);
    when(queryStatistics.containsUpdates()).thenReturn(true);
    StringBuffer createdByBuffer = new StringBuffer();
    createdByBuffer.append(
      "MATCH (u_creator:User)<-[:created_by]-(do_source:DataObject)-[:has_version]->(v_source:Version),(do_target:DataObject)-[:has_version]->(v_target:Version) "
    );
    createdByBuffer.append(
      " WHERE v_source.uid = '" +
      sourceVersionUID +
      "' AND v_target.uid = '" +
      targetVersionUID +
      "' AND do_source.shepardId = do_target.shepardId "
    );
    createdByBuffer.append(" CREATE (do_target)-[:created_by]->(u_creator)");
    String createdByQuery = createdByBuffer.toString();
    when(session.query(createdByQuery, paramsMap)).thenReturn(createdByResult);
    when(createdByResult.queryStatistics()).thenReturn(queryStatistics);
    when(queryStatistics.containsUpdates()).thenReturn(true);

    dao.copyDataObjectsWithParentsAndPredecessors(sourceVersionUID, targetVersionUID);
    verify(session).query(dataObjectQuery, paramsMap);
    verify(session).query(successorsQuery, paramsMap);
    verify(session).query(childQuery, paramsMap);
  }

  //@Test
  public void testCopyDataObjectReferences() {
    UUID sourceVersionUID = new UUID(0L, 1L);
    UUID targetVersionUID = new UUID(2L, 3L);
    Map<String, Object> paramsMap = new HashMap<>();
    StringBuffer copyInternalDORBuffer = new StringBuffer();
    copyInternalDORBuffer.append(
      "MATCH (v_source:Version)<-[:has_version]-(c_pointer:Collection)-[:has_dataobject]->(do_source_pointer:DataObject)-[:has_reference]->(dor_source:DataObjectReference)-[:points_to]->(do_source_pointed_to:DataObject)<-[:has_dataobject]-(c_pointed_to:Collection), "
    );
    copyInternalDORBuffer.append(
      "(do_target_pointed_to:DataObject)-[:has_version]->(v_target:Version)<-[:has_version]-(do_target_pointer:DataObject) "
    );
    copyInternalDORBuffer.append(
      " WHERE v_source.uid = '" +
      sourceVersionUID +
      "' AND v_target.uid = '" +
      targetVersionUID +
      "' AND do_source_pointer.shepardId = do_target_pointer.shepardId AND do_source_pointed_to.shepardId = do_target_pointed_to.shepardId AND id(c_pointer) = id(c_pointed_to) "
    );
    copyInternalDORBuffer.append(
      " CREATE (do_target_pointer)-[:has_reference]->(dor_target:DataObjectReference:BasicReference:VersionableEntity:BasicEntity)-[:points_to]->(do_target_pointed_to) "
    );
    copyInternalDORBuffer.append(" SET dor_target = dor_source");
    String copyInternalDORString = copyInternalDORBuffer.toString();
    when(session.query(copyInternalDORString, paramsMap)).thenReturn(copyInternalDORResult);
    when(copyInternalDORResult.queryStatistics()).thenReturn(queryStatistics);
    when(queryStatistics.containsUpdates()).thenReturn(true);
    StringBuffer attachVersionAndCreatedByBuffer = new StringBuffer();
    attachVersionAndCreatedByBuffer.append(
      "MATCH (v_source:Version)<-[:has_version]-(dor_source:DataObjectReference)-[:created_by]->(u_creator:User), (v_target:Version)<-[:has_version]-(do_target_pointer:DataObject)-[:has_reference]->(dor_target:DataObjectReference) "
    );
    attachVersionAndCreatedByBuffer.append(
      " WHERE v_source.uid = '" +
      sourceVersionUID +
      "' AND v_target.uid = '" +
      targetVersionUID +
      "' AND dor_source.shepardId = dor_target.shepardId "
    );
    attachVersionAndCreatedByBuffer.append(
      " CREATE (v_target)<-[:has_version]-(dor_target)-[:created_by]->(u_creator)"
    );
    String attachVersionAndCreatedByString = attachVersionAndCreatedByBuffer.toString();
    when(session.query(attachVersionAndCreatedByString, paramsMap)).thenReturn(copyVersionAndCreatedByResult);
    when(copyVersionAndCreatedByResult.queryStatistics()).thenReturn(queryStatistics);
    when(queryStatistics.containsUpdates()).thenReturn(true);
    StringBuffer copyExternalDORBuffer = new StringBuffer();
    copyExternalDORBuffer.append(
      "MATCH (v_source_pointer:Version)<-[:has_version]-(c_pointer:Collection)-[:has_dataobject]->(do_source_pointer:DataObject)-[:has_reference]->(dor_source:DataObjectReference)-[:points_to]->(do_pointed_to:DataObject)<-[:has_dataobject]-(c_pointed_to:Collection)-[:has_version]->(v_source_pointed_to:Version), "
    );
    copyExternalDORBuffer.append("(dor_source:DataObjectReference)-[:created_by]->(u_creator:User), ");
    copyExternalDORBuffer.append("(v_target_pointer:Version)<-[:has_version]-(do_target_pointer:DataObject) ");
    copyExternalDORBuffer.append(
      "WHERE v_source_pointer.uid = '" +
      sourceVersionUID +
      "' AND v_target_pointer.uid = '" +
      targetVersionUID +
      "' AND do_source_pointer.shepardId = do_target_pointer.shepardId AND NOT(c_pointer.shepardId = c_pointed_to.shepardId) "
    );
    copyExternalDORBuffer.append(
      "CREATE (do_target_pointer)-[:has_reference]->(dor_target:DataObjectReference:BasicReference:VersionableEntity:BasicEntity)-[:points_to]->(do_pointed_to), "
    );
    copyExternalDORBuffer.append(" (v_target_pointer)<-[:has_version]-(dor_target)-[:created_by]->(u_creator) ");
    copyExternalDORBuffer.append("SET dor_target=dor_source");
    String copyExternalDORString = copyExternalDORBuffer.toString();
    when(session.query(copyExternalDORString, paramsMap)).thenReturn(copyExternalDORResult);
    when(copyExternalDORResult.queryStatistics()).thenReturn(queryStatistics);
    when(queryStatistics.containsUpdates()).thenReturn(true);
    dao.copyDataObjectReferences(sourceVersionUID, targetVersionUID);
    verify(session).query(copyExternalDORString, paramsMap);
    verify(session).query(copyInternalDORString, paramsMap);
    verify(session).query(attachVersionAndCreatedByString, paramsMap);
  }

  //@Test
  public void copyFileReferencesTest() {
    UUID sourceVersionUID = new UUID(0L, 1L);
    UUID targetVersionUID = new UUID(2L, 3L);
    Map<String, Object> paramsMap = new HashMap<>();
    StringBuffer copyReferenceQueryBuffer = new StringBuffer();
    copyReferenceQueryBuffer.append(
      "MATCH (v_source:Version)<-[has_version]-(do_source:DataObject)-[:has_reference]->(fr_source:FileReference)-[:is_in_container]->(fc_pointed:FileContainer), "
    );
    copyReferenceQueryBuffer.append("(fr_source)-[:created_by]->(u_creator:User), ");
    copyReferenceQueryBuffer.append("(v_target:Version)<-[:has_version]-(do_target:DataObject) ");
    copyReferenceQueryBuffer.append(
      "WHERE v_source.uid = '" +
      sourceVersionUID +
      "' AND v_target.uid = '" +
      targetVersionUID +
      "' AND do_source.shepardId = do_target.shepardId "
    );
    copyReferenceQueryBuffer.append(
      "CREATE (v_target)<-[:has_version]-(fr_target:FileReference:BasicReference:VersionableEntity:BasicEntity)<-[:has_reference]-(do_target), "
    );
    copyReferenceQueryBuffer.append("(fc_pointed)<-[:is_in_container]-(fr_target)-[:created_by]->(u_creator) ");
    copyReferenceQueryBuffer.append("SET fr_target = fr_source");
    String copyReferenceQuery = copyReferenceQueryBuffer.toString();
    when(session.query(copyReferenceQuery, paramsMap)).thenReturn(copyFileReferencesResult);
    when(copyFileReferencesResult.queryStatistics()).thenReturn(queryStatistics);
    StringBuffer copyPayloadQueryBuffer = new StringBuffer();
    copyPayloadQueryBuffer.append(
      "MATCH (v_source:Version)<-[:has_version]-(fr_source:FileReference)-[:has_payload]->(sf:ShepardFile), "
    );
    copyPayloadQueryBuffer.append("(v_target:Version)<-[:has_version]-(fr_target:FileReference) ");
    copyPayloadQueryBuffer.append(
      "WHERE v_source.uid = '" +
      sourceVersionUID +
      "' AND v_target.uid = '" +
      targetVersionUID +
      "' AND fr_source.shepardId = fr_target.shepardId "
    );
    copyPayloadQueryBuffer.append("CREATE (fr_target)-[:has_payload]->(sf)");
    String copyPayloadQuery = copyPayloadQueryBuffer.toString();
    when(session.query(copyPayloadQuery, paramsMap)).thenReturn(copyFilePayloadResult);
    when(copyFilePayloadResult.queryStatistics()).thenReturn(queryStatistics);
    when(queryStatistics.containsUpdates()).thenReturn(true);
    dao.copyFileReferences(sourceVersionUID, targetVersionUID);
    verify(session).query(copyReferenceQuery, paramsMap);
    verify(session).query(copyPayloadQuery, paramsMap);
  }

  //@Test
  public void copyStructuredDataReferencesTest() {
    UUID sourceVersionUID = new UUID(0L, 1L);
    UUID targetVersionUID = new UUID(2L, 3L);
    Map<String, Object> paramsMap = new HashMap<>();
    StringBuffer copyReferenceQueryBuffer = new StringBuffer();
    copyReferenceQueryBuffer.append(
      "MATCH (v_source:Version)<-[has_version]-(do_source:DataObject)-[:has_reference]->(sdr_source:StructuredDataReference)-[:is_in_container]->(sdc_pointed:StructuredDataContainer), "
    );
    copyReferenceQueryBuffer.append("(sdr_source)-[:created_by]->(u_creator:User), ");
    copyReferenceQueryBuffer.append("(v_target:Version)<-[:has_version]-(do_target:DataObject) ");
    copyReferenceQueryBuffer.append(
      "WHERE v_source.uid = '" +
      sourceVersionUID +
      "' AND v_target.uid = '" +
      targetVersionUID +
      "' AND do_source.shepardId = do_target.shepardId "
    );
    copyReferenceQueryBuffer.append(
      "CREATE (v_target)<-[:has_version]-(sdr_target:StructuredDataReference:BasicReference:VersionableEntity:BasicEntity)<-[:has_reference]-(do_target), "
    );
    copyReferenceQueryBuffer.append("(sdc_pointed)<-[:is_in_container]-(sdr_target)-[:created_by]->(u_creator) ");
    copyReferenceQueryBuffer.append("SET sdr_target = sdr_source");
    String copyReferenceQuery = copyReferenceQueryBuffer.toString();
    when(session.query(copyReferenceQuery, paramsMap)).thenReturn(copyStructuredDataReferencesResult);
    when(copyStructuredDataReferencesResult.queryStatistics()).thenReturn(queryStatistics);
    StringBuffer copyPayloadQueryBuffer = new StringBuffer();
    copyPayloadQueryBuffer.append(
      "MATCH (v_source:Version)<-[:has_version]-(sdr_source:StructuredDataReference)-[:has_payload]->(sd:StructuredData), "
    );
    copyPayloadQueryBuffer.append("(v_target:Version)<-[:has_version]-(sdr_target:StructuredDataReference) ");
    copyPayloadQueryBuffer.append(
      "WHERE v_source.uid = '" +
      sourceVersionUID +
      "' AND v_target.uid = '" +
      targetVersionUID +
      "' AND sdr_source.shepardId = sdr_target.shepardId "
    );
    copyPayloadQueryBuffer.append("CREATE (sdr_target)-[:has_payload]->(sd)");
    String copyPayloadQuery = copyPayloadQueryBuffer.toString();
    when(session.query(copyPayloadQuery, paramsMap)).thenReturn(copyStructuredDataPayloadResult);
    when(copyStructuredDataPayloadResult.queryStatistics()).thenReturn(queryStatistics);
    when(queryStatistics.containsUpdates()).thenReturn(true);
    dao.copyStructuredDataReferences(sourceVersionUID, targetVersionUID);
    verify(session).query(copyReferenceQuery, paramsMap);
    verify(session).query(copyPayloadQuery, paramsMap);
  }

  //@Test
  public void copyTimeseriesReferencesTest() {
    UUID sourceVersionUID = new UUID(0L, 1L);
    UUID targetVersionUID = new UUID(2L, 3L);
    Map<String, Object> paramsMap = new HashMap<>();
    StringBuffer copyReferenceQueryBuffer = new StringBuffer();
    copyReferenceQueryBuffer.append(
      "MATCH (v_source:Version)<-[has_version]-(do_source:DataObject)-[:has_reference]->(tsr_source:TimeseriesReference)-[:is_in_container]->(tsc_pointed:TimeseriesContainer), "
    );
    copyReferenceQueryBuffer.append("(tsr_source)-[:created_by]->(u_creator:User), ");
    copyReferenceQueryBuffer.append("(v_target:Version)<-[:has_version]-(do_target:DataObject) ");
    copyReferenceQueryBuffer.append(
      "WHERE v_source.uid = '" +
      sourceVersionUID +
      "' AND v_target.uid = '" +
      targetVersionUID +
      "' AND do_source.shepardId = do_target.shepardId "
    );
    copyReferenceQueryBuffer.append(
      "CREATE (v_target)<-[:has_version]-(tsr_target:TimeseriesReference:BasicReference:VersionableEntity:BasicEntity)<-[:has_reference]-(do_target), "
    );
    copyReferenceQueryBuffer.append("(tsc_pointed)<-[:is_in_container]-(tsr_target)-[:created_by]->(u_creator) ");
    copyReferenceQueryBuffer.append("SET tsr_target = tsr_source");
    String copyReferenceQuery = copyReferenceQueryBuffer.toString();
    when(session.query(copyReferenceQuery, paramsMap)).thenReturn(copyTimeseriesReferencesResult);
    when(copyTimeseriesReferencesResult.queryStatistics()).thenReturn(queryStatistics);
    StringBuffer copyPayloadQueryBuffer = new StringBuffer();
    copyPayloadQueryBuffer.append(
      "MATCH (v_source:Version)<-[:has_version]-(tsr_source:TimeseriesReference)-[:has_payload]->(ts:Timeseries), "
    );
    copyPayloadQueryBuffer.append("(v_target:Version)<-[:has_version]-(tsr_target:TimeseriesReference) ");
    copyPayloadQueryBuffer.append(
      "WHERE v_source.uid = '" +
      sourceVersionUID +
      "' AND v_target.uid = '" +
      targetVersionUID +
      "' AND tsr_source.shepardId = tsr_target.shepardId "
    );
    copyPayloadQueryBuffer.append("CREATE (tsr_target)-[:has_payload]->(ts)");
    String copyPayloadQuery = copyPayloadQueryBuffer.toString();
    when(session.query(copyPayloadQuery, paramsMap)).thenReturn(copyTimeseriesPayloadResult);
    when(copyTimeseriesPayloadResult.queryStatistics()).thenReturn(queryStatistics);
    when(queryStatistics.containsUpdates()).thenReturn(true);
    dao.copyTimeseriesReferences(sourceVersionUID, targetVersionUID);
    verify(session).query(copyReferenceQuery, paramsMap);
    verify(session).query(copyPayloadQuery, paramsMap);
  }

  //@Test
  public void copyCollectionReferencesTest() {
    UUID sourceVersionUID = new UUID(0L, 1L);
    UUID targetVersionUID = new UUID(2L, 3L);
    Map<String, Object> paramsMap = new HashMap<>();
    StringBuffer queryBuffer = new StringBuffer();
    queryBuffer.append(
      "MATCH (v_source:Version)<-[has_version]-(do_source:DataObject)-[:has_reference]->(cr_source:CollectionReference)-[:points_to]->(c_pointed:Collection), "
    );
    queryBuffer.append("(cr_source)-[:created_by]->(u_creator:User), ");
    queryBuffer.append("(v_target:Version)<-[:has_version]-(do_target:DataObject) ");
    queryBuffer.append(
      "WHERE v_source.uid = '" +
      sourceVersionUID +
      "' AND v_target.uid = '" +
      targetVersionUID +
      "' AND do_source.shepardId = do_target.shepardId "
    );
    queryBuffer.append(
      "CREATE (v_target)<-[:has_version]-(cr_target:CollectionReference:BasicReference)<-[:has_reference]-(do_target), "
    );
    queryBuffer.append("(c_pointed)<-[:points_to]-(cr_target)-[:created_by]->(u_creator) ");
    queryBuffer.append("SET cr_target = cr_source");
    String copyReferenceQuery = queryBuffer.toString();
    when(session.query(copyReferenceQuery, paramsMap)).thenReturn(copyCollectionReferencesResult);
    when(copyCollectionReferencesResult.queryStatistics()).thenReturn(queryStatistics);
    when(queryStatistics.containsUpdates()).thenReturn(true);
    dao.copyCollectionReferences(sourceVersionUID, targetVersionUID);
    verify(session).query(copyReferenceQuery, paramsMap);
  }

  //@Test
  public void copyURIReferencesTest() {
    UUID sourceVersionUID = new UUID(0L, 1L);
    UUID targetVersionUID = new UUID(2L, 3L);
    Map<String, Object> paramsMap = new HashMap<>();
    StringBuffer queryBuffer = new StringBuffer();
    queryBuffer.append(
      "MATCH (v_source:Version)<-[:has_version]-(do_source:DataObject)-[:has_reference]->(ur_source:URIReference)-[:created_by]->(u_creator:User), "
    );
    queryBuffer.append("(v_target:Version)<-[:has_version]-(do_target:DataObject) ");
    queryBuffer.append(
      "WHERE v_source.uid = '" +
      sourceVersionUID +
      "' AND v_target.uid = '" +
      targetVersionUID +
      "' AND do_source.shepardId = do_target.shepardId "
    );
    queryBuffer.append(
      "CREATE (v_target)<-[:has_version]-(ur_target:URIReference:BasicReference:VersionableEntity:BasicEntity)<-[:has_reference]-(do_target), "
    );
    queryBuffer.append("(ur_target)-[:created_by]->(u_creator) ");
    queryBuffer.append("SET ur_target=ur_source");
    String copyReferenceQuery = queryBuffer.toString();
    when(session.query(copyReferenceQuery, paramsMap)).thenReturn(copyURIReferencesResult);
    when(copyURIReferencesResult.queryStatistics()).thenReturn(queryStatistics);
    when(queryStatistics.containsUpdates()).thenReturn(true);
    dao.copyURIReferences(sourceVersionUID, targetVersionUID);
    verify(session).query(copyReferenceQuery, paramsMap);
  }
}
