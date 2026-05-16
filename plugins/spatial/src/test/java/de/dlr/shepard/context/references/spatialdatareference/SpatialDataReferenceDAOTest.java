package de.dlr.shepard.context.references.spatialdatareference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.spatialdata.daos.SpatialDataReferenceDAO;
import de.dlr.shepard.context.references.spatialdata.entities.SpatialDataReference;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.session.Session;

public class SpatialDataReferenceDAOTest extends BaseTestCase {

  @Mock
  private Session session;

  @InjectMocks
  private SpatialDataReferenceDAO dao;

  @Test
  public void getEntityTypeTest() {
    var type = dao.getEntityType();
    assertEquals(SpatialDataReference.class, type);
  }

  @Test
  public void findByDataObjectShepardIdTest() {
    var obj = new DataObject(1L);
    obj.setShepardId(11L);
    var obj2 = new DataObject(100L);
    obj2.setShepardId(1001L);
    var ref = new SpatialDataReference(2L);
    ref.setShepardId(21L);
    var ref2 = new SpatialDataReference(3L);
    ref2.setShepardId(31L);
    var ref3 = new SpatialDataReference(4L);
    ref3.setShepardId(41L);
    ref.setDataObject(obj);
    ref2.setDataObject(obj2);

    var query =
      """
      MATCH (d:DataObject {shepardId: 11})-[hr:has_reference]->(r:SpatialDataReference { deleted: FALSE })\
      MATCH path=(r)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL \
      RETURN r, nodes(path), relationships(path)""";
    when(session.query(SpatialDataReference.class, query, Collections.emptyMap())).thenReturn(List.of(ref, ref2, ref3));

    var actual = dao.findByDataObjectShepardId(obj.getShepardId());
    verify(session).query(SpatialDataReference.class, query, Collections.emptyMap());
    assertEquals(List.of(ref), actual);
  }
}
