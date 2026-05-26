package de.dlr.shepard.v2.quality.io;

/**
 * TPL11 — canned Cypher queries for the independence-proof check.
 *
 * <p>The provenance graph in this fork uses the {@code has_successor} relationship
 * (directed <em>from</em> predecessor <em>to</em> successor). To walk up to ancestors
 * we therefore traverse the <em>incoming</em> direction.
 *
 * <p><strong>Hop cap.</strong> Both queries are bounded at {@code *..10} hops.
 * This covers all realistic Shepard provenance chains (MFFD: ≤ 6 steps, LUMEN:
 * ≤ 15 test runs in a linear chain). If your campaign has a provenance chain
 * longer than 10 hops, shared ancestors beyond that horizon will not be detected —
 * the result is therefore a <em>best-effort</em> independence claim within a
 * 10-hop window, not a formal proof. The limit is a deliberate trade-off against
 * query runtime on large graphs.
 *
 * <p><strong>Relationship name.</strong> {@code has_successor} is the canonical
 * name as stored in Neo4j (see {@code Constants.HAS_SUCCESSOR}). The
 * {@code has_successor} edge runs from predecessor → successor; following it
 * in the incoming direction ({@code <-[:has_successor*]-}) walks from a node
 * back towards its ancestors.
 */
public final class IndependenceProofQuery {

  private IndependenceProofQuery() {}

  /**
   * Returns any DataObject nodes that are common provenance ancestors of
   * both {@code setA} and {@code setB} within a 10-hop ancestry window.
   *
   * <p>Parameters:
   * <ul>
   *   <li>{@code setA} — {@code List<String>} of DataObject appIds in set A</li>
   *   <li>{@code setB} — {@code List<String>} of DataObject appIds in set B</li>
   * </ul>
   *
   * <p>Returns rows: {@code sharedAncestorAppId} (String),
   * {@code reachableFromA} (List&lt;String&gt; — appIds from setA that reach the ancestor),
   * {@code reachableFromB} (List&lt;String&gt; — appIds from setB that reach the ancestor).
   *
   * <p>A DataObject in setA or setB is itself considered an ancestor when it
   * appears in both sets (zero-hop overlap).
   */
  public static final String SHARED_ANCESTORS = """
      WITH $setA AS setA, $setB AS setB
      MATCH (a:DataObject)<-[:has_successor*0..10]-(ancestor:DataObject)
      WHERE a.appId IN setA
        AND (a.deleted IS NULL OR a.deleted = false)
        AND (ancestor.deleted IS NULL OR ancestor.deleted = false)
      WITH ancestor, collect(DISTINCT a.appId) AS reachableFromA
      MATCH (b:DataObject)<-[:has_successor*0..10]-(ancestor)
      WHERE b.appId IN $setB
        AND (b.deleted IS NULL OR b.deleted = false)
      WITH ancestor, reachableFromA, collect(DISTINCT b.appId) AS reachableFromB
      WHERE size(reachableFromA) > 0 AND size(reachableFromB) > 0
      RETURN ancestor.appId AS sharedAncestorAppId,
             reachableFromA,
             reachableFromB
      ORDER BY sharedAncestorAppId
      """;

  /**
   * Returns annotation key-value pairs that appear on at least one DataObject
   * from {@code setA} and at least one DataObject from {@code setB}
   * with the <em>same key and the same value</em>.
   *
   * <p>Parameters:
   * <ul>
   *   <li>{@code setA} — {@code List<String>} of DataObject appIds in set A</li>
   *   <li>{@code setB} — {@code List<String>} of DataObject appIds in set B</li>
   * </ul>
   *
   * <p>Returns rows: {@code sharedKey} (String), {@code sharedValue} (String),
   * {@code fromA} (List&lt;String&gt; — appIds from setA that carry the annotation),
   * {@code fromB} (List&lt;String&gt; — appIds from setB that carry the annotation).
   *
   * <p>DataObject attributes are stored as a map property ({@code do.attributes}).
   * Neo4j does not support map-property decomposition natively; the service
   * layer performs the in-memory intersection after fetching the attribute maps.
   * This query fetches only the relevant rows.
   */
  public static final String ANNOTATIONS_FOR_SET = """
      MATCH (do:DataObject)
      WHERE do.appId IN $appIds
        AND (do.deleted IS NULL OR do.deleted = false)
      RETURN do.appId AS doAppId, do AS dataObject
      """;
}
