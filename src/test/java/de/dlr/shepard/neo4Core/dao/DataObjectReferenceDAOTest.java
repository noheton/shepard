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
import de.dlr.shepard.neo4Core.entities.DataObjectReference;

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

		var query = """
				MATCH (d:DataObject)-[hr:has_reference]->(r:DataObjectReference { deleted: FALSE }) WHERE ID(d)=1 \
				MATCH path=(r)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL \
				RETURN r, nodes(path), relationships(path)""";
		when(session.query(DataObjectReference.class, query, Collections.emptyMap()))
				.thenReturn(List.of(ref, ref2, ref3));

		var actual = dao.findByDataObjectNeo4jId(1L);
		verify(session).query(DataObjectReference.class, query, Collections.emptyMap());
		assertEquals(List.of(ref), actual);
	}

	@Test
	public void findByDataObjectShepardIdTest() {
		var obj = new DataObject(1L);
		obj.setShepardId(11L);
		var obj2 = new DataObject(100L);
		obj2.setShepardId(1001L);
		var ref = new DataObjectReference(2L);
		ref.setShepardId(21L);
		var ref2 = new DataObjectReference(3L);
		ref.setShepardId(31L);
		var ref3 = new DataObjectReference(4L);
		ref3.setShepardId(41L);
		ref.setDataObject(obj);
		ref2.setDataObject(obj2);

		String query = "MATCH (d:DataObject)-[hr:has_reference]->(r:DataObjectReference { deleted: FALSE }) WHERE d.shepardId=11 MATCH path=(r)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL RETURN r, nodes(path), relationships(path)";
		when(session.query(DataObjectReference.class, query, Collections.emptyMap()))
				.thenReturn(List.of(ref, ref2, ref3));

		var actual = dao.findByDataObjectShepardId(obj.getShepardId());
		verify(session).query(DataObjectReference.class, query, Collections.emptyMap());
		assertEquals(List.of(ref), actual);
	}
}
