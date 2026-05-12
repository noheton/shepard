package de.dlr.shepard.context.semantic;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class SemanticRepositoryConnectorFactory {

  public ISemanticRepositoryConnector getRepositoryService(SemanticRepositoryType type, String endpoint) {
    return switch (type) {
      case SPARQL -> new SparqlConnector(endpoint);
      // N1a — INTERNAL routes to the in-process n10s plugin via the
      // existing OGM session. `endpoint` is ignored (the value is
      // typically left blank by clients but accepting any value keeps
      // the upstream wire shape).
      case INTERNAL -> new InternalSemanticConnector();
      default -> {
        Log.errorf("Missing implementation of type: %s", type);
        throw new UnsupportedOperationException("Repository Type " + type + "is not yet implemented");
      }
    };
  }
}
