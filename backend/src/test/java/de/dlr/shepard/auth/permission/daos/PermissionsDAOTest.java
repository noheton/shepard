package de.dlr.shepard.auth.permission.daos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.auth.permission.entities.Permissions;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.session.Session;

public class PermissionsDAOTest extends BaseTestCase {

  @Mock
  private Session session;

  @InjectMocks
  private PermissionsDAO dao;

  @Test
  public void getEntityTypeTest() {
    var type = dao.getEntityType();
    assertEquals(Permissions.class, type);
  }

  @Test
  public void findByEntityTest() {
    var perm = new Permissions(1L);
    String query =
      """
      MATCH (e:BasicEntity)-[:has_permissions]->(p:Permissions) WHERE ID(e) = 2 \
      MATCH path=(p)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL \
      RETURN p, nodes(path), relationships(path)""";
    when(session.query(Permissions.class, query, Collections.emptyMap())).thenReturn(List.of(perm));
    var actual = dao.findByEntityNeo4jId(2L);
    verify(session).query(Permissions.class, query, Collections.emptyMap());
    assertEquals(perm, actual);
  }

  @Test
  public void findByEntityTest_notFound() {
    String query =
      """
      MATCH (e:BasicEntity)-[:has_permissions]->(p:Permissions) WHERE ID(e) = 1 \
      MATCH path=(p)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL \
      RETURN p, nodes(path), relationships(path)""";
    when(session.query(Permissions.class, query, Collections.emptyMap())).thenReturn(Collections.emptyList());
    var actual = dao.findByEntityNeo4jId(1L);
    verify(session).query(Permissions.class, query, Collections.emptyMap());
    assertNull(actual);
  }
}
