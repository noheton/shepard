package de.dlr.shepard.v2.quality.services;

import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.v2.quality.io.IndependenceProofQuery;
import de.dlr.shepard.v2.quality.io.IndependenceProofRequestIO;
import de.dlr.shepard.v2.quality.io.IndependenceProofResultIO;
import de.dlr.shepard.v2.quality.io.IndependenceProofResultIO.SharedAncestorIO;
import de.dlr.shepard.v2.quality.io.IndependenceProofResultIO.SharedAnnotationIO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.neo4j.ogm.session.Session;

/**
 * TPL11 — independence-proof service.
 *
 * <p>Checks whether two sets of DataObjects are independent from each other
 * with respect to:
 * <ol>
 *   <li><strong>Provenance ancestry</strong> — do any members of set A and set B share
 *       a common provenance ancestor (within a 10-hop window)? Ancestor walks are
 *       performed via the {@code has_successor} relationship in the incoming direction
 *       (predecessor → this node = outgoing; this node → successors = we walk backwards).</li>
 *   <li><strong>Annotation overlap</strong> — do any members of set A and set B share
 *       an annotation key-value pair (same key AND same value)? "Same value" is a strict
 *       string equality check. Attributes are stored as individual Neo4j node properties
 *       using the OGM {@code @Properties(delimiter="||")} convention
 *       ({@code attributes||<key>}); the service reads them via Neo4j's
 *       {@code properties()} function and performs the intersection in-process.</li>
 * </ol>
 *
 * <p>The check is <em>best-effort</em>: the ancestor walk is bounded at 10 hops,
 * and the annotation check covers only string-valued attributes (the standard
 * Shepard DataObject attribute type).
 */
@ApplicationScoped
public class IndependenceProofService {

  private static final String ATTRIBUTES_PREFIX = "attributes||";

  /** Neo4j OGM session — obtained via the shared {@link NeoConnector} singleton. */
  private final Session session;

  public IndependenceProofService() {
    this.session = NeoConnector.getInstance().getNeo4jSession();
  }

  /**
   * Injector-friendly constructor for tests (pass a mock or real session).
   *
   * @param session the Neo4j OGM session to use
   */
  IndependenceProofService(Session session) {
    this.session = session;
  }

  // ── public API ────────────────────────────────────────────────────────────

  /**
   * Run the full independence check.
   *
   * @param req the request carrying setA and setB (validated before this call)
   * @return an {@link IndependenceProofResultIO} with {@code independent=true}
   *         iff both ancestor and annotation checks find no overlap
   */
  public IndependenceProofResultIO check(IndependenceProofRequestIO req) {
    List<String> setA = req.getSetA();
    List<String> setB = req.getSetB();

    Log.debugf(
      "TPL11: independence-proof check — setA=%d items, setB=%d items",
      setA.size(),
      setB.size()
    );

    List<SharedAncestorIO> sharedAncestors = findSharedAncestors(setA, setB);
    List<SharedAnnotationIO> sharedAnnotations = findSharedAnnotations(setA, setB);

    boolean independent = sharedAncestors.isEmpty() && sharedAnnotations.isEmpty();

    Log.debugf(
      "TPL11: result — independent=%b, sharedAncestors=%d, sharedAnnotations=%d",
      independent,
      sharedAncestors.size(),
      sharedAnnotations.size()
    );

    return new IndependenceProofResultIO(independent, sharedAncestors, sharedAnnotations, Instant.now());
  }

  // ── ancestor check ────────────────────────────────────────────────────────

  @SuppressWarnings("unchecked")
  List<SharedAncestorIO> findSharedAncestors(List<String> setA, List<String> setB) {
    var result = session.query(
      IndependenceProofQuery.SHARED_ANCESTORS,
      Map.of("setA", setA, "setB", setB)
    );

    List<SharedAncestorIO> ancestors = new ArrayList<>();
    for (var row : result.queryResults()) {
      Object ancestorId = row.get("sharedAncestorAppId");
      Object fromA = row.get("reachableFromA");
      Object fromB = row.get("reachableFromB");

      if (ancestorId == null) continue;

      List<String> fromAList = toStringList(fromA);
      List<String> fromBList = toStringList(fromB);

      if (!fromAList.isEmpty() && !fromBList.isEmpty()) {
        ancestors.add(new SharedAncestorIO(ancestorId.toString(), fromAList, fromBList));
      }
    }
    return ancestors;
  }

  // ── annotation check ─────────────────────────────────────────────────────

  /**
   * Find annotation key-value pairs that appear on at least one DataObject
   * from setA and at least one DataObject from setB with the same value.
   *
   * <p>Implementation: fetch all node properties for both sets, extract the
   * {@code attributes||} prefix entries, then compute the intersection.
   * This is done in-process after a single Cypher call per set — acceptable
   * for typical Shepard dataset sizes (hundreds of DataObjects, dozens of
   * annotation keys per node).
   */
  List<SharedAnnotationIO> findSharedAnnotations(List<String> setA, List<String> setB) {
    // Fetch attribute maps for both sets in two queries
    Map<String, Map<String, String>> attrA = fetchAttributeMaps(setA);
    Map<String, Map<String, String>> attrB = fetchAttributeMaps(setB);

    // Build inverted index: (key, value) → list of appIds in A
    // key: "annotationKey::annotationValue"
    Map<String, List<String>> indexA = buildAnnotationIndex(attrA);
    Map<String, List<String>> indexB = buildAnnotationIndex(attrB);

    List<SharedAnnotationIO> shared = new ArrayList<>();
    for (Map.Entry<String, List<String>> entryA : indexA.entrySet()) {
      List<String> fromB = indexB.get(entryA.getKey());
      if (fromB != null && !fromB.isEmpty()) {
        // Split the compound key back into annotation key + value
        int sep = entryA.getKey().indexOf("::");
        if (sep < 0) continue;
        String annKey = entryA.getKey().substring(0, sep);
        String annVal = entryA.getKey().substring(sep + 2);
        shared.add(new SharedAnnotationIO(annKey, annVal, entryA.getValue(), fromB));
      }
    }
    return shared;
  }

  /**
   * Fetch all {@code attributes||} properties for the given appIds.
   *
   * @param appIds the DataObject appIds to query
   * @return map from appId → (annotationKey → annotationValue)
   */
  @SuppressWarnings("unchecked")
  private Map<String, Map<String, String>> fetchAttributeMaps(List<String> appIds) {
    if (appIds.isEmpty()) return Map.of();

    // Use Neo4j's properties() function to get the full property map per node,
    // then filter for the attributes|| prefix in-process.
    String query =
      "MATCH (do:DataObject) " +
      "WHERE do.appId IN $appIds " +
      "  AND (do.deleted IS NULL OR do.deleted = false) " +
      "RETURN do.appId AS doAppId, properties(do) AS props";

    Map<String, Map<String, String>> result = new HashMap<>();
    var queryResult = session.query(query, Map.of("appIds", appIds));
    for (var row : queryResult.queryResults()) {
      Object doAppId = row.get("doAppId");
      Object props = row.get("props");
      if (doAppId == null || !(props instanceof Map)) continue;

      Map<?, ?> propMap = (Map<?, ?>) props;
      Map<String, String> attrs = new HashMap<>();
      for (Map.Entry<?, ?> e : propMap.entrySet()) {
        String key = String.valueOf(e.getKey());
        if (key.startsWith(ATTRIBUTES_PREFIX) && e.getValue() != null) {
          String attrKey = key.substring(ATTRIBUTES_PREFIX.length());
          attrs.put(attrKey, String.valueOf(e.getValue()));
        }
      }
      result.put(doAppId.toString(), attrs);
    }
    return result;
  }

  /**
   * Build an inverted index from a per-DO attribute map.
   * The index key is {@code "<annotationKey>::<annotationValue>"},
   * the value is the list of DataObject appIds that carry that pair.
   *
   * <p>The {@code ::} separator is safe here because annotation keys in Shepard
   * are plain strings without {@code ::}; annotation values could theoretically
   * contain {@code ::} but the separator position is determined by the first
   * occurrence, so compound values are handled correctly.
   *
   * @param attrMap map from doAppId → (key → value)
   * @return inverted index (key::value → list of doAppIds)
   */
  private Map<String, List<String>> buildAnnotationIndex(Map<String, Map<String, String>> attrMap) {
    Map<String, List<String>> index = new HashMap<>();
    for (Map.Entry<String, Map<String, String>> doEntry : attrMap.entrySet()) {
      String doAppId = doEntry.getKey();
      for (Map.Entry<String, String> ann : doEntry.getValue().entrySet()) {
        if (ann.getValue() == null || ann.getValue().isBlank()) continue;
        String compound = ann.getKey() + "::" + ann.getValue();
        index.computeIfAbsent(compound, k -> new ArrayList<>()).add(doAppId);
      }
    }
    return index;
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  @SuppressWarnings("unchecked")
  private static List<String> toStringList(Object val) {
    if (val instanceof List<?> list) {
      List<String> result = new ArrayList<>();
      for (Object item : list) {
        if (item != null) result.add(item.toString());
      }
      return result;
    }
    if (val instanceof String s) {
      return List.of(s);
    }
    return List.of();
  }
}
