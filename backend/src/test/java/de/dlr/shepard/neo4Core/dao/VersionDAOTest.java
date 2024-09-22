package de.dlr.shepard.neo4Core.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.entities.Version;
import de.dlr.shepard.util.CypherQueryHelper;
import de.dlr.shepard.util.CypherQueryHelper.Neighborhood;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.model.QueryStatistics;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.Session;

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
    Version ret = null;
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

  @Test
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

  @Test
  public void copyDataObjectTest() {
    UUID sourceVersionUID = new UUID(0L, 1L);
    UUID targetVersionUID = new UUID(2L, 3L);
    Map<String, Object> paramsMap = new HashMap<>();
    String query =
      "MATCH (do_source:DataObject)-[:has_version]->(v_source:Version)-[:has_predecessor]->(v_target:Version)<-[:has_version]-(col_target:Collection) WHERE v_source.uid = '00000000-0000-0000-0000-000000000001' AND v_target.uid = '00000000-0000-0002-0000-000000000003'  CREATE (col_target)-[:has_dataobject]->(do_target:DataObject)-[:has_version]->(v_target)  SET do_target = do_source";
    when(session.query(query, paramsMap)).thenReturn(result);
    when(result.queryStatistics()).thenReturn(queryStatistics);
    when(queryStatistics.containsUpdates()).thenReturn(true);
    dao.copyDataObjects(sourceVersionUID, targetVersionUID);
    verify(session).query(query, paramsMap);
  }

  @Test
  public void copyChildRelationsTest() {
    UUID sourceVersionUID = new UUID(0L, 1L);
    UUID targetVersionUID = new UUID(2L, 3L);
    Map<String, Object> paramsMap = new HashMap<>();
    String query =
      "MATCH (do_source_parent:DataObject)-[:has_child]->(do_source_child:DataObject)-[:has_version]->(v_source:Version)-[:has_predecessor]->(v_target:Version)<-[:has_version]-(do_target_parent:DataObject), (v_target)<-[:has_version]-(do_target_child:DataObject)  WHERE v_source.uid = '00000000-0000-0000-0000-000000000001' AND v_target.uid = '00000000-0000-0002-0000-000000000003'  AND do_source_parent.shepardId=do_target_parent.shepardId AND do_source_child.shepardId=do_target_child.shepardId  CREATE (do_target_parent)-[:has_child]->(do_target_child)";
    when(session.query(query, paramsMap)).thenReturn(result);
    when(result.queryStatistics()).thenReturn(queryStatistics);
    when(queryStatistics.containsUpdates()).thenReturn(true);
    dao.copyChildRelations(sourceVersionUID, targetVersionUID);
    verify(session).query(query, paramsMap);
  }

  @Test
  public void copySuccessorRelationsTest() {
    UUID sourceVersionUID = new UUID(0L, 1L);
    UUID targetVersionUID = new UUID(2L, 3L);
    Map<String, Object> paramsMap = new HashMap<>();
    String query =
      "MATCH (do_source_predecessor:DataObject)-[:has_successor]->(do_source_successor:DataObject)-[:has_version]->(v_source:Version)-[:has_predecessor]->(v_target:Version)<-[:has_version]-(do_target_predecessor:DataObject), (v_target)<-[:has_version]-(do_target_successor:DataObject)  WHERE v_source.uid = '00000000-0000-0000-0000-000000000001' AND v_target.uid = '00000000-0000-0002-0000-000000000003'  AND do_source_predecessor.shepardId=do_target_predecessor.shepardId AND do_source_successor.shepardId=do_target_successor.shepardId  CREATE (do_target_predecessor)-[:has_successor]->(do_target_successor)";
    when(session.query(query, paramsMap)).thenReturn(result);
    when(result.queryStatistics()).thenReturn(queryStatistics);
    when(queryStatistics.containsUpdates()).thenReturn(true);
    dao.copySuccessorRelations(sourceVersionUID, targetVersionUID);
    verify(session).query(query, paramsMap);
  }

  @Test
  public void copyDataObjectsWithParentsAndPredecessorsTest() {
    UUID sourceVersionUID = new UUID(0L, 1L);
    UUID targetVersionUID = new UUID(2L, 3L);
    String successorsQuery =
      "MATCH (do_source_predecessor:DataObject)-[:has_successor]->(do_source_successor:DataObject)-[:has_version]->(v_source:Version)-[:has_predecessor]->(v_target:Version)<-[:has_version]-(do_target_predecessor:DataObject), (v_target)<-[:has_version]-(do_target_successor:DataObject)  WHERE v_source.uid = '00000000-0000-0000-0000-000000000001' AND v_target.uid = '00000000-0000-0002-0000-000000000003'  AND do_source_predecessor.shepardId=do_target_predecessor.shepardId AND do_source_successor.shepardId=do_target_successor.shepardId  CREATE (do_target_predecessor)-[:has_successor]->(do_target_successor)";
    Map<String, Object> paramsMap = new HashMap<>();
    when(session.query(successorsQuery, paramsMap)).thenReturn(successorResult);
    when(successorResult.queryStatistics()).thenReturn(queryStatistics);
    String childQuery =
      "MATCH (do_source_parent:DataObject)-[:has_child]->(do_source_child:DataObject)-[:has_version]->(v_source:Version)-[:has_predecessor]->(v_target:Version)<-[:has_version]-(do_target_parent:DataObject), (v_target)<-[:has_version]-(do_target_child:DataObject)  WHERE v_source.uid = '00000000-0000-0000-0000-000000000001' AND v_target.uid = '00000000-0000-0002-0000-000000000003'  AND do_source_parent.shepardId=do_target_parent.shepardId AND do_source_child.shepardId=do_target_child.shepardId  CREATE (do_target_parent)-[:has_child]->(do_target_child)";
    when(session.query(childQuery, paramsMap)).thenReturn(childResult);
    when(childResult.queryStatistics()).thenReturn(queryStatistics);
    String dataObjectQuery =
      "MATCH (do_source:DataObject)-[:has_version]->(v_source:Version)-[:has_predecessor]->(v_target:Version)<-[:has_version]-(col_target:Collection) WHERE v_source.uid = '00000000-0000-0000-0000-000000000001' AND v_target.uid = '00000000-0000-0002-0000-000000000003'  CREATE (col_target)-[:has_dataobject]->(do_target:DataObject)-[:has_version]->(v_target)  SET do_target = do_source";
    when(session.query(dataObjectQuery, paramsMap)).thenReturn(dataObjectResult);
    when(dataObjectResult.queryStatistics()).thenReturn(queryStatistics);
    when(queryStatistics.containsUpdates()).thenReturn(true);
    dao.copyDataObjectsWithParentsAndPredecessors(sourceVersionUID, targetVersionUID);
    verify(session).query(dataObjectQuery, paramsMap);
    verify(session).query(successorsQuery, paramsMap);
    verify(session).query(childQuery, paramsMap);
  }
}
