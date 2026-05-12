package de.dlr.shepard.context.semantic;

import io.quarkus.logging.Log;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.neo4j.ogm.session.Session;

/**
 * N1a — semantic-repository connector backed by the neosemantics
 * ("n10s") plugin running inside shepard's existing Neo4j instance.
 *
 * <p>The connector routes through the same OGM {@link Session} the
 * rest of shepard uses; no new database connection is opened. n10s
 * imports RDF data under nodes carrying the {@code :Resource} label,
 * giving the connector a clean keyspace to read from without
 * disturbing shepard's domain graph (per
 * {@code aidocs/48 §3.3}'s "namespace separation" rail).
 *
 * <p><b>Graceful degradation.</b> If the n10s plugin is absent — e.g.
 * an operator who upgraded from upstream shepard but hasn't pulled
 * the new compose file — every call returns the "nothing found"
 * value ({@code healthCheck() == false}, {@code getTerm == empty
 * map}). This matches the contract of the existing
 * {@link SparqlConnector} for an unreachable endpoint, so callers
 * don't need special-casing.
 *
 * <p><b>Construction.</b> Prefer the no-arg ctor in production
 * (pulls the session from {@link de.dlr.shepard.common.neo4j.NeoConnector#getInstance()});
 * tests inject a mock via the {@link #InternalSemanticConnector(Session)}
 * seam.
 *
 * @see SemanticRepositoryType#INTERNAL
 * @see N10sBootstrapHook
 */
public class InternalSemanticConnector implements ISemanticRepositoryConnector {

  /**
   * Cypher that reads an RDF resource's labels via n10s's
   * "shortened" property naming. Two shapes are tolerated:
   * <ol>
   *   <li>n10s materialises {@code rdfs:label} as
   *       {@code rdfs__label} (single value or list when
   *       {@code handleMultival=ARRAY}).</li>
   *   <li>language-tagged labels become
   *       {@code rdfs__label@en}, {@code rdfs__label@de}, ...
   *       which OGM surfaces as plain map keys.</li>
   * </ol>
   */
  static final String GET_TERM_CYPHER =
    "MATCH (r:Resource {uri: $uri}) " + "RETURN properties(r) AS props " + "LIMIT 1";

  /**
   * Lightweight existence-probe for n10s. Returns 1 when the
   * procedure namespace is registered, 0 otherwise. We can't use
   * {@code CALL n10s.graphconfig.show()} directly because the
   * procedure raises in Neo4j 5 when called against an unconfigured
   * graph — counting the registration is exception-free.
   */
  static final String HEALTH_CYPHER =
    "CALL dbms.procedures() YIELD name WHERE name STARTS WITH 'n10s.' " +
    "RETURN count(name) > 0 AS available";

  private final Session session;

  /** Production ctor — pulls the OGM session at call time. */
  public InternalSemanticConnector() {
    this(de.dlr.shepard.common.neo4j.NeoConnector.getInstance().getNeo4jSession());
  }

  /** Test seam — accept a pre-built OGM session (typically a mock). */
  public InternalSemanticConnector(Session session) {
    this.session = session;
  }

  @Override
  public boolean healthCheck() {
    if (session == null) {
      Log.warn("InternalSemanticConnector: no OGM session available; reporting unhealthy.");
      return false;
    }
    try {
      var result = session.query(HEALTH_CYPHER, Collections.emptyMap());
      var it = result.queryResults().iterator();
      if (!it.hasNext()) return false;
      Object available = it.next().get("available");
      return Boolean.TRUE.equals(available);
    } catch (RuntimeException ex) {
      // Neo4j raises if `dbms.procedures()` is denied or n10s is
      // mid-load. Treat any failure as "not available" rather than
      // bubbling — the bootstrap hook is the source of truth and
      // logs explicitly.
      Log.warnf("InternalSemanticConnector: health probe failed (%s).", ex.getClass().getSimpleName());
      return false;
    }
  }

  @Override
  public Map<String, String> getTerm(String termIri) {
    if (session == null || termIri == null || termIri.isBlank()) {
      return Collections.emptyMap();
    }
    final Map<String, Object> props;
    try {
      var result = session.query(GET_TERM_CYPHER, Map.of("uri", termIri));
      var it = result.queryResults().iterator();
      if (!it.hasNext()) return Collections.emptyMap();
      Object raw = it.next().get("props");
      if (!(raw instanceof Map<?, ?> map)) return Collections.emptyMap();
      // Defensive copy — OGM may return an unmodifiable view.
      props = new LinkedHashMap<>();
      for (var e : map.entrySet()) {
        if (e.getKey() != null) props.put(e.getKey().toString(), e.getValue());
      }
    } catch (RuntimeException ex) {
      Log.warnf("InternalSemanticConnector: getTerm failed (%s).", ex.getClass().getSimpleName());
      return Collections.emptyMap();
    }

    return extractLabels(props);
  }

  /**
   * Pull every label-like property off an n10s {@code :Resource} node
   * and return a language-keyed map matching the
   * {@link ISemanticRepositoryConnector} contract.
   *
   * <p>n10s with {@code handleVocabUris=IGNORE} + {@code keepLangTag=true}
   * yields property names of the shape {@code rdfs__label} (no
   * language tag) or {@code rdfs__label@en} (language tag preserved).
   * For the bare form we use the empty string as the language key —
   * same convention as {@link SparqlConnector#parseResult}.
   */
  static Map<String, String> extractLabels(Map<String, Object> props) {
    if (props == null || props.isEmpty()) return Collections.emptyMap();
    Map<String, String> labels = new HashMap<>();
    for (var e : props.entrySet()) {
      String key = e.getKey();
      if (key == null) continue;
      // Only look at rdfs:label (n10s prefixes vocabulary with `__`).
      if (!key.startsWith("rdfs__label")) continue;
      String value = stringValue(e.getValue());
      if (value == null || value.isBlank()) continue;
      int at = key.indexOf('@');
      String lang = (at >= 0 && at < key.length() - 1) ? key.substring(at + 1) : "";
      labels.put(lang, value);
    }
    return labels;
  }

  private static String stringValue(Object raw) {
    if (raw == null) return null;
    // n10s with handleMultival=ARRAY returns a String[]; pick the
    // first non-blank entry to match SparqlConnector's contract of
    // returning a single label per language.
    if (raw instanceof String s) return s;
    if (raw instanceof String[] arr) {
      for (String s : arr) if (s != null && !s.isBlank()) return s;
      return null;
    }
    if (raw instanceof Iterable<?> it) {
      for (Object o : it) {
        if (o == null) continue;
        String s = o.toString();
        if (!s.isBlank()) return s;
      }
      return null;
    }
    return raw.toString();
  }
}
