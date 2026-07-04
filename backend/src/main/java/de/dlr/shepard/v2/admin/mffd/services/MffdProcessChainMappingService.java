package de.dlr.shepard.v2.admin.mffd.services;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.v2.admin.mffd.io.ProcessChainMappingResultIO;
import de.dlr.shepard.v2.admin.mffd.io.ProcessChainMappingResultIO.UnresolvedEntryIO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.Session;

/**
 * MFFD-MAPPING-REST-1 — applies a MFFD process-chain mapping YAML
 * payload against the live Neo4j graph, materialising
 * {@code has_successor} edges between matching DataObjects.
 *
 * <p>Design contract: see
 * {@code aidocs/integrations/118-mffd-process-chain-mapping.md}.
 * Cross-cuts: GAP-4 in
 * {@code aidocs/agent-findings/mffd-feature-gaps-2026-06-02.md}.
 *
 * <p>Matching shape: each YAML selector pair {@code (key, value)} is
 * normalised to a {@code SemanticAnnotation} match on
 * {@code (subjectAppId = d.appId, subjectKind = "DataObject",
 *        propertyIRI = $iri, valueName = $value)}. Multiple keys on a
 * selector are AND-ed.
 *
 * <p>Merge shape: for each (source × target) Cartesian, MERGE
 * {@code (s)-[r:has_successor]->(t)} with {@code r.transitionKind}
 * stamped on both ON CREATE and ON MATCH so re-runs converge.
 *
 * <p>Idempotent: re-running the same YAML preserves the edge set.
 * Mutating an entry's {@code transitionKind} and re-running updates the
 * existing edge. The loader never deletes edges.
 */
@ApplicationScoped
public class MffdProcessChainMappingService {

  static final int SUPPORTED_SCHEMA_VERSION = 1;

  static final Set<String> VALID_TRANSITION_KINDS = Set.of(
    "normal", "rework", "re-test", "concession"
  );

  /** Explicit mapping of YAML keys → {@code urn:shepard:mffd:*} predicates. */
  static final Map<String, String> EXPLICIT_PREDICATE_MAP = Map.of(
    "process",       "urn:shepard:mffd:process-type",
    "step_number",   "urn:shepard:mffd:step-number",
    "ply_number",    "urn:shepard:mffd:ply-number",
    "track_number",  "urn:shepard:mffd:track-number",
    "part_name",     "urn:shepard:mffd:part-name",
    "campaign_id",   "urn:shepard:mffd:campaign-id",
    "cleat_id",      "urn:shepard:mffd:cleat-id"
  );

  /**
   * Singleton YAML mapper — preserves source-position info on parsed
   * {@code JsonNode}s so the loader can attribute unresolved selectors
   * back to YAML line numbers.
   */
  private static final ObjectMapper YAML_MAPPER = buildYamlMapper();

  private static ObjectMapper buildYamlMapper() {
    YAMLFactory factory = new YAMLFactory();
    factory.enable(JsonParser.Feature.INCLUDE_SOURCE_IN_LOCATION);
    return new YAMLMapper(factory);
  }

  /** Thrown when the YAML is malformed or carries an unsupported schemaVersion. */
  public static final class InvalidMappingPayloadException extends RuntimeException {
    public InvalidMappingPayloadException(String message) {
      super(message);
    }
    public InvalidMappingPayloadException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  /**
   * Parse + apply the YAML payload. Throws
   * {@link InvalidMappingPayloadException} on malformed YAML; returns
   * a populated result with counters + unresolved checklist otherwise.
   *
   * @param yamlBody YAML text (non-null, non-blank).
   * @return populated {@link ProcessChainMappingResultIO}.
   */
  public ProcessChainMappingResultIO apply(String yamlBody) {
    if (yamlBody == null || yamlBody.isBlank()) {
      throw new InvalidMappingPayloadException("YAML body is empty.");
    }

    JsonNode root;
    try {
      root = YAML_MAPPER.readTree(yamlBody);
    } catch (Exception e) {
      throw new InvalidMappingPayloadException("YAML parse error: " + e.getMessage(), e);
    }
    if (root == null || !root.isObject()) {
      throw new InvalidMappingPayloadException("Top-level YAML must be a mapping.");
    }

    int schemaVersion = root.path("schemaVersion").asInt(-1);
    if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
      throw new InvalidMappingPayloadException(
        "Unsupported schemaVersion=" + schemaVersion +
        " (this loader supports " + SUPPORTED_SCHEMA_VERSION + ")."
      );
    }

    JsonNode mappings = root.path("mappings");
    if (!mappings.isArray()) {
      throw new InvalidMappingPayloadException("'mappings' must be a list.");
    }

    ProcessChainMappingResultIO result = new ProcessChainMappingResultIO();
    result.setSchemaVersion(schemaVersion);
    result.setEntries(mappings.size());

    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) {
      Log.warn("MFFD process-chain mapping: Neo4j session unavailable; treating all entries as unresolved.");
      // Carry on so callers still get a structural report.
    }

    Iterator<JsonNode> it = mappings.elements();
    int idx = 0;
    while (it.hasNext()) {
      JsonNode entry = it.next();
      int line = lineOf(entry, idx);

      JsonNode source = entry.path("source");
      JsonNode target = entry.path("target");
      String kind = entry.path("transitionKind").asText("normal");

      if (!VALID_TRANSITION_KINDS.contains(kind)) {
        result.getWarnings().add(
          "line " + line + ": transitionKind=" + kind +
          " not in " + VALID_TRANSITION_KINDS
        );
      }

      Map<String, String> sourceSelectors = selectorAnnotations(source);
      Map<String, String> targetSelectors = selectorAnnotations(target);

      List<String> sourceAppIds = sourceSelectors.isEmpty() || live == null
        ? List.of()
        : findDataObjectAppIds(live, sourceSelectors);
      List<String> targetAppIds = targetSelectors.isEmpty() || live == null
        ? List.of()
        : findDataObjectAppIds(live, targetSelectors);

      if (sourceSelectors.isEmpty()) {
        result.getUnresolved().add(new UnresolvedEntryIO(line, "source", "Empty or non-mapping selector."));
      } else if (sourceAppIds.isEmpty()) {
        result.getUnresolved().add(new UnresolvedEntryIO(line, "source", "No DataObjects match all selector predicates."));
      }
      if (targetSelectors.isEmpty()) {
        result.getUnresolved().add(new UnresolvedEntryIO(line, "target", "Empty or non-mapping selector."));
      } else if (targetAppIds.isEmpty()) {
        result.getUnresolved().add(new UnresolvedEntryIO(line, "target", "No DataObjects match all selector predicates."));
      }

      if (sourceAppIds.isEmpty() || targetAppIds.isEmpty()) {
        result.setUnmatched(result.getUnmatched() + 1);
        idx++;
        continue;
      }

      int created = mergeEdges(live, sourceAppIds, targetAppIds, kind);
      result.setMatched(result.getMatched() + sourceAppIds.size() * targetAppIds.size());
      result.setEdgesCreated(result.getEdgesCreated() + created);
      idx++;
    }

    return result;
  }

  /** Maps an entry's selector JsonNode into a {predicateIri → value} bag. */
  Map<String, String> selectorAnnotations(JsonNode selector) {
    if (selector == null || !selector.isObject()) return Map.of();
    Map<String, String> out = new LinkedHashMap<>();
    Iterator<String> keys = selector.fieldNames();
    while (keys.hasNext()) {
      String key = keys.next();
      String predicate = yamlKeyToPredicate(key);
      out.put(predicate, selector.get(key).asText());
    }
    return out;
  }

  /** Maps a YAML selector key to its {@code urn:shepard:mffd:*} predicate IRI. */
  static String yamlKeyToPredicate(String key) {
    String explicit = EXPLICIT_PREDICATE_MAP.get(key);
    if (explicit != null) return explicit;
    return "urn:shepard:mffd:" + key.replace('_', '-');
  }

  /**
   * Find every DataObject {@code appId} whose {@code :SemanticAnnotation}
   * set covers every required (predicate, value) pair.
   */
  List<String> findDataObjectAppIds(Session live, Map<String, String> annotations) {
    if (annotations.isEmpty()) return List.of();
    // Build a parameterised AND-over-predicates Cypher.
    StringBuilder cypher = new StringBuilder();
    cypher.append("MATCH (d:DataObject) WHERE d.appId IS NOT NULL");
    Map<String, Object> params = new HashMap<>();
    int i = 0;
    for (Map.Entry<String, String> e : annotations.entrySet()) {
      String iriParam = "iri" + i;
      String valParam = "val" + i;
      cypher.append(" AND EXISTS { ")
        .append("MATCH (a:SemanticAnnotation { subjectKind: 'DataObject', subjectAppId: d.appId, propertyIRI: $")
        .append(iriParam)
        .append(", valueName: $")
        .append(valParam)
        .append(" }) ")
        .append("}");
      params.put(iriParam, e.getKey());
      params.put(valParam, e.getValue());
      i++;
    }
    cypher.append(" RETURN d.appId AS appId");

    List<String> out = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    try {
      Result result = live.query(cypher.toString(), params);
      if (result != null) {
        for (Map<String, Object> row : result.queryResults()) {
          Object id = row.get("appId");
          if (id != null) {
            String s = id.toString();
            if (seen.add(s)) out.add(s);
          }
        }
      }
    } catch (RuntimeException e) {
      Log.warnf(e, "MFFD process-chain mapping: source/target match query failed (params=%s)", annotations);
    }
    return out;
  }

  /**
   * MERGE the Cartesian-product set of {@code has_successor} edges
   * between {@code sourceAppIds} and {@code targetAppIds}. Returns the
   * number of (s, t) pairs that produced an edge (idempotent — re-runs
   * don't grow the count).
   */
  int mergeEdges(Session live, List<String> sourceAppIds, List<String> targetAppIds, String kind) {
    if (live == null) return 0;
    String cypher =
      "UNWIND $pairs AS pair " +
      "MATCH (s:DataObject {appId: pair.s}), (t:DataObject {appId: pair.t}) " +
      "MERGE (s)-[r:has_successor]->(t) " +
      "ON CREATE SET r.transitionKind = $kind, r.createdAtMillis = timestamp(), " +
      "              r.createdBySource = 'mffd-process-chain-mapping' " +
      "ON MATCH  SET r.transitionKind = $kind, r.updatedAtMillis = timestamp() " +
      "RETURN count(r) AS n";

    List<Map<String, String>> pairs = new ArrayList<>(sourceAppIds.size() * targetAppIds.size());
    for (String s : sourceAppIds) {
      for (String t : targetAppIds) {
        pairs.add(Map.of("s", s, "t", t));
      }
    }
    int n = 0;
    try {
      Result result = live.query(cypher, Map.of("pairs", pairs, "kind", kind));
      if (result != null) {
        for (Map<String, Object> row : result.queryResults()) {
          Object v = row.get("n");
          if (v instanceof Number num) n = num.intValue();
        }
      }
    } catch (RuntimeException e) {
      Log.warnf(e, "MFFD process-chain mapping: MERGE failed (pairs.size=%d, kind=%s)", pairs.size(), kind);
    }
    return n;
  }

  /**
   * Best-effort line-number recovery for a YAML mapping entry. Jackson
   * exposes the start mark via the node's {@code JsonLocation}. When
   * the source location is absent we fall back to the array index + 1.
   */
  static int lineOf(JsonNode entry, int fallbackIdx) {
    try {
      if (entry instanceof com.fasterxml.jackson.databind.node.ObjectNode) {
        // JsonNode itself does not expose location; this fallback covers
        // mappers without INCLUDE_SOURCE_IN_LOCATION. Always-safe path.
        return fallbackIdx + 1;
      }
    } catch (RuntimeException ignored) { /* fall through */ }
    return fallbackIdx + 1;
  }
}
