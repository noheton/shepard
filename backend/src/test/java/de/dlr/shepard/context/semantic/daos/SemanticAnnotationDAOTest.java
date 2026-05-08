package de.dlr.shepard.context.semantic.daos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.session.Session;

public class SemanticAnnotationDAOTest extends BaseTestCase {

  @Mock
  private Session session;

  @Mock
  private EntityIdResolver entityIdResolver;

  @InjectMocks
  private SemanticAnnotationDAO dao;

  @Test
  public void getEntityTypeTest() {
    var type = dao.getEntityType();
    assertEquals(SemanticAnnotation.class, type);
  }

  @Test
  public void findAllSemanticAnnotationsTest() {
    // L2c: ID(e) flipped to {appId: $entityAppId}; resolver translates the OGM long.
    when(entityIdResolver.resolveAppId(1L)).thenReturn("appid-e-1");
    var annotation = new SemanticAnnotation(1L);
    annotation.setPropertyName("Test");

    var query =
      """
      MATCH (e {appId: $entityAppId})-[ha:has_annotation]->(a:SemanticAnnotation) \
      WITH a MATCH path=(a)-[*0..1]->(n) WHERE n.deleted = FALSE OR n.deleted IS NULL \
      RETURN a, nodes(path), relationships(path)""";
    var paramsMap = Map.<String, Object>of("entityAppId", "appid-e-1");
    when(session.query(SemanticAnnotation.class, query, paramsMap)).thenReturn(List.of(annotation));

    var actual = dao.findAllSemanticAnnotationsByNeo4jId(1L);
    verify(session).query(SemanticAnnotation.class, query, paramsMap);
    assertEquals(List.of(annotation), actual);
  }

  @Test
  public void findAllSemanticAnnotationsByShepardIdTest() {
    var annotation = new SemanticAnnotation(1L);
    annotation.setPropertyName("Test");

    var query =
      """
      MATCH (e)-[ha:has_annotation]->(a:SemanticAnnotation) \
      WHERE e.shepardId=11 WITH a MATCH path=(a)-[*0..1]->(n) WHERE n.deleted = FALSE OR n.deleted IS NULL \
      RETURN a, nodes(path), relationships(path)""";
    when(session.query(SemanticAnnotation.class, query, Collections.emptyMap())).thenReturn(List.of(annotation));

    var actual = dao.findAllSemanticAnnotationsByShepardId(11L);
    verify(session).query(SemanticAnnotation.class, query, Collections.emptyMap());
    assertEquals(List.of(annotation), actual);
  }
}
