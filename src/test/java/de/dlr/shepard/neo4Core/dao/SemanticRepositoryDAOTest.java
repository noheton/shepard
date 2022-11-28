package de.dlr.shepard.neo4Core.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.session.Session;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.entities.SemanticRepository;

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
}
