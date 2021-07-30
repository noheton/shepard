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
import de.dlr.shepard.neo4Core.entities.TimeseriesContainer;
import de.dlr.shepard.neo4Core.orderBy.ContainerAttributes;
import de.dlr.shepard.util.QueryParamHelper;

public class TimeseriesContainerDAOTest extends BaseTestCase {
	@Mock
	private Session session;

	@InjectMocks
	private TimeseriesContainerDAO dao;

	@Test
	public void getEntityTypeTest() {
		var type = dao.getEntityType();
		assertEquals(TimeseriesContainer.class, type);
	}

	@Test
	public void findAll_WithoutName() {
		var col1 = new TimeseriesContainer(1L);
		col1.setName("Yes");

		var query = "MATCH (c:TimeseriesContainer ) "
				+ "WITH c MATCH path=(c)-[*0..1]-() RETURN c, nodes(path), relationships(path)";
		when(session.query(TimeseriesContainer.class, query, Collections.emptyMap())).thenReturn(List.of(col1));

		var params = new QueryParamHelper();
		var actual = dao.findAllTimeseriesContainers(params);
		verify(session).query(TimeseriesContainer.class, query, Collections.emptyMap());
		assertEquals(List.of(col1), actual);
	}

	@Test
	public void findAll_WithoutNameOrderByNameDesc() {
		var col1 = new TimeseriesContainer(1L);
		col1.setName("Yes");

		var query = "MATCH (c:TimeseriesContainer ) "
				+ "WITH c MATCH path=(c)-[*0..1]-() RETURN c, nodes(path), relationships(path)"
				+ " ORDER BY toLower(c.name) DESC";
		when(session.query(TimeseriesContainer.class, query, Collections.emptyMap())).thenReturn(List.of(col1));

		var params = new QueryParamHelper();
		var attr = ContainerAttributes.name;
		params = params.withOrderByAttribute(attr, true);
		var actual = dao.findAllTimeseriesContainers(params);
		verify(session).query(TimeseriesContainer.class, query, Collections.emptyMap());
		assertEquals(List.of(col1), actual);
	}

	@Test
	public void findAll_WithName() {
		var col1 = new TimeseriesContainer(1L);
		col1.setName("Yes");
		var col2 = new TimeseriesContainer(2L);
		col2.setName("No");

		var query = "MATCH (c:TimeseriesContainer { name : \"Yes\" }) WITH c "
				+ "MATCH path=(c)-[*0..1]-() RETURN c, nodes(path), relationships(path)";
		when(session.query(TimeseriesContainer.class, query, Collections.emptyMap())).thenReturn(List.of(col1, col2));

		var params = new QueryParamHelper().withName("Yes");
		var actual = dao.findAllTimeseriesContainers(params);
		verify(session).query(TimeseriesContainer.class, query, Collections.emptyMap());
		assertEquals(List.of(col1), actual);
	}

	@Test
	public void findAll_WithNameOrderByNameDesc() {
		var col1 = new TimeseriesContainer(1L);
		col1.setName("Yes");
		var col2 = new TimeseriesContainer(2L);
		col2.setName("No");

		var query = "MATCH (c:TimeseriesContainer { name : \"Yes\" }) WITH c "
				+ "MATCH path=(c)-[*0..1]-() RETURN c, nodes(path), relationships(path)"
				+ " ORDER BY toLower(c.name) DESC";
		when(session.query(TimeseriesContainer.class, query, Collections.emptyMap())).thenReturn(List.of(col1, col2));

		var params = new QueryParamHelper().withName("Yes");
		var attr = ContainerAttributes.name;
		params = params.withOrderByAttribute(attr, true);
		var actual = dao.findAllTimeseriesContainers(params);
		verify(session).query(TimeseriesContainer.class, query, Collections.emptyMap());
		assertEquals(List.of(col1), actual);
	}

	@Test
	public void findAll_WithPage() {
		var col1 = new TimeseriesContainer(1L);
		col1.setName("Yes");

		var query = "MATCH (c:TimeseriesContainer ) WITH c SKIP 300 LIMIT 100 "
				+ "MATCH path=(c)-[*0..1]-() RETURN c, nodes(path), relationships(path)";
		when(session.query(TimeseriesContainer.class, query, Collections.emptyMap())).thenReturn(List.of(col1));

		var params = new QueryParamHelper().withPageAndSize(3, 100);
		var actual = dao.findAllTimeseriesContainers(params);
		verify(session).query(TimeseriesContainer.class, query, Collections.emptyMap());
		assertEquals(List.of(col1), actual);
	}

	@Test
	public void findAll_WithPageOrderByNameDesc() {
		var col1 = new TimeseriesContainer(1L);
		col1.setName("Yes");

		var query = "MATCH (c:TimeseriesContainer ) WITH c SKIP 300 LIMIT 100 "
				+ "MATCH path=(c)-[*0..1]-() RETURN c, nodes(path), relationships(path)"
				+ " ORDER BY toLower(c.name) DESC";
		when(session.query(TimeseriesContainer.class, query, Collections.emptyMap())).thenReturn(List.of(col1));

		var params = new QueryParamHelper().withPageAndSize(3, 100);
		var attr = ContainerAttributes.name;
		params = params.withOrderByAttribute(attr, true);
		var actual = dao.findAllTimeseriesContainers(params);
		verify(session).query(TimeseriesContainer.class, query, Collections.emptyMap());
		assertEquals(List.of(col1), actual);
	}

	@Test
	public void findAll_WithNameAndPage() {
		var col1 = new TimeseriesContainer(1L);
		col1.setName("Yes");
		var col2 = new TimeseriesContainer(2L);
		col2.setName("No");

		var query = "MATCH (c:TimeseriesContainer { name : \"Yes\" }) WITH c SKIP 300 LIMIT 100 "
				+ "MATCH path=(c)-[*0..1]-() RETURN c, nodes(path), relationships(path)";
		when(session.query(TimeseriesContainer.class, query, Collections.emptyMap())).thenReturn(List.of(col1, col2));

		var params = new QueryParamHelper().withPageAndSize(3, 100).withName("Yes");
		var actual = dao.findAllTimeseriesContainers(params);
		verify(session).query(TimeseriesContainer.class, query, Collections.emptyMap());
		assertEquals(List.of(col1), actual);
	}

	@Test
	public void findAll_WithNameAndPageOrderByNameDesc() {
		var col1 = new TimeseriesContainer(1L);
		col1.setName("Yes");
		var col2 = new TimeseriesContainer(2L);
		col2.setName("No");

		var query = "MATCH (c:TimeseriesContainer { name : \"Yes\" }) WITH c SKIP 300 LIMIT 100 "
				+ "MATCH path=(c)-[*0..1]-() RETURN c, nodes(path), relationships(path)"
				+ " ORDER BY toLower(c.name) DESC";
		when(session.query(TimeseriesContainer.class, query, Collections.emptyMap())).thenReturn(List.of(col1, col2));

		var params = new QueryParamHelper().withPageAndSize(3, 100).withName("Yes");
		var attr = ContainerAttributes.name;
		params = params.withOrderByAttribute(attr, true);
		var actual = dao.findAllTimeseriesContainers(params);
		verify(session).query(TimeseriesContainer.class, query, Collections.emptyMap());
		assertEquals(List.of(col1), actual);
	}
}
