package de.dlr.shepard.neo4Core.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.entities.Version;
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
    Map<String, Object> paramsMap = new HashMap<>();
    long collectionId = 5L;
    String query =
      "MATCH (col:Collection)-[]->(ver:Version) WHERE col.shepardId = " +
      collectionId +
      " AND ver.uid = '00000000-0000-0001-0000-000000000002' AND col.deleted = false RETURN ver";
    Version ver = new Version();
    ver.setDescription("version");
    UUID id = new UUID(1L, 2L);
    ver.setUid(id);
    when(session.query(Version.class, query, paramsMap)).thenReturn(List.of(ver));
    when(session.load(Version.class, id, 1)).thenReturn(ver);
    Version found = dao.find(collectionId, id.toString());
    assertEquals(ver, found);
  }

  @Test
  public void findAllVersionsTest() {
    Version ver1 = new Version();
    Version ver2 = new Version();
    ver1.setName("name1");
    ver2.setName("ver2");
    long collectionId = 5;
    String query =
      "MATCH (col:Collection)-[]->(ver:Version) WHERE col.shepardId = " +
      collectionId +
      " AND col.deleted = false RETURN ver";
    Map<String, Object> paramsMap = new HashMap<>();
    when(session.query(Version.class, query, paramsMap)).thenReturn(List.of(ver1, ver2));
    List<Version> allVersions = dao.findAllVersions(collectionId);
    assertThat(allVersions.contains(ver1));
    assertThat(allVersions.contains(ver2));
  }

  @Test
  public void findHEADVersionUUIDTest() {
    Version ver = new Version();
    UUID uid = new UUID(1L, 2L);
    ver.setUid(uid);
    long collectionId = 5L;
    String query =
      "MATCH (c:Collection)-[:has_version]->(v:Version) WHERE c.shepardId = " +
      collectionId +
      " AND (NOT exists ((v)<-[:has_predecessor]-(:Version))) RETURN v";
    Map<String, Object> paramsMap = new HashMap<>();
    when(session.query(Version.class, query, paramsMap)).thenReturn(List.of(ver));
    UUID headVersionUUID = dao.findHEADVersionUUID(collectionId);
    assertEquals(headVersionUUID, uid);
  }

  @Test
  public void findHEADVersionTest() {
    Version ver = new Version();
    UUID uid = new UUID(1L, 2L);
    ver.setUid(uid);
    long collectionId = 5L;
    String query =
      "MATCH (c:Collection)-[:has_version]->(v:Version) WHERE c.shepardId = " +
      collectionId +
      " AND (NOT exists ((v)<-[:has_predecessor]-(:Version))) RETURN v";
    Map<String, Object> paramsMap = new HashMap<>();
    when(session.query(Version.class, query, paramsMap)).thenReturn(List.of(ver));
    Version headVersion = dao.findHEADVersion(collectionId);
    assertEquals(headVersion, ver);
  }

  @Test
  public void findVersionByNeo4jIdTest() {
    Version ver = new Version();
    ver.setName("name");
    long neo4jId = 10L;
    String query = "MATCH (ve:VersionableEntity)-[:has_version]->(v) WHERE id(ve) = " + neo4jId + " RETURN v";
    Map<String, Object> paramsMap = new HashMap<>();
    when(session.query(Version.class, query, paramsMap)).thenReturn(List.of(ver));
    Version found = dao.findVersionByNeo4jId(neo4jId);
    assertEquals(ver, found);
  }

  @Test
  public void createLinkTest() {
    long versionableEntityId = 15L;
    String versionUID = "123";
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
}
