package de.dlr.shepard.data.semantic;

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
  public void getRepositoryService_NotYetImplemented() {
    assertThrows(UnsupportedOperationException.class, () ->
      factory.getRepositoryService(SemanticRepositoryType.JSKOS, "endpoint")
    );
  }
}
