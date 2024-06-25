package de.dlr.shepard.neo4Core.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.session.Session;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.entities.SemanticRepository;
import de.dlr.shepard.neo4Core.orderBy.SemanticRepositoryAttributes;
import de.dlr.shepard.util.QueryParamHelper;

public class SemanticRepositoryDAOTest extends BaseTestCase {
	@Mock
	private Session session;

	@InjectMocks
	private SemanticRepositoryDAO dao;

	@Test
	public void getEntityTypeTest() {
		var type = dao.getEntityType();
		assertEquals(SemanticRepository.class, type);
	}

	@Test
	public void findAllSemanticRepositoriesTest_Pagination() {
		QueryParamHelper params = new QueryParamHelper().withName("name").withPageAndSize(2, 10)
				.withOrderByAttribute(SemanticRepositoryAttributes.name, true);
		Map<String, Object> paramsMap = Map.of("name", "name", "offset", 20, "size", 10);
		var repo = new SemanticRepository(1L);
		repo.setName("Name");
		var wrongRepo = new SemanticRepository(1L);
		wrongRepo.setName("Wrong");
		var query = """
				MATCH (r:SemanticRepository { name : $name, deleted: FALSE }) WITH r \
				ORDER BY toLower(r.name) DESC SKIP $offset LIMIT $size \
				MATCH path=(r)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL RETURN r, nodes(path), relationships(path)""";
		when(session.query(SemanticRepository.class, query, paramsMap)).thenReturn(List.of(repo, wrongRepo));
		var actual = dao.findAllSemanticRepositories(params);
		assertEquals(List.of(repo), actual);
	}

	@Test
	public void findAllSemanticRepositoriesTest_NoPagination() {
		var repo = new SemanticRepository(1L);
		Map<String, Object> paramsMap = Collections.emptyMap();
		var query = """
				MATCH (r:SemanticRepository { deleted: FALSE }) WITH r \
				MATCH path=(r)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL RETURN r, nodes(path), relationships(path)""";
		when(session.query(SemanticRepository.class, query, paramsMap)).thenReturn(List.of(repo));
		QueryParamHelper params = new QueryParamHelper();
		var actual = dao.findAllSemanticRepositories(params);
		verify(session).query(SemanticRepository.class, query, paramsMap);
		assertEquals(List.of(repo), actual);
	}
}
