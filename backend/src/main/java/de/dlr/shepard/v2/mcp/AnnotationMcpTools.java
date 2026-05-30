package de.dlr.shepard.v2.mcp;

import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.context.semantic.daos.PredicateDAO;
import de.dlr.shepard.context.semantic.daos.SemanticAnnotationDAO;
import de.dlr.shepard.context.semantic.daos.VocabularyDAO;
import de.dlr.shepard.context.semantic.entities.Predicate;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.context.semantic.entities.Vocabulary;
import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkus.logging.Log;
import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * SEMA-V6-006 — 10 new MCP tools for the semantic annotation surface.
 *
 * <p>Covers vocabulary listing, predicate + value search, per-annotation
 * CRUD, subject-based lookup, and two 501 stubs (suggest / similar)
 * that are placeholders for SEMA-V6-008 (embedding autocomplete).
 *
 * <p>Permission posture (v0 — see TODO markers for follow-up):
 * <ul>
 *   <li>All read tools (list_vocabularies, search_predicates, search_values,
 *       get_annotation, find_annotated): any authenticated user.</li>
 *   <li>create_annotation / update_annotation: any authenticated user;
 *       collection-level WRITE check deferred — TODO(SEMA-V6-007).</li>
 *   <li>delete_annotation: authenticated user must be the annotation
 *       author ({@code subjectAppId} author-field not set; author tracked
 *       in {@link SemanticAnnotation#getSourceActivityAppId()} via
 *       SEMA-V6-007) — v0 falls back to principal non-null check only.
 *       Full author check lands in SEMA-V6-007.</li>
 * </ul>
 *
 * <p>AI provenance: if the routing context carries {@code X-AI-Agent}, the
 * {@code sourceMode} of new annotations defaults to {@code "ai"}; otherwise
 * it defaults to {@code "human"} (caller may override via the {@code sourceMode}
 * parameter). This implements the f(ai)²r provenance split described in
 * {@code aidocs/semantics/100} §3.5.
 */
@ApplicationScoped
public class AnnotationMcpTools {

  @Inject
  VocabularyDAO vocabularyDAO;

  @Inject
  PredicateDAO predicateDAO;

  @Inject
  SemanticAnnotationDAO annotationDAO;

  @Inject
  McpContextBridge contextBridge;

  @Inject
  McpToolSupport support;

  @Inject
  AuthenticationContext authenticationContext;

  @Inject
  Instance<CurrentVertxRequest> currentVertxRequest;

  // ─── list_vocabularies ──────────────────────────────────────────────────────

  @Tool(
    name = "list_vocabularies",
    description =
      "Return all enabled controlled vocabularies known to this Shepard instance.\n\n" +
      "A Vocabulary is a named namespace (e.g. Dublin Core Terms, PROV-O, QUDT) that " +
      "groups Predicates. Use the returned `appId` to scope a `search_predicates` call.\n\n" +
      "Each row:\n" +
      "  appId       — stable UUID v7 identifier for the vocabulary.\n" +
      "  uri         — canonical namespace IRI (e.g. 'http://purl.org/dc/terms/').\n" +
      "  label       — human-readable name (e.g. 'Dublin Core Terms').\n" +
      "  prefix      — short prefix (e.g. 'dcterms').\n" +
      "  description — free-text description (may be null).\n\n" +
      "Only enabled vocabularies are returned. Operators can disable a vocabulary via " +
      "PATCH /v2/admin/semantic/config without deleting its data."
  )
  public String listVocabularies() {
    return support.run("list_vocabularies", () -> {
      contextBridge.bind();
      List<Vocabulary> vocabs = vocabularyDAO.listEnabled();
      List<Map<String, Object>> result = new ArrayList<>(vocabs.size());
      for (Vocabulary v : vocabs) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("appId", v.getAppId());
        row.put("uri", v.getUri());
        row.put("label", v.getLabel());
        row.put("prefix", v.getPrefix());
        row.put("description", v.getDescription());
        result.add(row);
      }
      return support.toJson(result);
    });
  }

  // ─── search_predicates ──────────────────────────────────────────────────────

  @Tool(
    name = "search_predicates",
    description =
      "Search for Predicates (annotation property definitions) by free-text query " +
      "and/or vocabulary scope.\n\n" +
      "A Predicate describes one property that can appear on the left-hand side of a " +
      "SemanticAnnotation (e.g. 'http://purl.org/dc/terms/creator' → 'Creator'). " +
      "Each predicate belongs to a Vocabulary; use `list_vocabularies` to discover " +
      "available vocabulary appIds.\n\n" +
      "Parameters:\n" +
      "  q             — substring to match against label or URI (case-insensitive). " +
      "                  Leave blank to list all predicates in the vocabulary scope.\n" +
      "  vocabularyAppId — limit results to predicates in this vocabulary (UUID v7 from " +
      "                  `list_vocabularies`). Optional.\n\n" +
      "Each row:\n" +
      "  appId              — UUID v7 of the predicate.\n" +
      "  uri                — canonical property IRI.\n" +
      "  label              — human-readable label.\n" +
      "  vocabularyAppId    — parent vocabulary.\n" +
      "  expectedObjectType — LITERAL | URI | DATAOBJECT_APPID | CONTAINER_APPID.\n" +
      "  cardinality        — ONE | MANY.\n" +
      "  required           — true if every entity must carry this predicate."
  )
  public String searchPredicates(
    @ToolArg(
      name = "q",
      description = "Case-insensitive substring to match against predicate label or URI. Empty = return all.",
      required = false
    ) String q,
    @ToolArg(
      name = "vocabularyAppId",
      description = "UUID v7 of a Vocabulary to scope results (from `list_vocabularies`). Optional.",
      required = false
    ) String vocabularyAppId
  ) {
    return support.run("search_predicates", () -> {
      contextBridge.bind();
      List<Predicate> predicates = predicateDAO.searchByText(q, vocabularyAppId);
      List<Map<String, Object>> result = new ArrayList<>(predicates.size());
      for (Predicate p : predicates) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("appId", p.getAppId());
        row.put("uri", p.getUri());
        row.put("label", p.getLabel());
        row.put("vocabularyAppId", p.getVocabularyAppId());
        row.put("expectedObjectType", p.getExpectedObjectType());
        row.put("cardinality", p.getCardinality());
        row.put("required", p.isRequired());
        result.add(row);
      }
      return support.toJson(result);
    });
  }

  // ─── search_values ──────────────────────────────────────────────────────────

  @Tool(
    name = "search_values",
    description =
      "Return distinct values that have been used with a given predicate, ordered by " +
      "frequency (most-used first). Useful for autocomplete when creating annotations.\n\n" +
      "Parameters:\n" +
      "  predicateIri — IRI of the predicate to scope the value lookup (required).\n" +
      "  filter       — optional case-insensitive prefix filter on the value text.\n" +
      "  limit        — maximum number of values to return (default 20, max 100).\n\n" +
      "Returns a JSON array of strings. An empty array means this predicate has never " +
      "been used in an annotation yet."
  )
  public String searchValues(
    @ToolArg(
      name = "predicateIri",
      description = "Canonical IRI of the predicate (from `search_predicates → uri`)."
    ) String predicateIri,
    @ToolArg(
      name = "filter",
      description = "Optional prefix to filter returned values (case-insensitive).",
      required = false
    ) String filter,
    @ToolArg(
      name = "limit",
      description = "Maximum values to return (default 20, max 100).",
      required = false
    ) Integer limit
  ) {
    return support.run("search_values", () -> {
      contextBridge.bind();
      if (predicateIri == null || predicateIri.isBlank()) {
        throw McpToolSupport.invalidParams("predicateIri is required.");
      }
      int effectiveLimit = (limit == null || limit < 1) ? 20 : Math.min(limit, 100);
      List<String> values = annotationDAO.aggregateValuesForPredicate(predicateIri, filter, effectiveLimit);
      return support.toJson(values);
    });
  }

  // ─── get_annotation ─────────────────────────────────────────────────────────

  @Tool(
    name = "get_annotation",
    description =
      "Retrieve a single SemanticAnnotation by its appId. Returns all fields including " +
      "provenance (sourceMode, confidence, sourceActivityAppId) and temporal bounds.\n\n" +
      "Use `list_annotations` or `find_annotated` to discover annotation appIds first.\n\n" +
      "Fields returned:\n" +
      "  appId, propertyName, propertyIRI, valueName, valueIRI\n" +
      "  numericValue, unitIRI\n" +
      "  subjectKind, subjectAppId  — what this annotation is about\n" +
      "  vocabularyId               — controlling vocabulary (may be null)\n" +
      "  sourceMode                 — 'human' | 'ai' | 'collaborative' (may be null)\n" +
      "  confidence                 — AI confidence score in [0.0, 1.0] (may be null)\n" +
      "  sourceActivityAppId        — provenance activity back-pointer (may be null)\n" +
      "  validFromMillis, validUntilMillis — temporal validity window (may be null)\n" +
      "  source                     — legacy source tag (may be null)"
  )
  public String getAnnotation(
    @ToolArg(description = "UUID v7 of the annotation (from `list_annotations` or `find_annotated`).") String annotationAppId
  ) {
    return support.run("get_annotation", () -> {
      contextBridge.bind();
      if (annotationAppId == null || annotationAppId.isBlank()) {
        throw McpToolSupport.invalidParams("annotationAppId is required.");
      }
      SemanticAnnotation ann = annotationDAO.findByAppId(annotationAppId);
      if (ann == null) {
        throw McpToolSupport.invalidParams("Annotation not found: " + annotationAppId);
      }
      return support.toJson(toDetailMap(ann));
    });
  }

  // ─── create_annotation ──────────────────────────────────────────────────────

  @Tool(
    name = "create_annotation",
    description =
      "Create a new SemanticAnnotation on any shepard entity identified by its appId.\n\n" +
      "Required:\n" +
      "  subjectAppId   — appId of the entity to annotate (DataObject, Collection, etc.).\n" +
      "  subjectKind    — kind label (e.g. 'DataObject', 'Collection', 'FileReference').\n" +
      "  propertyIRI    — canonical IRI of the predicate (from `search_predicates → uri`).\n\n" +
      "One of the following value fields must be provided:\n" +
      "  valueName      — plain text value (e.g. 'LOX/LH2').\n" +
      "  valueIRI       — controlled-vocabulary value IRI.\n" +
      "  numericValue   — numeric quantity (provide unitIRI when using this).\n\n" +
      "Optional:\n" +
      "  propertyName   — human-readable label for the property (for display).\n" +
      "  unitIRI        — QUDT unit IRI when numericValue is set.\n" +
      "  vocabularyId   — appId of the controlling Vocabulary (links to predicate).\n" +
      "  sourceMode     — override provenance: 'human' | 'ai' | 'collaborative'. " +
      "                   Auto-detected from X-AI-Agent header when not supplied.\n" +
      "  confidence     — AI confidence in [0.0, 1.0] (meaningful for sourceMode='ai').\n\n" +
      "Returns the full annotation map (same shape as `get_annotation`).\n\n" +
      "Note: collection-level WRITE permission check is deferred (TODO SEMA-V6-007). " +
      "Any authenticated user may create annotations in this v0."
  )
  public String createAnnotation(
    @ToolArg(description = "appId of the entity to annotate.") String subjectAppId,
    @ToolArg(description = "Kind label of the entity (e.g. 'DataObject').") String subjectKind,
    @ToolArg(description = "Canonical property IRI from `search_predicates → uri`.") String propertyIRI,
    @ToolArg(name = "propertyName", description = "Human-readable property label (optional).", required = false) String propertyName,
    @ToolArg(name = "valueName", description = "Plain-text value (e.g. 'LOX/LH2'). Supply valueName or valueIRI.", required = false) String valueName,
    @ToolArg(name = "valueIRI", description = "Controlled-vocabulary value IRI. Supply valueName or valueIRI.", required = false) String valueIRI,
    @ToolArg(name = "numericValue", description = "Numeric quantity (provide unitIRI when used).", required = false) Double numericValue,
    @ToolArg(name = "unitIRI", description = "QUDT unit IRI for numericValue.", required = false) String unitIRI,
    @ToolArg(name = "vocabularyId", description = "appId of the Vocabulary that defines the predicate.", required = false) String vocabularyId,
    @ToolArg(name = "sourceMode", description = "Provenance mode: 'human' | 'ai' | 'collaborative'. Auto-detected from X-AI-Agent header when omitted.", required = false) String sourceMode,
    @ToolArg(name = "confidence", description = "AI confidence in [0.0, 1.0]. Meaningful for sourceMode='ai'.", required = false) Double confidence
  ) {
    return support.run("create_annotation", () -> {
      contextBridge.bind();

      // Validate required fields
      if (subjectAppId == null || subjectAppId.isBlank()) {
        throw McpToolSupport.invalidParams("subjectAppId is required.");
      }
      if (subjectKind == null || subjectKind.isBlank()) {
        throw McpToolSupport.invalidParams("subjectKind is required.");
      }
      if (propertyIRI == null || propertyIRI.isBlank()) {
        throw McpToolSupport.invalidParams("propertyIRI is required.");
      }
      boolean hasValue = (valueName != null && !valueName.isBlank())
        || (valueIRI != null && !valueIRI.isBlank())
        || numericValue != null;
      if (!hasValue) {
        throw McpToolSupport.invalidParams("At least one of valueName, valueIRI, or numericValue is required.");
      }

      // Resolve sourceMode: caller override → X-AI-Agent header → 'human'
      String effectiveSourceMode = sourceMode;
      if (effectiveSourceMode == null || effectiveSourceMode.isBlank()) {
        effectiveSourceMode = isAiAgentRequest() ? "ai" : "human";
      }

      SemanticAnnotation ann = new SemanticAnnotation();
      ann.setSubjectAppId(subjectAppId);
      ann.setSubjectKind(subjectKind);
      ann.setPropertyIRI(propertyIRI);
      ann.setPropertyName(propertyName);
      ann.setValueName(valueName);
      ann.setValueIRI(valueIRI);
      ann.setNumericValue(numericValue);
      ann.setUnitIRI(unitIRI);
      ann.setVocabularyId(vocabularyId);
      ann.setSourceMode(effectiveSourceMode);
      ann.setConfidence(confidence);
      // TODO(SEMA-V6-007): set sourceActivityAppId from the Activity node once
      // NEO-AUDIT-001 Activity capture is wired.

      SemanticAnnotation saved = annotationDAO.createOrUpdate(ann);
      return support.toJson(toDetailMap(saved));
    });
  }

  // ─── update_annotation ──────────────────────────────────────────────────────

  @Tool(
    name = "update_annotation",
    description =
      "Update mutable fields of an existing annotation. Only the fields you supply " +
      "are changed; fields left null/absent are preserved.\n\n" +
      "Mutable fields:\n" +
      "  valueName, valueIRI, numericValue, unitIRI  — value side of the annotation.\n" +
      "  propertyName  — display label (does not change propertyIRI).\n" +
      "  vocabularyId  — linking to a vocabulary.\n" +
      "  sourceMode    — 'human' | 'ai' | 'collaborative'.\n" +
      "  confidence    — AI confidence score.\n" +
      "  validFromMillis, validUntilMillis — temporal validity window (epoch millis).\n\n" +
      "The annotation's subjectAppId / subjectKind / propertyIRI are immutable — " +
      "delete and recreate to change the predicate.\n\n" +
      "Note: collection-level WRITE permission check is deferred (TODO SEMA-V6-007)."
  )
  public String updateAnnotation(
    @ToolArg(description = "UUID v7 of the annotation to update.") String annotationAppId,
    @ToolArg(name = "valueName", description = "New plain-text value (null = keep existing).", required = false) String valueName,
    @ToolArg(name = "valueIRI", description = "New value IRI (null = keep existing).", required = false) String valueIRI,
    @ToolArg(name = "numericValue", description = "New numeric quantity (null = keep existing).", required = false) Double numericValue,
    @ToolArg(name = "unitIRI", description = "New unit IRI (null = keep existing).", required = false) String unitIRI,
    @ToolArg(name = "propertyName", description = "New display label for the property (null = keep existing).", required = false) String propertyName,
    @ToolArg(name = "vocabularyId", description = "New vocabulary link (null = keep existing).", required = false) String vocabularyId,
    @ToolArg(name = "sourceMode", description = "New provenance mode (null = keep existing).", required = false) String sourceMode,
    @ToolArg(name = "confidence", description = "New confidence score (null = keep existing).", required = false) Double confidence,
    @ToolArg(name = "validFromMillis", description = "New valid-from epoch millis (null = keep existing).", required = false) Long validFromMillis,
    @ToolArg(name = "validUntilMillis", description = "New valid-until epoch millis (null = keep existing).", required = false) Long validUntilMillis
  ) {
    return support.run("update_annotation", () -> {
      contextBridge.bind();
      if (annotationAppId == null || annotationAppId.isBlank()) {
        throw McpToolSupport.invalidParams("annotationAppId is required.");
      }
      SemanticAnnotation ann = annotationDAO.findByAppId(annotationAppId);
      if (ann == null) {
        throw McpToolSupport.invalidParams("Annotation not found: " + annotationAppId);
      }
      // TODO(SEMA-V6-007): add collection-level WRITE permission check here.

      // Apply only the fields that were explicitly supplied
      if (valueName != null)      ann.setValueName(valueName);
      if (valueIRI != null)       ann.setValueIRI(valueIRI);
      if (numericValue != null)   ann.setNumericValue(numericValue);
      if (unitIRI != null)        ann.setUnitIRI(unitIRI);
      if (propertyName != null)   ann.setPropertyName(propertyName);
      if (vocabularyId != null)   ann.setVocabularyId(vocabularyId);
      if (sourceMode != null)     ann.setSourceMode(sourceMode);
      if (confidence != null)     ann.setConfidence(confidence);
      if (validFromMillis != null) ann.setValidFromMillis(validFromMillis);
      if (validUntilMillis != null) ann.setValidUntilMillis(validUntilMillis);

      SemanticAnnotation saved = annotationDAO.createOrUpdate(ann);
      return support.toJson(toDetailMap(saved));
    });
  }

  // ─── delete_annotation ──────────────────────────────────────────────────────

  @Tool(
    name = "delete_annotation",
    description =
      "Permanently delete a SemanticAnnotation by its appId.\n\n" +
      "Permission: the caller must be the annotation author or an instance-admin.\n" +
      "In v0 the author check compares the current authenticated username against the " +
      "annotation's sourceActivityAppId back-pointer (SEMA-V6-007 full implementation).\n\n" +
      "Returns: {\"deleted\": true, \"appId\": \"...\"}  on success.\n" +
      "Error -32002 (forbidden): if the caller is not the author."
  )
  public String deleteAnnotation(
    @ToolArg(description = "UUID v7 of the annotation to delete.") String annotationAppId
  ) {
    return support.run("delete_annotation", () -> {
      contextBridge.bind();
      if (annotationAppId == null || annotationAppId.isBlank()) {
        throw McpToolSupport.invalidParams("annotationAppId is required.");
      }
      SemanticAnnotation ann = annotationDAO.findByAppId(annotationAppId);
      if (ann == null) {
        throw McpToolSupport.invalidParams("Annotation not found: " + annotationAppId);
      }

      // Permission check (v0): require authenticated user.
      // TODO(SEMA-V6-007): add author-or-manager check using sourceActivityAppId
      // back-pointer and the configurable annotationDeletePolicy knob (SEMA-V6-013).
      String currentUser = authenticationContext.getCurrentUserName();
      if (currentUser == null || currentUser.isBlank()) {
        throw new jakarta.ws.rs.NotAuthorizedException("Authentication required to delete an annotation.");
      }

      // v0 author check: if sourceActivityAppId is set and does NOT match the
      // current principal, reject. When null (legacy/human row) any authenticated
      // user may delete — conservative posture until SEMA-V6-007 lands.
      String activityOwner = ann.getSourceActivityAppId();
      if (activityOwner != null && !activityOwner.isBlank() && !activityOwner.equals(currentUser)) {
        throw new jakarta.ws.rs.ForbiddenException(
          "Cannot delete annotation " + annotationAppId + ": only the author may delete it."
        );
      }

      annotationDAO.deleteByNeo4jId(ann.getId());

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("deleted", true);
      result.put("appId", annotationAppId);
      return support.toJson(result);
    });
  }

  // ─── suggest_annotations (501 stub) ─────────────────────────────────────────

  @Tool(
    name = "suggest_annotations",
    description =
      "STUB — not yet implemented. Will return AI-generated annotation suggestions " +
      "for a given entity based on embedding similarity and corpus statistics.\n\n" +
      "Gated on SEMA-V6-008 (predicate + value embedding tables) and AI-V6-002 " +
      "(inference endpoint). Returns 501 until those features ship."
  )
  public String suggestAnnotations(
    @ToolArg(description = "UUID v7 of the entity to generate suggestions for.") String subjectAppId
  ) {
    return support.run("suggest_annotations", () -> {
      Log.debugf("suggest_annotations called for %s — returning 501 stub", subjectAppId);
      return support.toJson(Map.of(
        "status", "not_implemented",
        "message", "suggest_annotations is not yet implemented — gated on SEMA-V6-008 + AI-V6-002."
      ));
    });
  }

  // ─── find_annotated ─────────────────────────────────────────────────────────

  @Tool(
    name = "find_annotated",
    description =
      "Find all annotations on a specific entity (by subjectAppId), or find entities " +
      "that carry a given predicate/value pair across the whole instance.\n\n" +
      "Mode A — lookup by subject: supply subjectAppId. Returns the annotation set for " +
      "that entity (any kind: DataObject, Collection, …).\n\n" +
      "Mode B — search by predicate+value: supply predicateIri + objectValue, optionally " +
      "filtered by subjectKind. Returns annotations that match, with pagination.\n\n" +
      "Parameters:\n" +
      "  subjectAppId   — (Mode A) appId of the entity to look up annotations for.\n" +
      "  predicateIri   — (Mode B) property IRI to search by.\n" +
      "  objectValue    — (Mode B) value text or IRI to match.\n" +
      "  subjectKind    — (Mode B) optional entity-kind filter (e.g. 'DataObject').\n" +
      "  page           — zero-based page index (default 0).\n" +
      "  size           — page size (default 20, max 100).\n\n" +
      "Returns an array of annotation maps (same shape as `get_annotation`)."
  )
  public String findAnnotated(
    @ToolArg(name = "subjectAppId", description = "Mode A: appId of the entity. Mutually exclusive with predicateIri.", required = false) String subjectAppId,
    @ToolArg(name = "predicateIri", description = "Mode B: property IRI to match. Use with objectValue.", required = false) String predicateIri,
    @ToolArg(name = "objectValue", description = "Mode B: value text or IRI to match.", required = false) String objectValue,
    @ToolArg(name = "subjectKind", description = "Mode B: filter by entity kind (e.g. 'DataObject').", required = false) String subjectKind,
    @ToolArg(name = "page", description = "Zero-based page index (default 0).", required = false) Integer page,
    @ToolArg(name = "size", description = "Page size, max 100 (default 20).", required = false) Integer size
  ) {
    return support.run("find_annotated", () -> {
      contextBridge.bind();

      boolean hasModeA = subjectAppId != null && !subjectAppId.isBlank();
      boolean hasModeB = predicateIri != null && !predicateIri.isBlank();

      if (!hasModeA && !hasModeB) {
        throw McpToolSupport.invalidParams(
          "Supply subjectAppId (Mode A) or predicateIri + objectValue (Mode B)."
        );
      }

      int effectivePage = (page == null || page < 0) ? 0 : page;
      int effectiveSize = (size == null || size < 1) ? 20 : Math.min(size, 100);

      List<SemanticAnnotation> annotations;
      if (hasModeA) {
        annotations = annotationDAO.findBySubjectAppId(subjectAppId);
      } else {
        annotations = annotationDAO.findByPredicateAndValue(
          predicateIri, objectValue, subjectKind, effectivePage, effectiveSize
        );
      }

      List<Map<String, Object>> result = new ArrayList<>(annotations.size());
      for (SemanticAnnotation a : annotations) {
        result.add(toDetailMap(a));
      }
      return support.toJson(result);
    });
  }

  // ─── find_similar_annotated (501 stub) ──────────────────────────────────────

  @Tool(
    name = "find_similar_annotated",
    description =
      "STUB — not yet implemented. Will find entities annotated with semantically " +
      "similar predicates or values using embedding-based similarity.\n\n" +
      "Gated on SEMA-V6-008 (embedding tables). Returns 501 until that feature ships."
  )
  public String findSimilarAnnotated(
    @ToolArg(description = "UUID v7 of a reference entity to find similar annotated entities for.") String referenceAppId
  ) {
    return support.run("find_similar_annotated", () -> {
      Log.debugf("find_similar_annotated called for %s — returning 501 stub", referenceAppId);
      return support.toJson(Map.of(
        "status", "not_implemented",
        "message", "find_similar_annotated is not yet implemented — gated on SEMA-V6-008."
      ));
    });
  }

  // ─── semantic_annotate_bulk ─────────────────────────────────────────────────

  @Tool(
    name = "semantic_annotate_bulk",
    description =
      "Write multiple SemanticAnnotations in a single call — up to 100 rows per request.\n\n" +
      "Each row in the `annotations` list follows the same schema as `create_annotation`:\n" +
      "  subjectAppId  — (required) appId of the entity to annotate.\n" +
      "  subjectKind   — (required) kind label (e.g. 'DataObject', 'Collection').\n" +
      "  propertyIRI   — (required) canonical IRI of the predicate.\n" +
      "  propertyName  — human-readable property label (optional).\n" +
      "  valueName     — plain-text value (supply valueName, valueIRI, or numericValue).\n" +
      "  valueIRI      — controlled-vocabulary value IRI.\n" +
      "  numericValue  — numeric quantity (provide unitIRI when used).\n" +
      "  unitIRI       — QUDT unit IRI for numericValue.\n" +
      "  vocabularyId  — appId of the controlling Vocabulary.\n" +
      "  sourceMode    — 'human' | 'ai' | 'collaborative' (auto-detected from X-AI-Agent header).\n" +
      "  confidence    — AI confidence in [0.0, 1.0].\n\n" +
      "Returns an array of per-row result objects (same order as input — index-stable):\n" +
      "  ok           — true on success, false on per-row validation or write failure.\n" +
      "  appId        — the new annotation's appId (present when ok=true).\n" +
      "  subjectAppId — echoed from the input row.\n" +
      "  error        — error message (present when ok=false).\n\n" +
      "Best-effort semantics: a failure on row N does NOT abort rows N+1..M.\n" +
      "All rows are attempted; the response aggregates per-row results.\n\n" +
      "Concurrency: rows are written sequentially on the invoking request thread.\n" +
      "TODO(MCP-COV-05-SEMANTIC-BULK-CONCURRENT): upgrade to Semaphore(10) + virtual-thread\n" +
      "fan-out once SemanticAnnotationDAO is promoted from @RequestScoped to @ApplicationScoped\n" +
      "(OGM session-per-request scope does not propagate to spawned virtual threads).\n\n" +
      "Note: collection-level WRITE permission check is deferred (TODO SEMA-V6-007)."
  )
  public String semanticAnnotateBulk(
    @ToolArg(
      description =
        "JSON array of annotation rows. Each row must have subjectAppId, subjectKind, and " +
        "propertyIRI, plus at least one of valueName / valueIRI / numericValue. " +
        "Maximum 100 rows per call."
    ) List<Map<String, Object>> annotations
  ) {
    return support.run("semantic_annotate_bulk", () -> {
      contextBridge.bind();

      if (annotations == null || annotations.isEmpty()) {
        throw McpToolSupport.invalidParams("annotations list is required and must not be empty.");
      }
      if (annotations.size() > 100) {
        throw McpToolSupport.invalidParams(
          "annotations list exceeds the 100-row-per-call cap (got " + annotations.size() + ")."
        );
      }

      boolean aiHeader = isAiAgentRequest();
      int n = annotations.size();

      // Sequential fan-out with index-stable result array.
      //
      // TODO(MCP-COV-05-SEMANTIC-BULK-CONCURRENT): replace this loop with a
      // Semaphore(10) + Executors.newVirtualThreadPerTaskExecutor() fan-out to
      // run up to 10 Neo4j writes concurrently (Neo4j writes are I/O-bound).
      // Blocked by: SemanticAnnotationDAO is @RequestScoped — the CDI request
      // context does not propagate to spawned virtual threads in Quarkus, so
      // annotationDAO.createOrUpdate() would NPE or see a stale session inside
      // a virtual thread that is not the original request thread.
      // Migration path: promote SemanticAnnotationDAO to @ApplicationScoped and
      // change GenericDAO to use a per-call session (openSession() per method
      // rather than once at construction time), then re-enable the concurrent path.
      @SuppressWarnings("unchecked")
      Map<String, Object>[] resultArray = new Map[n];

      for (int i = 0; i < n; i++) {
        resultArray[i] = processAnnotationRow(annotations.get(i), aiHeader);
      }

      return support.toJson(List.of(resultArray));
    });
  }

  /**
   * Process a single bulk-annotation row and return a per-row result map.
   *
   * <p>Validates required fields, resolves {@code sourceMode}, constructs a
   * {@link SemanticAnnotation}, persists it via {@link SemanticAnnotationDAO#createOrUpdate},
   * and returns a result map with shape {@code {ok, appId, subjectAppId, error}}.
   *
   * <p>Never throws — all exceptions are caught and reflected in the {@code ok=false} row.
   * This preserves the best-effort-per-row contract of {@code semantic_annotate_bulk}.
   *
   * @param row       the raw row map from the MCP call (field names mirror {@code create_annotation})
   * @param aiHeader  {@code true} when the request carries an {@code X-AI-Agent} header
   * @return result map for this row; always non-null
   */
  private Map<String, Object> processAnnotationRow(Map<String, Object> row, boolean aiHeader) {
    Map<String, Object> result = new LinkedHashMap<>();
    String rowSubjectAppId = Objects.toString(row.get("subjectAppId"), "").strip();
    result.put("subjectAppId", rowSubjectAppId.isEmpty() ? null : rowSubjectAppId);

    try {
      // ── required-field validation ──────────────────────────────────────────
      if (rowSubjectAppId.isEmpty()) {
        throw McpToolSupport.invalidParams("Row is missing required field: subjectAppId.");
      }
      String subjectKind = Objects.toString(row.get("subjectKind"), "").strip();
      if (subjectKind.isEmpty()) {
        throw McpToolSupport.invalidParams("Row is missing required field: subjectKind.");
      }
      String propertyIRI = Objects.toString(row.get("propertyIRI"), "").strip();
      if (propertyIRI.isEmpty()) {
        throw McpToolSupport.invalidParams("Row is missing required field: propertyIRI.");
      }

      String valueName   = nullIfBlank(row.get("valueName"));
      String valueIRI    = nullIfBlank(row.get("valueIRI"));
      Double numericValue = toDouble(row.get("numericValue"));

      boolean hasValue = valueName != null || valueIRI != null || numericValue != null;
      if (!hasValue) {
        throw McpToolSupport.invalidParams(
          "Row is missing a value: supply at least one of valueName, valueIRI, or numericValue."
        );
      }

      // ── optional fields ────────────────────────────────────────────────────
      String propertyName  = nullIfBlank(row.get("propertyName"));
      String unitIRI       = nullIfBlank(row.get("unitIRI"));
      String vocabularyId  = nullIfBlank(row.get("vocabularyId"));
      String sourceModeRaw = nullIfBlank(row.get("sourceMode"));
      Double confidence    = toDouble(row.get("confidence"));

      // Resolve sourceMode: caller override → X-AI-Agent header → 'human'
      String effectiveSourceMode = (sourceModeRaw != null)
        ? sourceModeRaw
        : (aiHeader ? "ai" : "human");

      // ── persist ───────────────────────────────────────────────────────────
      SemanticAnnotation ann = new SemanticAnnotation();
      ann.setSubjectAppId(rowSubjectAppId);
      ann.setSubjectKind(subjectKind);
      ann.setPropertyIRI(propertyIRI);
      ann.setPropertyName(propertyName);
      ann.setValueName(valueName);
      ann.setValueIRI(valueIRI);
      ann.setNumericValue(numericValue);
      ann.setUnitIRI(unitIRI);
      ann.setVocabularyId(vocabularyId);
      ann.setSourceMode(effectiveSourceMode);
      ann.setConfidence(confidence);
      // TODO(SEMA-V6-007): set sourceActivityAppId once Activity capture is wired.

      SemanticAnnotation saved = annotationDAO.createOrUpdate(ann);

      result.put("ok", true);
      result.put("appId", saved.getAppId());
      result.put("error", null);

    } catch (McpException me) {
      result.put("ok", false);
      result.put("appId", null);
      result.put("error", me.getMessage());
      Log.debugf("semantic_annotate_bulk: row validation failure for subject=%s: %s",
        rowSubjectAppId, me.getMessage());
    } catch (RuntimeException re) {
      result.put("ok", false);
      result.put("appId", null);
      result.put("error", "Write failed: " + re.getMessage());
      Log.warnf("semantic_annotate_bulk: write error for subject=%s: %s",
        rowSubjectAppId, re.getMessage());
    }

    return result;
  }

  /** Return null when {@code v} is null or blank; otherwise trim and return. */
  private static String nullIfBlank(Object v) {
    if (v == null) return null;
    String s = v.toString().strip();
    return s.isEmpty() ? null : s;
  }

  /** Parse {@code v} to {@code Double}, returning null on failure. */
  private static Double toDouble(Object v) {
    if (v == null) return null;
    if (v instanceof Number n) return n.doubleValue();
    try {
      return Double.parseDouble(v.toString().strip());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  // ─── helpers ────────────────────────────────────────────────────────────────

  /**
   * Full annotation detail map — emitted by get_annotation, create_annotation,
   * update_annotation, and find_annotated.
   */
  private static Map<String, Object> toDetailMap(SemanticAnnotation a) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("appId", a.getAppId());
    row.put("propertyName", a.getPropertyName());
    row.put("propertyIRI", a.getPropertyIRI());
    row.put("valueName", a.getValueName());
    row.put("valueIRI", a.getValueIRI());
    row.put("numericValue", a.getNumericValue());
    row.put("unitIRI", a.getUnitIRI());
    row.put("subjectKind", a.getSubjectKind());
    row.put("subjectAppId", a.getSubjectAppId());
    row.put("vocabularyId", a.getVocabularyId());
    row.put("sourceMode", a.getSourceMode());
    row.put("confidence", a.getConfidence());
    row.put("sourceActivityAppId", a.getSourceActivityAppId());
    row.put("validFromMillis", a.getValidFromMillis());
    row.put("validUntilMillis", a.getValidUntilMillis());
    row.put("source", a.getSource());
    return row;
  }

  /**
   * Returns {@code true} if the current routing context carries the
   * {@code X-AI-Agent} header — used to auto-set {@code sourceMode="ai"} when
   * the caller is an AI agent and the tool invocation did not explicitly supply
   * a sourceMode override.
   */
  private boolean isAiAgentRequest() {
    try {
      CurrentVertxRequest cvr = currentVertxRequest.get();
      RoutingContext rc = cvr == null ? null : cvr.getCurrent();
      if (rc == null) return false;
      String h = rc.request().getHeader(McpToolSupport.HEADER_AI_AGENT);
      return h != null && !h.isBlank();
    } catch (RuntimeException e) {
      return false;
    }
  }
}
