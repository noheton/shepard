package de.dlr.shepard.neo4Core.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.Session;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.entities.FileContainer;
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
		var con = new FileContainer(200L);
		var ref = new FileReference() {
			{
				setId(2L);
				setFileContainer(con);
				setDataObject(obj);
			}
		};
		var ref2 = new FileReference() {
			{
				setId(3L);
				setFileContainer(con);
				setDataObject(obj2);
			}
		};
		var ref3 = new FileReference() {
			{
				setId(3L);
				setFileContainer(con);
			}
		};

		var query = """
				MATCH (d:DataObject)-[hr:has_reference]->(r:FileReference { deleted: FALSE }) \
				WHERE ID(d)=1 MATCH path=(r)-[*0..1]-(n) WHERE NOT n:FileContainer \
				RETURN r, nodes(path), relationships(path)""";
		when(session.query(FileReference.class, query, Collections.emptyMap())).thenReturn(List.of(ref, ref2, ref3));

		var queryContainer = """
				MATCH (d:DataObject)-[hr:has_reference]->(r:FileReference { deleted: FALSE })-[ic:is_in_container]->(c:FileContainer) \
				WHERE ID(d)=1 RETURN ID(r), ID(c)""";
		var result = mock(Result.class);

		var resultList = new ArrayList<Map<String, Object>>();
		resultList.add(Map.of("ID(r)", 2L, "ID(c)", 200L));
		resultList.add(Map.of("ID(r)", 3L, "ID(c)", 200L));
		doCallRealMethod().when(result).forEach(any());
		when(result.iterator()).thenReturn(resultList.iterator());
		when(session.query(queryContainer, Collections.emptyMap())).thenReturn(result);

		var actual = dao.findByDataObject(1L);
		verify(session).query(FileReference.class, query, Collections.emptyMap());
		assertEquals(List.of(ref), actual);
	}
}
