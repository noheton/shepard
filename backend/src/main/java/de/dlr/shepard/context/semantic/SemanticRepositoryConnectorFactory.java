package de.dlr.shepard.context.semantic;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class SemanticRepositoryConnectorFactory {

  public ISemanticRepositoryConnector getRepositoryService(SemanticRepositoryType type, String endpoint) {
    return switch (type) {
      case SPARQL -> new SparqlConnector(endpoint);
      default -> {
        Log.errorf("Missing implementation of type: %s", type);
        throw new UnsupportedOperationException("Repository Type " + type + "is not yet implemented");
      }
    };
  }
}
