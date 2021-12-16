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
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.entities.StructuredDataReference;
import de.dlr.shepard.util.TraversalRules;

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

		var query = """
				MATCH (d:DataObject)-[hr:has_reference]->(r:StructuredDataReference { deleted: false }) WHERE ID(d)=1 \
				MATCH path=(r)-[*0..1]-(n) WHERE n.deleted = false or n.deleted IS NULL \
				RETURN r, nodes(path), relationships(path)""";
		when(session.query(StructuredDataReference.class, query, Collections.emptyMap()))
				.thenReturn(List.of(ref, ref2, ref3));

		var actual = dao.findByDataObject(1L);
		verify(session).query(StructuredDataReference.class, query, Collections.emptyMap());
		assertEquals(List.of(ref), actual);
	}

	@Test
	public void findReachableReferencesStartIdTest() {
		long startId = 1L;
		String query = dao.getSearchForReachableReferencesQuery(startId);
		StructuredDataReference reference = new StructuredDataReference();
		reference.setId(3L);
		when(dao.findByQuery(query, Collections.emptyMap())).thenReturn(List.of(reference));
		var actual = dao.findReachableReferences(startId);
		assertEquals(List.of(reference), actual);
	}

	@Test
	public void findReachableReferencesStartIdTraversalRuleTest() {
		long startId = 1L;
		TraversalRules children = TraversalRules.children;
		String query = dao.getSearchForReachableReferencesQuery(children, startId);
		StructuredDataReference reference = new StructuredDataReference();
		reference.setId(3L);
		when(dao.findByQuery(query, Collections.emptyMap())).thenReturn(List.of(reference));
		var actual = dao.findReachableReferences(children, startId);
		assertEquals(List.of(reference), actual);
	}
}
