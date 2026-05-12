package de.dlr.shepard.context.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@QuarkusComponentTest
public class SemanticRepositoryConnectorFactoryTest {

  @Inject
  SemanticRepositoryConnectorFactory factory;

  @Test
  public void getRepositoryService_Sparql() {
    var actual = factory.getRepositoryService(SemanticRepositoryType.SPARQL, "endpoint");
    assertEquals(SparqlConnector.class, actual.getClass());
  }

  @Test
  public void getRepositoryService_Internal() {
    // N1a — INTERNAL routes to the n10s-backed connector. The
    // endpoint string is ignored for this type; the connector talks
    // to the in-process OGM session.
    var actual = factory.getRepositoryService(SemanticRepositoryType.INTERNAL, "ignored");
    assertEquals(InternalSemanticConnector.class, actual.getClass());
  }

  @Test
  public void getRepositoryService_NotYetImplemented() {
    assertThrows(UnsupportedOperationException.class, () ->
      factory.getRepositoryService(SemanticRepositoryType.JSKOS, "endpoint")
    );
  }
}
