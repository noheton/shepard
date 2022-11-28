package de.dlr.shepard.neo4Core.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.Session;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.entities.Collection;

public class AbstractEntityDAOTest extends BaseTestCase {

	@Mock
	private Session session;

	@InjectMocks
	private AbstractEntityDAO dao = new AbstractEntityDAO();

	@Test
	public void findTest() {
		var entity = new Collection(1L);
		var result = mock(Result.class);
		when(result.queryResults()).thenReturn(List.of(Map.of("n", entity)));

		when(session.query("MATCH (n) WHERE ID(n) = 1 RETURN n", Collections.emptyMap())).thenReturn(result);
		var actual = dao.find(1L);
		assertEquals(entity, actual);
	}

	@Test
	public void findTest_noResult() {
		var result = mock(Result.class);
		when(result.queryResults()).thenReturn(Collections.emptyList());

		when(session.query("MATCH (n) WHERE ID(n) = 1 RETURN n", Collections.emptyMap())).thenReturn(result);
		var actual = dao.find(1L);
		assertNull(actual);
	}

	@Test
	public void findTest_noN() {
		var entity = new Collection(1L);
		var result = mock(Result.class);
		when(result.queryResults()).thenReturn(List.of(Map.of("x", entity)));

		when(session.query("MATCH (n) WHERE ID(n) = 1 RETURN n", Collections.emptyMap())).thenReturn(result);
		var actual = dao.find(1L);
		assertNull(actual);
	}

	@Test
	public void findTest_wrongType() {
		var result = mock(Result.class);
		when(result.queryResults()).thenReturn(List.of(Map.of("n", "test")));

		when(session.query("MATCH (n) WHERE ID(n) = 1 RETURN n", Collections.emptyMap())).thenReturn(result);
		var actual = dao.find(1L);
		assertNull(actual);
	}

	@Test
	public void updateTest() {
		var entity = new Collection(1L);
		dao.update(entity);
		verify(session).save(entity);
	}

}
