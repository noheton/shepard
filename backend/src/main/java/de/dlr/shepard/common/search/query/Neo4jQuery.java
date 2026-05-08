package de.dlr.shepard.common.search.query;

import java.util.Map;

/**
 * Carrier for a parameterised Cypher query: the Cypher fragment plus the
 * parameter map that must accompany it when handed to the OGM session.
 *
 * <p>Introduced as part of the C5 (Cypher injection) fix: every value that
 * derives from user input is bound as a Cypher parameter (e.g. {@code $p0})
 * rather than concatenated into the query string. Callers must always
 * forward {@link #params()} to {@code session.query(...)} so the binding
 * actually takes effect.
 */
public record Neo4jQuery(String cypher, Map<String, Object> params) {}
