package de.dlr.shepard.semantics;

import java.util.Map;

public interface ISemanticRepositoryConnector {
  /**
   * Returns a map of language-label pairs or an empty map if not found
   *
   * @param termIri The iri of the term in question
   * @return a map of labels
   */
  Map<String, String> getTerm(String termIri);

  /**
   * Checks whether the sparql endpoint responds as expected
   *
   * @return boolean
   */
  boolean healthCheck();
}
