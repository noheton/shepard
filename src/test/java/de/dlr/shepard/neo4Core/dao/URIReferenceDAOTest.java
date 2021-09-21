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
import de.dlr.shepard.neo4Core.entities.URIReference;

public class URIReferenceDAOTest extends BaseTestCase {
	@Mock
	private Session session;

	@InjectMocks
	private URIReferenceDAO dao;

	@Test
	public void getEntityTypeTest() {
		var type = dao.getEntityType();
		assertEquals(URIReference.class, type);
	}

	@Test
	public void findByDataObjectTest() {
		var obj = new DataObject(1L);
		var obj2 = new DataObject(100L);
		var ref = new URIReference(2L);
		var ref2 = new URIReference(3L);
		var ref3 = new URIReference(3L);
		ref.setDataObject(obj);
		ref2.setDataObject(obj2);

		var query = "MATCH (d:DataObject)-[hr:has_reference]->(r:URIReference { deleted: false }) WHERE ID(d)=1 "
				+ "MATCH path=(User)<-[]-(r)-[*0..1]-({deleted: False}) "
				+ "RETURN r, nodes(path), relationships(path)";
		when(session.query(URIReference.class, query, Collections.emptyMap())).thenReturn(List.of(ref, ref2, ref3));

		var actual = dao.findByDataObject(1L);
		verify(session).query(URIReference.class, query, Collections.emptyMap());
		assertEquals(List.of(ref), actual);
	}
}
