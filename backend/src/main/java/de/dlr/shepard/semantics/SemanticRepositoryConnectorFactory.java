package de.dlr.shepard.semantics;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SemanticRepositoryConnectorFactory {

  public ISemanticRepositoryConnector getRepositoryService(SemanticRepositoryType type, String endpoint) {
    return switch (type) {
      case SPARQL -> new SparqlConnector(endpoint);
      default -> {
        log.error("Missing implementation of type: {}", type);
        throw new UnsupportedOperationException("Repository Type " + type + "is not yet implemented");
      }
    };
  }
}
