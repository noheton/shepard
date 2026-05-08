package de.dlr.shepard.context.references.dataobject.daos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.dataobject.entities.DataObjectReference;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.session.Session;

public class DataObjectReferenceDAOTest extends BaseTestCase {

  @Mock
  private Session session;

  @InjectMocks
  private DataObjectReferenceDAO dao;

  @Test
  public void getEntityTypeTest() {
    var type = dao.getEntityType();
    assertEquals(DataObjectReference.class, type);
  }

  @Test
  public void findByDataObjectTest() {
    var obj = new DataObject(1L);
    var obj2 = new DataObject(100L);
    var ref = new DataObjectReference(2L);
    var ref2 = new DataObjectReference(3L);
    var ref3 = new DataObjectReference(3L);
    ref.setDataObject(obj);
    ref2.setDataObject(obj2);

    // C5b: ID(d) is bound as Cypher parameter $dataObjectId.
    var query =
      """
      MATCH (d:DataObject)-[hr:has_reference]->(r:DataObjectReference { deleted: FALSE }) WHERE ID(d)=$dataObjectId \
      MATCH path=(r)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL \
      RETURN r, nodes(path), relationships(path)""";
    var paramsMap = Map.<String, Object>of("dataObjectId", 1L);
    when(session.query(DataObjectReference.class, query, paramsMap)).thenReturn(List.of(ref, ref2, ref3));

    var actual = dao.findByDataObjectNeo4jId(1L);
    verify(session).query(DataObjectReference.class, query, paramsMap);
    assertEquals(List.of(ref), actual);
  }
}
