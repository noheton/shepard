package de.dlr.shepard.neo4Core.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.neo4j.ogm.model.QueryStatistics;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.Session;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.entities.BasicEntity;
import de.dlr.shepard.neo4Core.entities.Collection;
import de.dlr.shepard.neo4Core.entities.Permissions;
import de.dlr.shepard.neo4Core.entities.User;

public class PermissionsDAOTest extends BaseTestCase {

	@Mock
	private Session session;

	@InjectMocks
	private PermissionsDAO dao;

	@Test
	public void getEntityTypeTest() {
		var type = dao.getEntityType();
		assertEquals(Permissions.class, type);
	}

	@Test
	public void findByEntityTest() {
		var perm = new Permissions(1L);
		String query = """
				MATCH (e)-[:has_permissions]->(p:Permissions) WHERE ID(e) = 2 \
				MATCH path=(p)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL \
				RETURN p, nodes(path), relationships(path)""";
		when(session.query(Permissions.class, query, Collections.emptyMap())).thenReturn(List.of(perm));
		var actual = dao.findByEntityNeo4jId(2L);
		verify(session).query(Permissions.class, query, Collections.emptyMap());
		assertEquals(perm, actual);
	}

	@Test
	public void findByShepardIdTest() {
		var perm = new Permissions(1L);
		String query = """
				MATCH (e)-[:has_permissions]->(p:Permissions) WHERE e.shepardId = 11 \
				MATCH path=(p)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL \
				RETURN p, nodes(path), relationships(path)""";
		when(session.query(Permissions.class, query, Collections.emptyMap())).thenReturn(List.of(perm));
		var actual = dao.findByEntityShepardId(11L);
		verify(session).query(Permissions.class, query, Collections.emptyMap());
		assertEquals(perm, actual);
	}

	@Test
	public void findByCollectionShepardIdTest() {
		var perm = new Permissions(1L);
		String query = """
				MATCH (c:Collection)-[:has_permissions]->(p:Permissions) WHERE c.shepardId = 11 \
				MATCH path=(p)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL \
				RETURN p, nodes(path), relationships(path)""";
		when(session.query(Permissions.class, query, Collections.emptyMap())).thenReturn(List.of(perm));
		var actual = dao.findByCollectionShepardId(11L);
		verify(session).query(Permissions.class, query, Collections.emptyMap());
		assertEquals(perm, actual);
	}

	@Test
	public void findByEntityTest_notFound() {
		String query = """
				MATCH (e)-[:has_permissions]->(p:Permissions) WHERE ID(e) = 1 \
				MATCH path=(p)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL \
				RETURN p, nodes(path), relationships(path)""";
		when(session.query(Permissions.class, query, Collections.emptyMap())).thenReturn(Collections.emptyList());
		var actual = dao.findByEntityNeo4jId(1L);
		verify(session).query(Permissions.class, query, Collections.emptyMap());
		assertNull(actual);
	}

	@Test
	public void findByShepardIdTest_notFound() {
		String query = """
				MATCH (e)-[:has_permissions]->(p:Permissions) WHERE e.shepardId = 11 \
				MATCH path=(p)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL \
				RETURN p, nodes(path), relationships(path)""";
		when(session.query(Permissions.class, query, Collections.emptyMap())).thenReturn(Collections.emptyList());
		var actual = dao.findByEntityShepardId(11L);
		verify(session).query(Permissions.class, query, Collections.emptyMap());
		assertNull(actual);
	}

	@Test
	public void findByCollectionShepardIdTest_notFound() {
		String query = """
				MATCH (c:Collection)-[:has_permissions]->(p:Permissions) WHERE c.shepardId = 11 \
				MATCH path=(p)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL \
				RETURN p, nodes(path), relationships(path)""";
		when(session.query(Permissions.class, query, Collections.emptyMap())).thenReturn(Collections.emptyList());
		var actual = dao.findByCollectionShepardId(11L);
		verify(session).query(Permissions.class, query, Collections.emptyMap());
		assertNull(actual);
	}

	@Test
	public void createWithEntityTest() {
		var user = new User("bob");
		var perm = new Permissions();
		perm.setOwner(user);
		var col = new Collection(2L);
		var col2 = new Collection(3L);
		ArrayList<BasicEntity> colList = new ArrayList<BasicEntity>();
		colList.add(col);
		colList.add(col2);

		String query = "MATCH (e) WHERE ID(e) = 2 MATCH (p:Permissions) WHERE ID(p) = 1 "
				+ "CREATE path = (e)-[r:has_permissions]->(p)";

		var created = new Permissions();
		created.setOwner(user);
		created.setId(1L);

		var updated = new Permissions();
		updated.setOwner(user);
		updated.setId(1L);
		updated.setEntities(colList);

		var stat = mock(QueryStatistics.class);
		when(stat.containsUpdates()).thenReturn(true);
		var res = mock(Result.class);
		when(res.queryStatistics()).thenReturn(stat);

		doAnswer(new Answer<Void>() {

			@Override
			public Void answer(InvocationOnMock invocation) throws Throwable {
				perm.setId(1L);
				return null;
			}
		}).when(session).save(perm, 1);
		when(session.query(query, Collections.emptyMap())).thenReturn(res);
		when(session.load(Permissions.class, 1L, 1)).thenReturn(updated);

		var actual = dao.createWithEntityNeo4jId(perm, 2L);
		assertEquals(updated, actual);
	}

	@Test
	public void createWithShepardIdTest() {
		var user = new User("bob");
		var perm = new Permissions();
		perm.setOwner(user);
		var col = new Collection(2L);
		col.setShepardId(21L);
		ArrayList<BasicEntity> colList = new ArrayList<BasicEntity>();
		colList.add(col);

		String query = "MATCH (e) WHERE e.shepardId = 21 MATCH (p:Permissions) WHERE ID(p) = 1 "
				+ "CREATE path = (e)-[r:has_permissions]->(p)";

		var created = new Permissions();
		created.setOwner(user);
		created.setId(1L);

		var updated = new Permissions();
		updated.setOwner(user);
		updated.setId(1L);
		updated.setEntities(colList);

		var stat = mock(QueryStatistics.class);
		when(stat.containsUpdates()).thenReturn(true);
		var res = mock(Result.class);
		when(res.queryStatistics()).thenReturn(stat);

		doAnswer(new Answer<Void>() {

			@Override
			public Void answer(InvocationOnMock invocation) throws Throwable {
				perm.setId(1L);
				return null;
			}
		}).when(session).save(perm, 1);
		when(session.query(query, Collections.emptyMap())).thenReturn(res);
		when(session.load(Permissions.class, 1L, 1)).thenReturn(updated);

		var actual = dao.createWithEntityShepardId(perm, col.getShepardId());
		assertEquals(updated, actual);
	}

}
