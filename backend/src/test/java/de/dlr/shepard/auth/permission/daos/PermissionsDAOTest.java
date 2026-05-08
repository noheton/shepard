package de.dlr.shepard.auth.permission.daos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.auth.permission.model.Permissions;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.neo4j.entities.BasicEntity;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.data.file.entities.FileContainer;
import jakarta.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.session.Session;

public class PermissionsDAOTest extends BaseTestCase {

  @Mock
  private Session session;

  @Mock
  private EntityIdResolver entityIdResolver;

  @InjectMocks
  private PermissionsDAO dao;

  @Test
  public void getEntityTypeTest() {
    var type = dao.getEntityType();
    assertEquals(Permissions.class, type);
  }

  @Test
  public void findByEntityTest() {
    // L2c: WHERE ID(e) flipped to {appId: $appId}; resolver translates long → appId.
    when(entityIdResolver.resolveAppId(2L)).thenReturn("appid-e-2");
    var perm = new Permissions(1L);
    String query =
      """
      MATCH (e:BasicEntity {appId: $appId})-[:has_permissions]->(p:Permissions) \
      MATCH path=(p)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL \
      RETURN p, nodes(path), relationships(path)""";
    Map<String, Object> params = Map.of("appId", "appid-e-2");
    when(session.query(Permissions.class, query, params)).thenReturn(List.of(perm));
    var actual = dao.findByEntityNeo4jId(2L);
    verify(session).query(Permissions.class, query, params);
    // Cypher does not contain the deprecated id() function; appId is parameter-bound.
    assertThat(query).doesNotContain("ID(e)").doesNotContain("id(e)").contains("$appId");
    assertThat(params).containsEntry("appId", "appid-e-2");
    assertEquals(perm, actual);
  }

  @Test
  public void findByEntityTest_notFound() {
    when(entityIdResolver.resolveAppId(1L)).thenReturn("appid-e-1");
    String query =
      """
      MATCH (e:BasicEntity {appId: $appId})-[:has_permissions]->(p:Permissions) \
      MATCH path=(p)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL \
      RETURN p, nodes(path), relationships(path)""";
    Map<String, Object> params = Map.of("appId", "appid-e-1");
    when(session.query(Permissions.class, query, params)).thenReturn(Collections.emptyList());
    var actual = dao.findByEntityNeo4jId(1L);
    verify(session).query(Permissions.class, query, params);
    assertNull(actual);
  }

  @Test
  public void findByEntityTest_resolverNotFound_returnsNull() {
    // If the resolver can't find an appId for this OGM id (e.g. legacy row
    // missed by V12), the prior behaviour was an empty Cypher result; we
    // preserve that by returning null without ever hitting the session.
    when(entityIdResolver.resolveAppId(99L)).thenThrow(new NotFoundException("missing"));
    var actual = dao.findByEntityNeo4jId(99L);
    assertNull(actual);
    verify(session, org.mockito.Mockito.never()).query(eq(Permissions.class), any(String.class), any(Map.class));
  }

  @Test
  public void findByEntityNeo4jIds_emptyInput_returnsEmptyMap_andSkipsQuery() {
    var actual = dao.findByEntityNeo4jIds(Collections.emptyList());
    assertThat(actual).isEmpty();
    verify(session, org.mockito.Mockito.never()).query(eq(Permissions.class), any(String.class), any(Map.class));
  }

  @Test
  public void findByEntityNeo4jIds_groupsResultsByEntityId() {
    when(entityIdResolver.resolveAppId(10L)).thenReturn("appid-e-10");
    when(entityIdResolver.resolveAppId(20L)).thenReturn("appid-e-20");
    when(entityIdResolver.resolveAppId(30L)).thenThrow(new NotFoundException("missing"));

    var entityA = new FileContainer(10L);
    var entityB = new Collection(20L);
    entityB.setId(20L);
    var perms1 = new Permissions(1L);
    var perms2 = new Permissions(2L);
    var entitiesA = new ArrayList<BasicEntity>();
    entitiesA.add(entityA);
    perms1.setEntities(entitiesA);
    var entitiesB = new ArrayList<BasicEntity>();
    entitiesB.add(entityB);
    perms2.setEntities(entitiesB);

    when(session.query(eq(Permissions.class), any(String.class), any(Map.class))).thenReturn(List.of(perms1, perms2));

    var actual = dao.findByEntityNeo4jIds(List.of(10L, 20L, 30L));

    assertThat(actual).containsOnlyKeys(10L, 20L);
    assertThat(actual.get(10L)).isEqualTo(perms1);
    assertThat(actual.get(20L)).isEqualTo(perms2);
  }
}
