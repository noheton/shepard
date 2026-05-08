package de.dlr.shepard.data.file.daos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.data.file.entities.ShepardFile;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.session.Session;

public class ShepardFileDAOTest extends BaseTestCase {

  @Mock
  private Session session;

  @InjectMocks
  private ShepardFileDAO dao = new ShepardFileDAO();

  @Test
  public void findTest() {
    // C5b: ID(c) is bound as Cypher parameter $containerId alongside $oid.
    var f = new ShepardFile("oid", new Date(), "filename", "md5");
    var query =
      """
      MATCH (c:FileContainer)-[:file_in_container]->(f:ShepardFile {oid: $oid}) \
      WHERE ID(c)=$containerId MATCH path=(f)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL \
      RETURN f, nodes(path), relationships(path)""";
    var paramsMap = Map.<String, Object>of("oid", "oid", "containerId", 123L);

    when(session.query(ShepardFile.class, query, paramsMap)).thenReturn(List.of(f));
    var actual = dao.find(123L, "oid");
    assertEquals(f, actual);
    verify(session).query(ShepardFile.class, query, paramsMap);
  }

  @Test
  public void findTest_notFound() {
    var query =
      """
      MATCH (c:FileContainer)-[:file_in_container]->(f:ShepardFile {oid: $oid}) \
      WHERE ID(c)=$containerId MATCH path=(f)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL \
      RETURN f, nodes(path), relationships(path)""";
    var paramsMap = Map.<String, Object>of("oid", "oid", "containerId", 123L);

    when(session.query(ShepardFile.class, query, paramsMap)).thenReturn(Collections.emptyList());
    var actual = dao.find(123L, "oid");
    assertNull(actual);
  }
}
