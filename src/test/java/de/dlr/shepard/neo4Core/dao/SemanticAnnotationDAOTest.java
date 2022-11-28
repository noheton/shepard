package de.dlr.shepard.neo4Core.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.session.Session;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.entities.SemanticAnnotation;

public class SemanticAnnotationDAOTest extends BaseTestCase {
	@Mock
	private Session session;

	@InjectMocks
	private SemanticAnnotationDAO dao;

	@Test
	public void getEntityTypeTest() {
		var type = dao.getEntityType();
		assertEquals(SemanticAnnotation.class, type);
	}

	@Test
	public void findAllSemanticAnnotationsTest() {
		var annotation = new SemanticAnnotation(1L);
		annotation.setName("Test");

		var query = """
				MATCH (a:SemanticAnnotation { deleted: FALSE })<-[ha:has_annotation]-(e) \
				WHERE ID(e)=1 WITH a MATCH path=(a)-[*0..1]->(n) WHERE n.deleted = FALSE OR n.deleted IS NULL \
				RETURN a, nodes(path), relationships(path)""";
		when(session.query(SemanticAnnotation.class, query, Collections.emptyMap())).thenReturn(List.of(annotation));

		var actual = dao.findAllSemanticAnnotations(1L);
		verify(session).query(SemanticAnnotation.class, query, Collections.emptyMap());
		assertEquals(List.of(annotation), actual);
	}

}
