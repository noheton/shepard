package de.dlr.shepard.neo4Core.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.entities.StructuredDataReference;
import de.dlr.shepard.util.TraversalRules;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.session.Session;

public class StructuredDataReferenceDAOTest extends BaseTestCase {

  @Mock
  private Session session;

  @InjectMocks
  private StructuredDataReferenceDAO dao;

  @Test
  public void getEntityTypeTest() {
    var type = dao.getEntityType();
    assertEquals(StructuredDataReference.class, type);
  }

  @Test
  public void findByDataObjectTest() {
    var obj = new DataObject(1L);
    var obj2 = new DataObject(100L);
    var ref = new StructuredDataReference(2L);
    var ref2 = new StructuredDataReference(3L);
    var ref3 = new StructuredDataReference(3L);
    ref.setDataObject(obj);
    ref2.setDataObject(obj2);

    var query =
      """
      MATCH (d:DataObject)-[hr:has_reference]->(r:StructuredDataReference { deleted: FALSE }) WHERE ID(d)=1 \
      MATCH path=(r)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL \
      RETURN r, nodes(path), relationships(path)""";
    when(session.query(StructuredDataReference.class, query, Collections.emptyMap())).thenReturn(
      List.of(ref, ref2, ref3)
    );

    var actual = dao.findByDataObjectNeo4jId(1L);
    verify(session).query(StructuredDataReference.class, query, Collections.emptyMap());
    assertEquals(List.of(ref), actual);
  }

  @Test
  public void findByDataObjectShepardIdTest() {
    var obj = new DataObject(1L);
    obj.setShepardId(11L);
    var obj2 = new DataObject(100L);
    obj2.setShepardId(1001L);
    var ref = new StructuredDataReference(2L);
    ref.setShepardId(21L);
    var ref2 = new StructuredDataReference(3L);
    ref2.setShepardId(31L);
    var ref3 = new StructuredDataReference(4L);
    ref3.setShepardId(41L);
    ref.setDataObject(obj);
    ref2.setDataObject(obj2);

    String query =
      "MATCH (d:DataObject)-[hr:has_reference]->(r:StructuredDataReference { deleted: FALSE }) WHERE d.shepardId=11 MATCH path=(r)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL RETURN r, nodes(path), relationships(path)";
    when(session.query(StructuredDataReference.class, query, Collections.emptyMap())).thenReturn(
      List.of(ref, ref2, ref3)
    );

    var actual = dao.findByDataObjectShepardId(obj.getShepardId());
    verify(session).query(StructuredDataReference.class, query, Collections.emptyMap());
    assertEquals(List.of(ref), actual);
  }

  @Test
  public void findReachableReferencesStartIdTest() {
    long startId = 1L;
    long collectionId = 2L;
    String userName = "user";
    String query = dao.getSearchForReachableReferencesQuery(collectionId, startId, userName);
    StructuredDataReference reference = new StructuredDataReference();
    reference.setId(3L);
    when(dao.findByQuery(query, Collections.emptyMap())).thenReturn(List.of(reference));
    var actual = dao.findReachableReferencesByNeo4jId(collectionId, startId, userName);
    assertEquals(List.of(reference), actual);
  }

  @Test
  public void findReachableReferencesByShepardIdStartIdTest() {
    long startShepardId = 11L;
    long collectionShepardId = 21L;
    String userName = "user";
    String query = dao.getSearchForReachableReferencesByShepardIdQuery(collectionShepardId, startShepardId, userName);
    StructuredDataReference reference = new StructuredDataReference();
    reference.setId(3L);
    when(dao.findByQuery(query, Collections.emptyMap())).thenReturn(List.of(reference));
    var actual = dao.findReachableReferencesByShepardId(collectionShepardId, startShepardId, userName);
    assertEquals(List.of(reference), actual);
  }

  @Test
  public void findReachableReferencesByShepardIdStartIdTraversalRuleTest() {
    long startShepardId = 11L;
    long collectionShepardId = 21L;
    String userName = "user";
    TraversalRules children = TraversalRules.children;
    String query = dao.getSearchForReachableReferencesByShepardIdQuery(
      children,
      collectionShepardId,
      startShepardId,
      userName
    );
    StructuredDataReference reference = new StructuredDataReference();
    reference.setId(3L);
    when(dao.findByQuery(query, Collections.emptyMap())).thenReturn(List.of(reference));
    var actual = dao.findReachableReferencesByShepardId(children, collectionShepardId, startShepardId, userName);
    assertEquals(List.of(reference), actual);
  }

  @Test
  public void findReachableReferencesWithoutDataObjectIdTest() {
    long collectionId = 2L;
    String userName = "user";
    String query = dao.getSearchForReachableReferencesQuery(collectionId, userName);
    StructuredDataReference reference = new StructuredDataReference();
    reference.setId(3L);
    when(dao.findByQuery(query, Collections.emptyMap())).thenReturn(List.of(reference));
    var actual = dao.findReachableReferencesByNeo4jId(collectionId, userName);
    assertEquals(List.of(reference), actual);
  }

  @Test
  public void findReachableReferencesByShepardIdWithoutDataObjectIdTest() {
    long collectionShepardId = 21L;
    String userName = "user";
    String query = dao.getSearchForReachableReferencesByShepardIdQuery(collectionShepardId, userName);
    StructuredDataReference reference = new StructuredDataReference();
    reference.setId(3L);
    when(dao.findByQuery(query, Collections.emptyMap())).thenReturn(List.of(reference));
    var actual = dao.findReachableReferencesByShepardId(collectionShepardId, userName);
    assertEquals(List.of(reference), actual);
  }
}
