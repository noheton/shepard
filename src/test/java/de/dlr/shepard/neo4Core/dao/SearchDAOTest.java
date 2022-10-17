package de.dlr.shepard.neo4Core.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.Session;

import de.dlr.shepard.BaseTestCase;

public class SearchDAOTest extends BaseTestCase {

	@Mock
	private Session session;

	@InjectMocks
	private SearchDAO dao;

	@Test
	public void getIdsFromQueryTest() {
		String query = "query";
		String[] variables = { "col" };
		ArrayList<Map<String, Object>> list = new ArrayList<>();
		Map<String, Object> map = new HashMap<>();
		map.put("id(col)", 123L);
		list.add(map);
		Iterator<Map<String, Object>> iterator = list.iterator();
		var result = mock(Result.class);
		when(result.iterator()).thenReturn(iterator);
		when(session.query(query, Collections.emptyMap())).thenReturn(result);
		List<Long[]> actual = dao.getIdsFromQuery(query, variables);
		assertEquals(1, actual.size());
		assertEquals(1, actual.get(0).length);
		assertEquals(123L, actual.get(0)[0]);
	}
}
