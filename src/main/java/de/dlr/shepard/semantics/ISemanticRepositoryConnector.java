package de.dlr.shepard.semantics;

import java.util.Map;

public interface ISemanticRepositoryConnector {

	/**
	 * Returns a map of property-object pairs or an empty map if not found
	 *
	 * @param termIri The iri of the term in question
	 * @return a map
	 */
	Map<String, String> getTerm(String termIri);
}
