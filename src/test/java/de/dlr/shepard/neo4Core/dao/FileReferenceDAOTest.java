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
import de.dlr.shepard.neo4Core.entities.FileReference;

public class FileReferenceDAOTest extends BaseTestCase {
	@Mock
	private Session session;

	@InjectMocks
	private FileReferenceDAO dao;

	@Test
	public void getEntityTypeTest() {
		var type = dao.getEntityType();
		assertEquals(FileReference.class, type);
	}

	@Test
	public void findByDataObjectTest() {
		var obj = new DataObject(1L);
		var obj2 = new DataObject(100L);
		var ref = new FileReference(2L);
		var ref2 = new FileReference(3L);
		var ref3 = new FileReference(3L);
		ref.setDataObject(obj);
		ref2.setDataObject(obj2);

		var query = """
				MATCH (d:DataObject)-[hr:has_reference]->(r:FileReference { deleted: false }) WHERE ID(d)=1 \
				MATCH path=(r)-[*0..1]-(n) WHERE n.deleted = false or n.deleted IS NULL \
				RETURN r, nodes(path), relationships(path)""";
		when(session.query(FileReference.class, query, Collections.emptyMap())).thenReturn(List.of(ref, ref2, ref3));

		var actual = dao.findByDataObject(1L);
		verify(session).query(FileReference.class, query, Collections.emptyMap());
		assertEquals(List.of(ref), actual);
	}
}
