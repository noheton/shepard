package de.dlr.shepard.data.structureddata.daos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.data.structureddata.entities.StructuredData;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.session.Session;

public class StructuredDataDAOTest extends BaseTestCase {

  @Mock
  private Session session;

  @InjectMocks
  private StructuredDataDAO dao = new StructuredDataDAO();

  @Test
  public void findTest() {
    var sd = new StructuredData("oid", new Date(), "name");
    var query =
      """
      MATCH (c:StructuredDataContainer)-[:structureddata_in_container]->(s:StructuredData {oid: $oid}) \
      WHERE ID(c)=123 MATCH path=(s)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL \
      RETURN s, nodes(path), relationships(path)""";

    when(session.query(StructuredData.class, query, Map.of("oid", "oid"))).thenReturn(List.of(sd));
    var actual = dao.find(123L, "oid");
    assertEquals(sd, actual);
  }

  @Test
  public void findTest_notFound() {
    var query =
      """
      MATCH (c:StructuredDataContainer)-[:structureddata_in_container]->(s:StructuredData {oid: $oid}) \
      WHERE ID(c)=123 MATCH path=(s)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL \
      RETURN s, nodes(path), relationships(path)""";

    when(session.query(StructuredData.class, query, Map.of("oid", "oid"))).thenReturn(Collections.emptyList());
    var actual = dao.find(123L, "oid");
    assertNull(actual);
  }
}
