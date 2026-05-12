package de.dlr.shepard.context.semantic;

public enum SemanticRepositoryType {
  SPARQL,
  JSKOS,
  SKOSMOS,
  /**
   * N1a — neosemantics-backed semantic repository hosted inside shepard's
   * Neo4j instance. The {@code endpoint} attribute is ignored for this
   * type; the connector routes through the in-process OGM session.
   * Operators who have removed the n10s plugin from their Neo4j see this
   * type degrade gracefully (the connector reports {@code healthCheck() ==
   * false} and {@code getTerm} returns an empty map) — the bootstrap hook
   * logs a warning at startup explaining the situation.
   *
   * @see de.dlr.shepard.context.semantic.InternalSemanticConnector
   * @see de.dlr.shepard.context.semantic.N10sBootstrapHook
   */
  INTERNAL,
}
