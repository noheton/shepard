package de.dlr.shepard.context.version.daos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.common.util.CypherQueryHelper;
import de.dlr.shepard.context.version.entities.VersionableEntity;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.session.Session;

public class VersionableEntityDAOTest extends BaseTestCase {

  private static class TestObject extends VersionableEntity {}

  private static class TestDAO extends VersionableEntityDAO<TestObject> {

    @Override
    public Class<TestObject> getEntityType() {
      return TestObject.class;
    }
  }

  @Mock
  private Session session;

  @InjectMocks
  private TestDAO dao = new TestDAO();

  @Test
  public void findByShepardIdTest() {
    TestObject ent = new TestObject();
    ent.setId(1L);
    ent.setShepardId(11L);
    Map<String, Object> paramsMap = new HashMap<>();
    String query =
      "MATCH (o:TestObject {deleted: FALSE})-[:has_version]->(v:Version) WHERE o.shepardId in " +
      "[" +
      ent.getShepardId() +
      "]" +
      " AND " +
      CypherQueryHelper.getVersionHeadPart("v") +
      " WITH o MATCH path=(o)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL RETURN o, nodes(path), relationships(path)";
    when(session.query(TestObject.class, query, paramsMap)).thenReturn(List.of(ent));
    VersionableEntity actual = dao.findByShepardId(ent.getShepardId());
    assertEquals(ent, actual);
  }

  @Test
  public void findByShepardIdWithVersionUIDTest() {
    TestObject ent = new TestObject();
    ent.setId(1L);
    ent.setShepardId(11L);
    UUID versionUID = new UUID(1L, 2L);
    Map<String, Object> paramsMap = new HashMap<>();
    String query =
      "MATCH (o:TestObject {deleted: FALSE})-[:has_version]->(v:Version) WHERE o.shepardId = 11 AND (v.uid = '00000000-0000-0001-0000-000000000002') WITH o MATCH path=(o)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL RETURN o, nodes(path), relationships(path)";
    when(session.query(TestObject.class, query, paramsMap)).thenReturn(List.of(ent));
    VersionableEntity actual = dao.findByShepardId(ent.getShepardId(), versionUID);
    assertEquals(ent, actual);
  }

  @Test
  public void findByShepardIdNotFoundTest() {
    TestObject ent = new TestObject();
    ent.setId(1L);
    ent.setShepardId(11L);
    Map<String, Object> paramsMap = new HashMap<>();
    String query =
      "MATCH (o {deleted: FALSE}) WHERE o.shepardId = 11 WITH o MATCH path=(o)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL RETURN o, nodes(path), relationships(path)";
    when(session.query(TestObject.class, query, paramsMap)).thenReturn(Collections.emptyList());
    VersionableEntity actual = dao.findByShepardId(ent.getShepardId());
    assertEquals(null, actual);
  }

  @Test
  public void findLightByShepardIdTest() {
    TestObject ent = new TestObject();
    ent.setId(1L);
    ent.setShepardId(11L);
    Map<String, Object> paramsMap = new HashMap<>();
    String query =
      "MATCH (o:TestObject {deleted: FALSE})-[:has_version]->(v:Version) WHERE o.shepardId in " +
      "[" +
      ent.getShepardId() +
      "]" +
      " AND " +
      CypherQueryHelper.getVersionHeadPart("v") +
      " WITH o RETURN o";
    when(session.query(TestObject.class, query, paramsMap)).thenReturn(List.of(ent));
    VersionableEntity actual = dao.findByShepardId(ent.getShepardId(), true);
    assertEquals(ent, actual);
  }

  @Test
  public void findLightByShepardIdNotFoundTest() {
    TestObject ent = new TestObject();
    ent.setId(1L);
    ent.setShepardId(11L);
    Map<String, Object> paramsMap = new HashMap<>();
    String query = "MATCH (o {deleted: FALSE}) WHERE o.shepardId = 11 WITH o RETURN o";
    when(session.query(TestObject.class, query, paramsMap)).thenReturn(Collections.emptyList());
    VersionableEntity actual = dao.findByShepardId(ent.getShepardId(), true);
    assertEquals(null, actual);
  }
}
