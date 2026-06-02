package de.dlr.shepard.v2.m4i;

import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.semantic.daos.SemanticAnnotationDAO;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.provenance.daos.ActivityDAO;
import de.dlr.shepard.provenance.entities.Activity;
import de.dlr.shepard.provenance.services.ProvJsonLdRenderer;
import de.dlr.shepard.publish.daos.PublicationDAO;
import de.dlr.shepard.publish.entities.Publication;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * M4I-c — metadata4ing (m4i) JSON-LD projection of a Shepard
 * {@link DataObject}.
 *
 * <p>Design source: {@code aidocs/semantics/94 §4.3} (M4I-c shape +
 * renderer) and {@code §4.4} (M4I-d resolvers). SHACL contract:
 * {@code backend/src/main/resources/shapes/m4i-dataobject-shape.ttl}.
 *
 * <h2>Architectural shape</h2>
 *
 * Per the design's §12 board consultation, this renderer is shaped as
 * an <strong>overlay renderer</strong>: it reads from the canonical
 * {@link DataObject} entity (loaded via the existing service path) and
 * emits a JSON-LD body whose {@code @context} declares the m4i / PROV-O
 * / OBO / QUDT / dcterms / schema.org namespaces. It does <em>not</em>
 * duplicate the DataObject's DAO path or talk to Neo4j directly for
 * core fields — it walks the in-memory graph already loaded by
 * {@code DataObjectService.getDataObject}.
 *
 * <p>The competing shape — a parallel renderer with its own Cypher
 * fetch — was rejected because it doubles the read path's complexity
 * (cache invalidation, permission re-check, Activity row lookup
 * timing) for no semantic gain. The Data Ontologist and RDM personas
 * agreed; the API Scrutinizer flagged "two read paths for one
 * resource" as an antipattern. See {@code aidocs/semantics/94 §12}.
 *
 * <h2>M4I-d resolvers (read-time, lazy)</h2>
 *
 * <ul>
 *   <li><b>Method</b> — when the most-recent Activity carries an
 *       {@code actionKind}, mint the IRI {@code shepard:method/<kind>}
 *       (typed {@code m4i:Method}) on the fly and emit
 *       {@code m4i:realizesMethod}. No write to the graph.</li>
 *   <li><b>Tool</b> — when the most-recent Activity carries a
 *       {@code targetKind}, mint {@code shepard:tool/<kind>} (typed
 *       {@code m4i:Tool}) and emit {@code m4i:hasEmployedTool}.</li>
 *   <li><b>NumericalVariable (DataObject level)</b> — for each direct
 *       {@link SemanticAnnotation} on the DataObject whose
 *       {@code numericValue} is set, emit a blank-node {@code
 *       m4i:NumericalVariable} carrying {@code m4i:hasValue} (xsd:double)
 *       and a {@code qudt:unit} reference when {@code unitIRI} is
 *       populated. Free-text annotations (no numeric value) fall
 *       through to {@code schema:keywords}.</li>
 *   <li><b>NumericalVariable (channel level, M4I-d-3-followup)</b> — for each
 *       timeseries channel reachable via the
 *       {@code DataObject → TimeseriesReference → TimeseriesContainer →
 *       AnnotatableTimeseries} multi-hop walk that carries a
 *       {@code urn:shepard:unit} semantic annotation, emit a blank-node
 *       {@code m4i:NumericalVariable} with {@code rdfs:label} (channel
 *       measurement name) and {@code m4i:hasUnit} (QUDT unit IRI). Channels
 *       without a unit annotation are silently skipped — no incomplete
 *       {@code NumericalVariable} nodes are emitted.</li>
 * </ul>
 */
@ApplicationScoped
public class M4iDataObjectRenderer {

  // ─── namespace constants ──────────────────────────────────────────────

  public static final String M4I_NS = "http://w3id.org/nfdi4ing/metadata4ing#";
  public static final String OBO_NS = "http://purl.obolibrary.org/obo/";
  public static final String PROV_NS = "http://www.w3.org/ns/prov#";
  public static final String DCTERMS_NS = "http://purl.org/dc/terms/";
  public static final String SCHEMA_NS = "http://schema.org/";
  public static final String QUDT_NS = "http://qudt.org/schema/qudt/";
  public static final String SHEPARD_NS = "https://noheton.github.io/shepard/prov#";
  public static final String XSD_NS = "http://www.w3.org/2001/XMLSchema#";
  public static final String RDFS_NS = "http://www.w3.org/2000/01/rdf-schema#";

  /** Stable m4i profile URI accepted on the {@code Accept} header. */
  public static final String M4I_PROFILE_URI = ProvJsonLdRenderer.M4I_PROFILE_URI;

  /** Convenience short form ({@code profile=metadata4ing}). */
  public static final String M4I_PROFILE_SHORT = ProvJsonLdRenderer.M4I_PROFILE_SHORT;

  /** JSON-LD media type. */
  public static final String MEDIA_TYPE = ProvJsonLdRenderer.MEDIA_TYPE;

  @Inject
  ActivityDAO activityDAO;

  @Inject
  SemanticAnnotationDAO semanticAnnotationDAO;

  @Inject
  PublicationDAO publicationDAO;

  // ─── public entry points ──────────────────────────────────────────────

  /**
   * Render a {@link DataObject} as an m4i JSON-LD body (standalone,
   * with an embedded {@code @context}). Returns a fresh
   * {@link LinkedHashMap} on every call.
   */
  public Map<String, Object> renderDataObject(DataObject dataObject) {
    Map<String, Object> out = new LinkedHashMap<>();
    if (dataObject == null) {
      return out;
    }
    out.put("@context", buildDataObjectContext());

    Map<String, Object> body = buildDataObjectNode(dataObject);
    // Merge body fields directly under the top level so a JSON-LD
    // framer sees one node, not a wrapped @graph singleton.
    for (Map.Entry<String, Object> e : body.entrySet()) {
      out.put(e.getKey(), e.getValue());
    }
    return out;
  }

  // ─── @context builder (shared with UH1b convergence point) ────────────

  /**
   * Build the canonical m4i {@code @context} block — the alphabet
   * used by every m4i-flavoured Shepard JSON-LD document.
   *
   * <p>Shared with {@link ProvJsonLdRenderer} (provenance trail) and
   * the UH1b Unhide feed renderer (per-Collection embeds): all three
   * speak the same prefix set so JSON-LD framers see a coherent
   * vocabulary. The set is the union of the three consumers'
   * requirements; extending here is the cheapest convergence point.
   */
  public static Map<String, Object> buildDataObjectContext() {
    Map<String, Object> ctx = new LinkedHashMap<>();
    ctx.put("m4i", M4I_NS);
    ctx.put("obo", OBO_NS);
    ctx.put("prov", PROV_NS);
    ctx.put("dcterms", DCTERMS_NS);
    ctx.put("schema", SCHEMA_NS);
    ctx.put("qudt", QUDT_NS);
    ctx.put("shepard", SHEPARD_NS);
    ctx.put("xsd", XSD_NS);
    ctx.put("rdfs", RDFS_NS);
    return ctx;
  }

  // ─── node-body builder ────────────────────────────────────────────────

  /**
   * Build the per-DataObject body (without the wrapping
   * {@code @context}) — the shape future contexts (UH1b
   * per-Collection embeds with nested DataObject summaries, an export
   * archive, an MCP tool result) can compose into a larger graph.
   */
  Map<String, Object> buildDataObjectNode(DataObject dataObject) {
    Map<String, Object> node = new LinkedHashMap<>();
    if (dataObject == null) {
      return node;
    }

    String appId = dataObject.getAppId();
    String id = "shepard:dataobject/" + (appId == null ? "anon" : appId);
    node.put("@id", id);
    node.put("@type", List.of("m4i:InvestigatedObject", "prov:Entity"));

    // ── mandatory triples ──
    if (appId != null) {
      node.put("dcterms:identifier", appId);
    }
    if (dataObject.getName() != null) {
      node.put("dcterms:title", dataObject.getName());
    }
    if (dataObject.getCreatedAt() != null) {
      node.put("schema:dateCreated", typedDateTime(dataObject.getCreatedAt().getTime()));
    }

    // ── optional descriptive triples ──
    if (dataObject.getDescription() != null && !dataObject.getDescription().isBlank()) {
      node.put("dcterms:description", dataObject.getDescription());
    }

    // ── KIP1a PID (m4i:hasIdentifier) ──
    addHasIdentifier(node, appId);

    // ── predecessor / successor (obo:RO_0002233/4) ──
    addPredecessorsAndSuccessors(node, dataObject);

    // ── most-recent Activity (prov:wasGeneratedBy + M4I-d-1/2 resolvers) ──
    addActivityProjection(node, appId);

    // ── annotations split: NumericalVariable vs keywords (M4I-d-3) ──
    addAnnotationProjection(node, appId);

    // ── channel-level NumericalVariable (M4I-d-3-followup) ──
    addChannelNumericalVariables(node, appId);

    return node;
  }

  // ─── M4I-c optional triple helpers ────────────────────────────────────

  private void addHasIdentifier(Map<String, Object> node, String appId) {
    if (appId == null || appId.isBlank()) return;
    try {
      List<Publication> pubs = publicationDAO.findByEntityAppId(appId);
      if (pubs == null || pubs.isEmpty()) return;
      Publication current = pubs.stream()
        .filter(p -> p != null && p.getPid() != null && !p.getPid().isBlank())
        .max(Comparator.comparing(p -> p.getMintedAt() == null ? 0L : p.getMintedAt()))
        .orElse(null);
      if (current == null) return;

      Map<String, Object> idNode = new LinkedHashMap<>();
      idNode.put("@type", "m4i:Identifier");
      idNode.put("m4i:identifierValue", current.getPid());
      idNode.put("m4i:hasIdentifierType", "Handle");
      node.put("m4i:hasIdentifier", idNode);
    } catch (RuntimeException e) {
      // Fail-soft per CLAUDE.md "secondary writes are fire-and-forget"
      // converse — secondary reads in the m4i renderer must not 500.
    }
  }

  private void addPredecessorsAndSuccessors(Map<String, Object> node, DataObject dataObject) {
    List<DataObject> preds = dataObject.getPredecessors();
    if (preds != null && !preds.isEmpty()) {
      List<Map<String, Object>> refs = new ArrayList<>(preds.size());
      for (DataObject p : preds) {
        if (p == null || p.isDeleted()) continue;
        String pid = p.getAppId();
        if (pid != null) refs.add(Map.of("@id", "shepard:dataobject/" + pid));
      }
      if (!refs.isEmpty()) {
        node.put("obo:RO_0002233", refs);
      }
    }
    List<DataObject> succs = dataObject.getSuccessors();
    if (succs != null && !succs.isEmpty()) {
      List<Map<String, Object>> refs = new ArrayList<>(succs.size());
      for (DataObject s : succs) {
        if (s == null || s.isDeleted()) continue;
        String sid = s.getAppId();
        if (sid != null) refs.add(Map.of("@id", "shepard:dataobject/" + sid));
      }
      if (!refs.isEmpty()) {
        node.put("obo:RO_0002234", refs);
      }
    }
  }

  // ─── M4I-d resolvers ──────────────────────────────────────────────────

  private void addActivityProjection(Map<String, Object> node, String appId) {
    if (appId == null || appId.isBlank()) return;
    Activity recent = mostRecentActivity(appId);
    if (recent == null) return;

    String activityId = "shepard:activity/" + (recent.getAppId() == null ? "anon" : recent.getAppId());
    node.put("prov:wasGeneratedBy", Map.of("@id", activityId));

    // M4I-d-1 — mint shepard:method/<actionKind> on the fly.
    String method = MethodResolver.iriFor(recent.getActionKind());
    if (method != null) {
      node.put("m4i:realizesMethod", Map.of("@id", method));
    }
    // M4I-d-2 — mint shepard:tool/<targetKind> on the fly.
    String tool = ToolResolver.iriFor(recent.getTargetKind());
    if (tool != null) {
      node.put("m4i:hasEmployedTool", Map.of("@id", tool));
    }
  }

  /**
   * Find the most-recent Activity targeting this DataObject. Caps the
   * lookup at one row by passing {@code limit=1} to
   * {@link ActivityDAO#list}. Fail-soft on Cypher errors.
   */
  private Activity mostRecentActivity(String appId) {
    try {
      List<Activity> rows = activityDAO.list(null, null, appId, null, null, 1);
      if (rows == null || rows.isEmpty()) return null;
      return rows.get(0);
    } catch (RuntimeException e) {
      return null;
    }
  }

  private void addAnnotationProjection(Map<String, Object> node, String appId) {
    if (appId == null || appId.isBlank()) return;
    List<SemanticAnnotation> annotations;
    try {
      annotations = semanticAnnotationDAO.findBySubjectAppId(appId);
    } catch (RuntimeException e) {
      return;
    }
    if (annotations == null || annotations.isEmpty()) return;

    List<Map<String, Object>> numericVars = new ArrayList<>();
    List<String> keywords = new ArrayList<>();

    for (SemanticAnnotation a : annotations) {
      if (a == null) continue;
      Map<String, Object> nv = NumericalVariableResolver.toNode(a);
      if (nv != null) {
        numericVars.add(nv);
      } else {
        String kw = keywordFor(a);
        if (kw != null && !keywords.contains(kw)) {
          keywords.add(kw);
        }
      }
    }

    if (!numericVars.isEmpty()) {
      node.put("m4i:hasNumericalVariable", numericVars);
    }
    if (!keywords.isEmpty()) {
      node.put("schema:keywords", keywords);
    }
  }

  /**
   * M4I-d-3-followup — emit one {@code m4i:NumericalVariable} blank node per
   * timeseries channel that carries a {@code urn:shepard:unit} annotation.
   *
   * <p>Walks the multi-hop path
   * {@code DataObject → TimeseriesReference → TimeseriesContainer → AnnotatableTimeseries}
   * via {@link de.dlr.shepard.context.semantic.daos.SemanticAnnotationDAO#findChannelUnitsByDataObjectAppId}.
   * Results are merged into the existing {@code m4i:hasNumericalVariable} list produced
   * by {@link #addAnnotationProjection} so the JSON-LD output contains both
   * DataObject-level numeric variables (from direct annotations with a
   * {@code numericValue}) and channel-level ones (from QUDT unit annotations on channels).
   *
   * <p>The {@code m4i:hasUnit} predicate (not {@code qudt:unit}) is used here because
   * this emission is describing a measured variable's unit — the distinction
   * aligns with the m4i 1.4.0 OWL where {@code m4i:NumericalVariable} carries
   * {@code m4i:hasUnit} (not the direct {@code qudt:unit} shortcut used for
   * scalar-value annotations).
   *
   * <p>Fail-soft — any DAO exception is caught; the renderer continues and the
   * caller receives the rest of the node.
   */
  private void addChannelNumericalVariables(Map<String, Object> node, String appId) {
    if (appId == null || appId.isBlank()) return;
    List<Map<String, Object>> channelRows;
    try {
      channelRows = semanticAnnotationDAO.findChannelUnitsByDataObjectAppId(appId);
    } catch (RuntimeException e) {
      return; // fail-soft per CLAUDE.md
    }
    if (channelRows == null || channelRows.isEmpty()) return;

    // Build the new channel-level NumericalVariable nodes.
    List<Map<String, Object>> channelNvs = new ArrayList<>(channelRows.size());
    for (Map<String, Object> row : channelRows) {
      Object labelObj = row.get("channelLabel");
      Object unitIriObj = row.get("unitIri");
      if (unitIriObj == null) continue;
      String unitIri = unitIriObj.toString();
      if (unitIri.isBlank()) continue;

      Map<String, Object> nv = new LinkedHashMap<>();
      nv.put("@type", "m4i:NumericalVariable");
      String label = (labelObj != null && !labelObj.toString().isBlank())
        ? labelObj.toString() : "channel";
      nv.put("rdfs:label", label);
      nv.put("m4i:hasUnit", Map.of("@id", unitIri));
      channelNvs.add(nv);
    }
    if (channelNvs.isEmpty()) return;

    // Merge with any DataObject-level NumericalVariable nodes already present.
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> existing =
      (List<Map<String, Object>>) node.get("m4i:hasNumericalVariable");
    if (existing == null) {
      node.put("m4i:hasNumericalVariable", channelNvs);
    } else {
      List<Map<String, Object>> merged = new ArrayList<>(existing);
      merged.addAll(channelNvs);
      node.put("m4i:hasNumericalVariable", merged);
    }
  }

  private static String keywordFor(SemanticAnnotation a) {
    String prop = a.getPropertyName();
    String val = a.getValueName();
    if (prop == null && val == null) return null;
    if (prop != null && val != null) return prop + "=" + val;
    return prop != null ? prop : val;
  }

  // ─── typed-literal helpers ────────────────────────────────────────────

  private static Map<String, Object> typedDateTime(long millis) {
    Map<String, Object> v = new LinkedHashMap<>();
    v.put("@type", "xsd:dateTime");
    v.put("@value", Instant.ofEpochMilli(millis).toString());
    return v;
  }

  // ─── M4I-d-1: MethodResolver ──────────────────────────────────────────

  /**
   * M4I-d-1 — lazy mint of {@code shepard:method/<actionKind>} IRIs
   * typed {@code m4i:Method}. Pure function; no DAO touch. The
   * read-time projection is the only emission surface; operators can
   * later refine labels via the admin custom-bundle path
   * ({@code aidocs/semantics/65 §2.3}).
   */
  static final class MethodResolver {

    private MethodResolver() {}

    /** Build the stable IRI form, or {@code null} for an unset action. */
    static String iriFor(String actionKind) {
      if (actionKind == null || actionKind.isBlank()) return null;
      return "shepard:method/" + actionKind;
    }
  }

  // ─── M4I-d-2: ToolResolver ────────────────────────────────────────────

  /**
   * M4I-d-2 — lazy mint of {@code shepard:tool/<targetKind>} IRIs
   * typed {@code m4i:Tool}. Pure function; no DAO touch. Operators
   * can later promote per-instrument names ({@code afp_robot},
   * {@code lbr_iiwa}, …) via the controlled-vocab table once
   * {@code aidocs/semantics/14} lands.
   */
  static final class ToolResolver {

    private ToolResolver() {}

    /** Build the stable IRI form, or {@code null} for an unset target. */
    static String iriFor(String targetKind) {
      if (targetKind == null || targetKind.isBlank()) return null;
      return "shepard:tool/" + targetKind;
    }
  }

  // ─── M4I-d-3: NumericalVariableResolver ───────────────────────────────

  /**
   * M4I-d-3 — promote a {@link SemanticAnnotation} carrying a numeric
   * value to a typed {@code m4i:NumericalVariable} blank-node body.
   * Returns {@code null} when the annotation has no numeric value (a
   * keyword-only annotation), routing the caller to the
   * {@code schema:keywords} fallback.
   *
   * <p>Consumes the QUDT unit IRIs written by the recovery script
   * {@code examples/mffd-showcase/scripts/recovery/annotate-channel-axes-and-units.py}
   * — when {@code SemanticAnnotation.unitIRI} is set (e.g.
   * {@code http://qudt.org/vocab/unit/N}), the body carries
   * {@code qudt:unit} pointing at that IRI.
   */
  static final class NumericalVariableResolver {

    private NumericalVariableResolver() {}

    /**
     * Build the {@code m4i:NumericalVariable} blank-node body, or
     * {@code null} when the annotation is not numeric.
     */
    static Map<String, Object> toNode(SemanticAnnotation a) {
      if (a == null) return null;
      Double numeric = a.getNumericValue();
      if (numeric == null) return null;

      Map<String, Object> nv = new LinkedHashMap<>();
      nv.put("@type", "m4i:NumericalVariable");
      String label = a.getPropertyName() != null ? a.getPropertyName()
        : (a.getValueName() != null ? a.getValueName() : "value");
      nv.put("rdfs:label", label);

      Map<String, Object> valueLiteral = new LinkedHashMap<>();
      valueLiteral.put("@type", "xsd:double");
      valueLiteral.put("@value", numeric.toString());
      nv.put("m4i:hasValue", valueLiteral);

      String unitIri = a.getUnitIRI();
      if (unitIri != null && !unitIri.isBlank()) {
        nv.put("qudt:unit", Map.of("@id", unitIri));
      }

      return nv;
    }
  }
}
