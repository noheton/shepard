package de.dlr.shepard.semantics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;

public class SemanticRepositoryConnectorFactoryTest extends BaseTestCase {

	private SemanticRepositoryConnectorFactory factory = new SemanticRepositoryConnectorFactory();

	@Test
	public void getRepositoryService_Sparql() {
		var actual = factory.getRepositoryService(SemanticRepositoryType.SPARQL, "endpoint");
		assertEquals(SparqlConnector.class, actual.getClass());
	}

	@Test
	public void getRepositoryService_NotYetImplemented() {
		assertThrows(UnsupportedOperationException.class,
				() -> factory.getRepositoryService(SemanticRepositoryType.JSKOS, "endpoint"));
	}

}
