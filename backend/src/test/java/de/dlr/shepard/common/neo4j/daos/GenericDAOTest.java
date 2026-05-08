package de.dlr.shepard.common.neo4j.daos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.identifier.HasAppId;
import de.dlr.shepard.common.util.TraversalRules;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.Data;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;
import org.neo4j.ogm.cypher.query.Pagination;
import org.neo4j.ogm.model.QueryStatistics;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.Session;

public class GenericDAOTest extends BaseTestCase {

  @Data
  private static class TestObject {

    private final int a;
  }

  private static class TestDAO extends GenericDAO<TestObject> {

    @Override
    public Class<TestObject> getEntityType() {
      return TestObject.class;
    }
  }

  /**
   * Mirrors the shape of a real {@code @NodeEntity} that implements
   * {@link HasAppId} via the inherited base class — it lets us exercise the
   * L2a write-side seam in {@link GenericDAO#createOrUpdate(Object)} without
   * needing a real Neo4j session.
   */
  @Data
  private static class TestObjectWithAppId implements HasAppId {

    private String appId;
    private final int a;
  }

  private static class TestDAOWithAppId extends GenericDAO<TestObjectWithAppId> {

    @Override
    public Class<TestObjectWithAppId> getEntityType() {
      return TestObjectWithAppId.class;
    }
  }

  @Mock
  private Session session;

  @Mock
  private EntityIdResolver entityIdResolver;

  @InjectMocks
  private TestDAO dao = new TestDAO();

  @Captor
  ArgumentCaptor<Pagination> paginationCaptor;

  @BeforeEach
  public void primeResolver() {
    // L2c: getSearchForReachableReferences* now translate the OGM longs to
    // appIds via the resolver. Prime the well-known ids used in this fixture.
    org.mockito.Mockito.lenient().when(entityIdResolver.resolveAppId(1L)).thenReturn("appid-1");
    org.mockito.Mockito.lenient().when(entityIdResolver.resolveAppId(2L)).thenReturn("appid-2");
  }

  @Test
  public void findAllTest() {
    var a = new TestObject(1);
    var b = new TestObject(2);

    when(session.loadAll(TestObject.class, 1)).thenReturn(List.of(a, b));
    var actual = dao.findAll();
    assertEquals(List.of(a, b), actual);
  }

  @Test
  public void findMatchingTest() {
    var a = new TestObject(1);
    var filter = new Filter("a", ComparisonOperator.EQUALS, 1);

    when(session.loadAll(TestObject.class, filter, 1)).thenReturn(List.of(a));
    var actual = dao.findMatching(filter);
    assertEquals(List.of(a), actual);
  }

  @Test
  public void findTest() {
    var a = new TestObject(1);

    when(session.load(TestObject.class, 1L, 1)).thenReturn(a);
    var actual = dao.findByNeo4jId(1L);
    assertEquals(a, actual);
  }

  @Test
  public void findLightTest() {
    var a = new TestObject(1);

    when(session.load(TestObject.class, 1L, 0)).thenReturn(a);
    var actual = dao.findLightByNeo4jId(1L);
    assertEquals(a, actual);
  }

  @Test
  public void deleteTest_Successful() {
    var a = new TestObject(1);

    when(session.load(TestObject.class, 1L)).thenReturn(a);
    doNothing().when(session).delete(a);
    var actual = dao.deleteByNeo4jId(1L);
    assertTrue(actual);
  }

  @Test
  public void deleteTest_Error() {
    var a = new TestObject(1);

    when(session.load(TestObject.class, 1L)).thenReturn(null);
    doNothing().when(session).delete(a);
    var actual = dao.deleteByNeo4jId(1L);
    assertFalse(actual);
  }

  @Test
  public void createOrUpdateTest() {
    var a = new TestObject(1);

    doNothing().when(session).save(a, 1);
    var actual = dao.createOrUpdate(a);
    assertEquals(a, actual);
  }

  @Test
  public void createOrUpdate_setsAppId_whenEntityIsHasAppIdAndAppIdIsNull() {
    // L2a: the DAO seam must mint a UUID v7 on the way to session.save().
    var appIdDao = new TestDAOWithAppId();
    var sessionMock = mock(Session.class);
    appIdDao.session = sessionMock;
    var entity = new TestObjectWithAppId(1);
    assertNull(entity.getAppId(), "precondition: appId starts null");

    var saved = appIdDao.createOrUpdate(entity);

    assertNotNull(saved.getAppId(), "appId should be populated after save()");
    assertEquals(36, saved.getAppId().length(), "appId should be a canonical 36-char UUID");
    // Parses cleanly as a UUID and is version 7.
    var parsed = UUID.fromString(saved.getAppId());
    assertEquals(7, parsed.version());
    verify(sessionMock).save(entity, 1);
  }

  @Test
  public void createOrUpdate_doesNotOverwriteExistingAppId() {
    // Idempotency: re-saving an already-tagged entity must not mint a new id.
    var appIdDao = new TestDAOWithAppId();
    var sessionMock = mock(Session.class);
    appIdDao.session = sessionMock;
    var entity = new TestObjectWithAppId(1);
    var existing = "0190d1f8-7c4d-7d8a-91a5-b7c2d3e4f506";
    entity.setAppId(existing);

    var saved = appIdDao.createOrUpdate(entity);

    assertEquals(existing, saved.getAppId(), "existing appId must be preserved");
    verify(sessionMock).save(entity, 1);
  }

  @Test
  public void createOrUpdate_legacyEntityWithoutHasAppId_isUntouched() {
    // Defensive: TestObject does not implement HasAppId. The seam must be a
    // no-op for it (read paths and legacy node types remain unaffected).
    var legacyDao = new TestDAO();
    var sessionMock = mock(Session.class);
    legacyDao.session = sessionMock;
    var legacy = new TestObject(1);

    var saved = legacyDao.createOrUpdate(legacy);

    assertEquals(legacy, saved);
    verify(sessionMock).save(legacy, 1);
  }

  @Test
  public void findByQueryTest() {
    var a = new TestObject(1);
    var query = "MATCH (n {a: 1}) RETURN n";
    Map<String, Object> params = Map.of("a", "b", "c", "d");

    when(session.query(TestObject.class, query, params)).thenReturn(List.of(a));
    var actual = dao.findByQuery(query, params);
    assertEquals(List.of(a), actual);
  }

  @Test
  public void runQueryTest() {
    var query = "MATCH (n {a: 1}) RETURN n";
    Map<String, Object> params = Map.of("a", "b", "c", "d");
    Result result = mock(Result.class);
    QueryStatistics stat = mock(QueryStatistics.class);

    when(session.query(query, params)).thenReturn(result);
    when(result.queryStatistics()).thenReturn(stat);
    when(stat.containsUpdates()).thenReturn(true);

    var actual = dao.runQuery(query, params);
    assertTrue(actual);
  }

  @Test
  public void getSearchForReachableReferencesQueryStartIdTest() {
    // L2c: id(d) / id(col) flipped to d.appId / col.appId, parameters renamed accordingly.
    long startId = 1L;
    long collectionId = 2L;
    String userName = "user";
    String startIdQuery =
      "MATCH path = (col:Collection)-[:has_dataobject]->(d:DataObject)-[hr:has_reference]->(r:TestObject) WITH nodes(path) as ns, r as ret WHERE d.appId = $startAppId AND col.appId = $collectionAppId AND NONE(node IN ns WHERE (node.deleted = TRUE)) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"user\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"user\"}))) MATCH path=(ret)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL RETURN ret, nodes(path), relationships(path)";
    var actual = dao.getSearchForReachableReferencesQuery(collectionId, startId, userName);
    assertEquals(startIdQuery, actual.cypher());
    assertEquals(Map.of("startAppId", "appid-1", "collectionAppId", "appid-2"), actual.params());
  }

  @Test
  public void getSearchForReachableReferencesByShepardIdQueryStartIdTest() {
    long startShepardId = 11L;
    long collectionShepardId = 21L;
    String userName = "user";
    String startIdQuery =
      "MATCH path = (col:Collection)-[:has_dataobject]->(d:DataObject)-[hr:has_reference]->(r:TestObject) WITH nodes(path) as ns, r as ret WHERE d.shepardId = 11 AND col.shepardId = 21 AND NONE(node IN ns WHERE (node.deleted = TRUE)) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"user\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"user\"}))) MATCH path=(ret)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL RETURN ret, nodes(path), relationships(path)";
    var actual = dao.getSearchForReachableReferencesByShepardIdQuery(collectionShepardId, startShepardId, userName);
    assertEquals(startIdQuery, actual);
  }

  @Test
  public void getSearchForReachableReferencesByShepardIdWithoutStartIdQueryStartIdTest() {
    long collectionShepardId = 21L;
    String username = "Leonard Bernstein";
    String startIdQuery =
      "MATCH path = (col:Collection)-[:has_dataobject]->(do:DataObject)-[hr:has_reference]->(r:TestObject) WITH nodes(path) as ns, r as ret WHERE col.shepardId = 21 AND NONE(node IN ns WHERE (node.deleted = TRUE)) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"Leonard Bernstein\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"Leonard Bernstein\"}))) MATCH path=(ret)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL RETURN ret, nodes(path), relationships(path)";
    var actual = dao.getSearchForReachableReferencesByShepardIdQuery(collectionShepardId, username);
    assertEquals(startIdQuery, actual);
  }

  private static Stream<Arguments> getSearchForReachableReferencesByShepardIdQueryTest() {
    String childrenQuery =
      "MATCH path = (col:Collection)-[:has_dataobject]->(d:DataObject)-[:has_child*0..]->(e:DataObject)-[hr:has_reference]->(r:TestObject) WITH nodes(path) as ns, r as ret WHERE d.shepardId = 11 AND col.shepardId = 21 AND NONE(node IN ns WHERE (node.deleted = TRUE)) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"user\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"user\"}))) MATCH path=(ret)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL RETURN ret, nodes(path), relationships(path)";
    String parentsQuery =
      "MATCH path = (col:Collection)-[:has_dataobject]->(d:DataObject)<-[:has_child*0..]-(e:DataObject)-[hr:has_reference]->(r:TestObject) WITH nodes(path) as ns, r as ret WHERE d.shepardId = 11 AND col.shepardId = 21 AND NONE(node IN ns WHERE (node.deleted = TRUE)) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"user\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"user\"}))) MATCH path=(ret)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL RETURN ret, nodes(path), relationships(path)";
    String predecessorsQuery =
      "MATCH path = (col:Collection)-[:has_dataobject]->(d:DataObject)<-[:has_successor*0..]-(e:DataObject)-[hr:has_reference]->(r:TestObject) WITH nodes(path) as ns, r as ret WHERE d.shepardId = 11 AND col.shepardId = 21 AND NONE(node IN ns WHERE (node.deleted = TRUE)) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"user\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"user\"}))) MATCH path=(ret)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL RETURN ret, nodes(path), relationships(path)";
    String successorsQuery =
      "MATCH path = (col:Collection)-[:has_dataobject]->(d:DataObject)-[:has_successor*0..]->(e:DataObject)-[hr:has_reference]->(r:TestObject) WITH nodes(path) as ns, r as ret WHERE d.shepardId = 11 AND col.shepardId = 21 AND NONE(node IN ns WHERE (node.deleted = TRUE)) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"user\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"user\"}))) MATCH path=(ret)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL RETURN ret, nodes(path), relationships(path)";
    // @formatter:off
	    return Stream.of(
	    		Arguments.of(TraversalRules.children, childrenQuery),
	    		Arguments.of(TraversalRules.parents, parentsQuery),
	    		Arguments.of(TraversalRules.predecessors, predecessorsQuery),
	    		Arguments.of(TraversalRules.successors, successorsQuery)
	    	    );
    // @formatter:on
  }

  @ParameterizedTest
  @MethodSource
  public void getSearchForReachableReferencesByShepardIdQueryTest(TraversalRules traversalRules, String expected) {
    long startShepardId = 11L;
    long collectionShepardId = 21L;
    String userName = "user";
    var actual = dao.getSearchForReachableReferencesByShepardIdQuery(
      traversalRules,
      collectionShepardId,
      startShepardId,
      userName
    );
    assertEquals(expected, actual);
  }

  @Test
  public void getSearchForReachableReferencesByNeo4jIdQueryTest() {
    // L2c: id(d) / id(col) flipped to appId equality for the traversal-rule variant.
    long startId = 1L;
    long collectionId = 2L;
    String userName = "user";
    var actual = dao.getSearchForReachableReferencesByNeo4jIdQuery(
      TraversalRules.children,
      collectionId,
      startId,
      userName
    );
    String expected =
      "MATCH path = (col:Collection)-[:has_dataobject]->(d:DataObject)-[:has_child*0..]->(e:DataObject)-[hr:has_reference]->(r:TestObject) WITH nodes(path) as ns, r as ret WHERE d.appId = $startAppId AND col.appId = $collectionAppId AND NONE(node IN ns WHERE (node.deleted = TRUE)) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"user\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"user\"}))) MATCH path=(ret)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL RETURN ret, nodes(path), relationships(path)";
    assertEquals(expected, actual.cypher());
    assertEquals(Map.of("startAppId", "appid-1", "collectionAppId", "appid-2"), actual.params());
  }

  @Test
  public void getSearchForReachableReferencesQueryCollectionIdOnly() {
    // L2c: id(col) flipped to col.appId; resolver translates the OGM long.
    long collectionId = 1L;
    String userName = "user";
    String expected =
      "MATCH path = (col:Collection)-[:has_dataobject]->(do:DataObject)-[hr:has_reference]->(r:TestObject) WITH nodes(path) as ns, r as ret WHERE col.appId = $collectionAppId AND NONE(node IN ns WHERE (node.deleted = TRUE)) AND (NOT exists((col)-[:has_permissions]->(:Permissions)) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: \"user\" })) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"Public\"})) OR exists((col)-[:has_permissions]->(:Permissions {permissionType: \"PublicReadable\"})) OR exists((col)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: \"user\"}))) MATCH path=(ret)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL RETURN ret, nodes(path), relationships(path)";
    var actual = dao.getSearchForReachableReferencesQuery(collectionId, userName);
    assertEquals(expected, actual.cypher());
    assertEquals(Map.of("collectionAppId", "appid-1"), actual.params());
  }

  //new test
  @Test
  public void deleteRelationTest() {
    long fromId = 1L;
    long toId = 2L;
    String fromType = "fromType";
    String toType = "toType";
    String relationName = "relationName";
    dao.deleteRelation(fromId, toId, fromType, toType, relationName);
    String expected = "MATCH (a:fromType {shepardId: 1})-[r:relationName]->(b:toType {shepardId: 2}) DELETE r;";
    verify(session).query(eq(expected), any());
  }
}
