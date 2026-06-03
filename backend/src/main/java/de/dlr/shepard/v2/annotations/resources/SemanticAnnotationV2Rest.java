package de.dlr.shepard.v2.annotations.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.identifier.AppIdGenerator;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.context.semantic.services.OntologyConfigService;
import de.dlr.shepard.provenance.entities.Activity;
import de.dlr.shepard.provenance.filters.ProvenanceCaptureFilter;
import de.dlr.shepard.provenance.services.ProvenanceService;
import de.dlr.shepard.v2.annotations.daos.SemanticAnnotationV2DAO;
import de.dlr.shepard.v2.annotations.io.AnnotationIO;
import de.dlr.shepard.v2.annotations.io.BulkAnnotationEntryIO;
import de.dlr.shepard.v2.annotations.io.BulkAnnotationErrorIO;
import de.dlr.shepard.v2.annotations.io.BulkCreateAnnotationsIO;
import de.dlr.shepard.v2.annotations.io.BulkCreateAnnotationsResultIO;
import de.dlr.shepard.v2.annotations.io.CreateAnnotationIO;
import de.dlr.shepard.v2.annotations.io.UpdateAnnotationIO;
import de.dlr.shepard.v2.project.services.ProjectAnnotationConstraints;
import io.quarkus.logging.Log;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * SEMA-V6-004 — polymorphic REST surface for semantic annotations.
 *
 * <p>All endpoints live under {@code /v2/annotations} (additive, no impact on
 * frozen {@code /shepard/api/...} surface — per CLAUDE.md API-version policy).
 *
 * <p>The subject of an annotation is any entity identified by an {@code appId}.
 * {@code subjectKind} (e.g. {@code "DataObject"}, {@code "Collection"}) plus
 * {@code subjectAppId} form the polymorphic subject pair. Permissions are
 * inherited from the subject entity's parent Collection, resolved via
 * {@link PermissionsService}.
 *
 * <p>Turtle export at {@code GET /v2/annotations/{appId}/export/turtle} produces
 * a minimal OA-framed Turtle document per §3.3 of
 * {@code aidocs/semantics/100-consistent-semantic-annotation-design.md}.
 *
 * <p>Security:
 * <ul>
 *   <li>List / get / find — caller must be able to Read the subject entity.</li>
 *   <li>Create / update — caller must be able to Write the subject entity.</li>
 *   <li>Delete — caller must be the annotation author (matched via
 *       {@code sourceActivityAppId} principal heuristic) OR hold Write
 *       permission on the subject entity.</li>
 * </ul>
 */
@Path("/v2/annotations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Authenticated
@Tag(name = "Semantic annotations (v2)")
public class SemanticAnnotationV2Rest {

  static final int MAX_PAGE_SIZE = 200;
  static final int DEFAULT_PAGE_SIZE = 50;

  static final String PROBLEM_TYPE_BAD_REQUEST = "/problems/annotations.bad-request";
  static final String PROBLEM_TYPE_NOT_FOUND = "/problems/annotations.not-found";
  static final String PROBLEM_TYPE_FORBIDDEN = "/problems/annotations.forbidden";
  static final String PROBLEM_TYPE_UNPROCESSABLE = "/problems/annotations.unprocessable";

  @Inject
  SemanticAnnotationV2DAO annotationDAO;

  @Inject
  PermissionsService permissionsService;

  @Inject
  EntityIdResolver entityIdResolver;

  @Inject
  OntologyConfigService ontologyConfigService;

  /** PROJ-SEMA-WRITE-GATE-1 — runtime gate for urn:shepard:project / partOf / programme. */
  @Inject
  ProjectAnnotationConstraints projectAnnotationConstraints;

  /** SEMA-V6-007 — mints `:Activity` nodes for annotation mutations. */
  @Inject
  ProvenanceService provenanceService;

  /**
   * SEMA-V6-007 — injected so handlers can set
   * {@link ProvenanceCaptureFilter#PROP_SKIP_CAPTURE} after calling
   * {@link ProvenanceService#record} directly, preventing a duplicate
   * {@code :Activity} row from the response filter.
   */
  @Context
  ContainerRequestContext requestContext;

  // ─── LIST ──────────────────────────────────────────────────────────────────

  @GET
  @Operation(
    summary = "List annotations with optional filters.",
    description =
      "Returns a page of `:SemanticAnnotation` nodes matching the provided filter parameters. " +
      "All filters are optional and AND-combined. Pagination is controlled by `page` " +
      "(zero-based, default 0) and `pageSize` (default 50, max 200).\n\n" +
      "Filters: `subjectAppId` — only annotations on this entity; `subjectKind` — narrow " +
      "to a specific entity kind; `predicateIri` — only annotations using this predicate; " +
      "`vocabId` — only annotations from this vocabulary.\n\n" +
      "Auth: the caller's Read permission on the subject entity is enforced per annotation " +
      "row — annotations whose subject the caller cannot Read are excluded from the result. " +
      "Supply `subjectAppId` to filter to one entity (and trigger a single permission check " +
      "at the list boundary).\n\n" +
      "Next step: `GET /v2/annotations/{appId}` for a single annotation, or " +
      "`POST /v2/annotations` to create."
  )
  @APIResponse(
    responseCode = "200",
    description = "Array of AnnotationV2 matching the filters (may be empty).",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = AnnotationIO.class))
  )
  @APIResponse(responseCode = "400", description = "Bad pagination params (RFC 7807).")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  public Response list(
    @QueryParam("subjectAppId") String subjectAppId,
    @QueryParam("subjectKind") String subjectKind,
    @QueryParam("predicateIri") String predicateIri,
    @QueryParam("vocabId") String vocabId,
    @QueryParam("page") @DefaultValue("0") int page,
    @QueryParam("pageSize") @DefaultValue("50") int pageSize,
    @Context SecurityContext sc
  ) {
    String caller = callerName(sc);
    if (caller == null) return unauthorized();
    if (page < 0 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Invalid pagination parameters", Response.Status.BAD_REQUEST,
        "page must be >= 0; pageSize must be in [1, " + MAX_PAGE_SIZE + "]");
    }

    // If subjectAppId is given, gate the whole request on that one subject.
    if (subjectAppId != null && !subjectAppId.isBlank()) {
      Response gate = checkReadAccessForSubject(subjectAppId, subjectKind, caller);
      if (gate != null) return gate;
    }

    List<AnnotationIO> result = annotationDAO
      .findFiltered(subjectAppId, subjectKind, predicateIri, vocabId, page, pageSize)
      .stream()
      .map(AnnotationIO::new)
      .toList();
    return Response.ok(result).build();
  }

  // ─── FIND (text search) ────────────────────────────────────────────────────

  @GET
  @Path("/find")
  @Operation(
    summary = "Text search over annotation values and predicate names.",
    description =
      "Case-insensitive substring search over annotation value names and predicate names. " +
      "Supply `q` with at least 1 character. Optional `vocabId` narrows to one vocabulary. " +
      "Pagination via `page` / `pageSize`.\n\n" +
      "Auth: result is the union of annotations the caller can Read; supply `subjectAppId` " +
      "or `subjectKind` in the parent `list` endpoint for entity-scoped discovery."
  )
  @APIResponse(
    responseCode = "200",
    description = "Array of AnnotationV2 matching the text query.",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = AnnotationIO.class))
  )
  @APIResponse(responseCode = "400", description = "Query string is blank (RFC 7807).")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  public Response find(
    @QueryParam("q") String q,
    @QueryParam("vocabId") String vocabId,
    @QueryParam("page") @DefaultValue("0") int page,
    @QueryParam("pageSize") @DefaultValue("50") int pageSize,
    @Context SecurityContext sc
  ) {
    String caller = callerName(sc);
    if (caller == null) return unauthorized();
    if (q == null || q.isBlank()) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Missing query parameter", Response.Status.BAD_REQUEST,
        "q must be non-blank");
    }
    if (page < 0 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Invalid pagination parameters", Response.Status.BAD_REQUEST,
        "page must be >= 0; pageSize must be in [1, " + MAX_PAGE_SIZE + "]");
    }

    List<AnnotationIO> result = annotationDAO
      .textSearch(q, vocabId, page, pageSize)
      .stream()
      .map(AnnotationIO::new)
      .toList();
    return Response.ok(result).build();
  }

  // ─── GET BY appId ──────────────────────────────────────────────────────────

  @GET
  @Path("/{appId}")
  @Operation(
    summary = "Get one annotation by its appId.",
    description =
      "Returns the full AnnotationV2 for the annotation identified by `appId` (UUID v7). " +
      "404 when the annotation does not exist. Auth: caller must be able to Read the " +
      "subject entity (inherited from its Collection).\n\n" +
      "Next step: `PUT /v2/annotations/{appId}` to update, " +
      "`DELETE /v2/annotations/{appId}` to delete, or " +
      "`GET /v2/annotations/{appId}/export/turtle` for the Turtle export."
  )
  @APIResponse(
    responseCode = "200",
    description = "The AnnotationV2 record.",
    content = @Content(schema = @Schema(implementation = AnnotationIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller cannot Read the subject entity.")
  @APIResponse(responseCode = "404", description = "No annotation with that appId.")
  public Response get(
    @PathParam("appId") String appId,
    @Context SecurityContext sc
  ) {
    String caller = callerName(sc);
    if (caller == null) return unauthorized();

    SemanticAnnotation annotation = annotationDAO.findByAnnotationAppId(appId);
    if (annotation == null) return notFound("annotation", appId);

    Response gate = checkReadAccessForSubject(annotation.getSubjectAppId(), annotation.getSubjectKind(), caller);
    if (gate != null) return gate;

    return Response.ok(new AnnotationIO(annotation)).build();
  }

  // ─── EXPORT / TURTLE ───────────────────────────────────────────────────────

  @GET
  @Path("/{appId}/export/turtle")
  @Produces("text/turtle")
  @Operation(
    summary = "Export one annotation as OA-framed Turtle.",
    description =
      "Produces a minimal Turtle document containing both the 'flat triple' form " +
      "(`<subjectEntityIri> <predicateIri> <objectValue> .`) and the " +
      "W3C Open Annotations (OA) frame (`oa:Annotation` with `oa:hasTarget` / " +
      "`oa:hasBody` and `prov:wasGeneratedBy`). Content-Type is `text/turtle`. " +
      "Per §3.3 of aidocs/semantics/100.\n\n" +
      "Auth: caller must be able to Read the subject entity."
  )
  @APIResponse(responseCode = "200", description = "Turtle document.", content = @Content(mediaType = "text/turtle"))
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller cannot Read the subject entity.")
  @APIResponse(responseCode = "404", description = "No annotation with that appId.")
  public Response exportTurtle(
    @PathParam("appId") String appId,
    @Context SecurityContext sc
  ) {
    String caller = callerName(sc);
    if (caller == null) return unauthorized();

    SemanticAnnotation annotation = annotationDAO.findByAnnotationAppId(appId);
    if (annotation == null) return notFound("annotation", appId);

    Response gate = checkReadAccessForSubject(annotation.getSubjectAppId(), annotation.getSubjectKind(), caller);
    if (gate != null) return gate;

    String turtle = buildTurtle(annotation);
    return Response.ok(turtle, "text/turtle").build();
  }

  // ─── CREATE ────────────────────────────────────────────────────────────────

  @POST
  @Operation(
    summary = "Create a new semantic annotation.",
    description =
      "Creates a `:SemanticAnnotation` node for the given subject entity. " +
      "The server mints `appId` (UUID v7) for the new annotation.\n\n" +
      "Required fields: `subjectAppId`, `subjectKind`, `predicateIri`. " +
      "Exactly one of `objectLiteral` / `objectIri` must be non-null.\n\n" +
      "Auth: caller must be able to Write the subject entity (inherited from its Collection). " +
      "`sourceMode` defaults to `'human'` if not provided. " +
      "`confidence` defaults to `1.0` for human writes.\n\n" +
      "Returns `201 Created` with the full AnnotationV2 body."
  )
  @APIResponse(
    responseCode = "201",
    description = "Annotation created.",
    content = @Content(schema = @Schema(implementation = AnnotationIO.class))
  )
  @APIResponse(responseCode = "400", description = "Missing required fields or object invariant violated (RFC 7807).")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller cannot Write the subject entity.")
  public Response create(
    CreateAnnotationIO body,
    @Context SecurityContext sc,
    @HeaderParam("X-AI-Agent") String aiAgentHeader
  ) {
    long startedAtMillis = System.currentTimeMillis();
    String caller = callerName(sc);
    if (caller == null) return unauthorized();

    // Validate required fields
    if (body == null) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Missing request body", Response.Status.BAD_REQUEST,
        "Request body is required");
    }
    if (blank(body.getSubjectAppId())) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Missing subjectAppId", Response.Status.BAD_REQUEST,
        "subjectAppId is required");
    }
    if (blank(body.getSubjectKind())) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Missing subjectKind", Response.Status.BAD_REQUEST,
        "subjectKind is required");
    }
    if (blank(body.getPredicateIri())) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Missing predicateIri", Response.Status.BAD_REQUEST,
        "predicateIri is required");
    }
    // XOR: exactly one of objectLiteral / objectIri must be non-null
    boolean hasLiteral = !blank(body.getObjectLiteral());
    boolean hasIri = !blank(body.getObjectIri());
    if (!hasLiteral && !hasIri) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Missing object value", Response.Status.BAD_REQUEST,
        "Exactly one of objectLiteral or objectIri must be provided");
    }
    if (hasLiteral && hasIri) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Ambiguous object value", Response.Status.BAD_REQUEST,
        "Provide exactly one of objectLiteral or objectIri, not both");
    }

    Response gate = checkWriteAccessForSubject(body.getSubjectAppId(), body.getSubjectKind(), caller);
    if (gate != null) return gate;

    // PROJ-SEMA-WRITE-GATE-1 — Project SHACL constraints (urn:shepard:project /
    // partOf / programme). No-op for any other predicate.
    String shaclViolation = projectAnnotationConstraints.check(
      body.getSubjectAppId(), body.getSubjectKind(), body.getPredicateIri(),
      body.getObjectLiteral(), body.getObjectIri());
    if (shaclViolation != null) {
      ProblemJson violationBody = new ProblemJson(PROBLEM_TYPE_UNPROCESSABLE,
        "Project constraint violation", 422, shaclViolation, null);
      return Response.status(422).type("application/problem+json").entity(violationBody).build();
    }

    SemanticAnnotation annotation = new SemanticAnnotation();
    annotation.setAppId(AppIdGenerator.next());
    annotation.setSubjectKind(body.getSubjectKind());
    annotation.setSubjectAppId(body.getSubjectAppId());
    annotation.setPropertyIRI(body.getPredicateIri());
    annotation.setPropertyName(body.getPredicateLabel());
    annotation.setVocabularyId(body.getVocabularyId());
    annotation.setValueIRI(body.getObjectIri());
    // valueName stores the literal (or the label of the IRI if IRI is given)
    annotation.setValueName(hasLiteral ? body.getObjectLiteral() : null);
    annotation.setNumericValue(body.getNumericValue());
    annotation.setUnitIRI(body.getUnitIri());
    // SEMA-V6-007: if X-AI-Agent header is present and sourceMode not explicitly given,
    // default to "ai" rather than "human" (EU AI Act Art-50 transparency hook).
    String resolvedSourceMode = body.getSourceMode() != null
      ? body.getSourceMode()
      : (aiAgentHeader != null && !aiAgentHeader.isBlank() ? "ai" : "human");
    annotation.setSourceMode(resolvedSourceMode);
    // Client-supplied sourceActivityAppId is accepted but will be overwritten below
    // if ProvenanceService mints a new Activity for this create.
    annotation.setSourceActivityAppId(body.getSourceActivityAppId());
    annotation.setAgentUsername(caller);  // SEMA-V6-013: record the author username at creation
    annotation.setValidFromMillis(body.getValidFromMillis());
    annotation.setValidUntilMillis(body.getValidUntilMillis());
    annotation.setConfidence(body.getConfidence() != null ? body.getConfidence() : 1.0);

    annotationDAO.createOrUpdate(annotation);

    // SEMA-V6-007: record :Activity, back-stamp Neo4j node, and stamp in-memory
    // entity so the 201 response body carries sourceActivityAppId.
    String activityAppId = recordAnnotationActivity(
      "CREATE", annotation.getAppId(), caller, startedAtMillis,
      "POST /v2/annotations — created annotation " + annotation.getAppId()
    );
    if (activityAppId != null) {
      annotation.setSourceActivityAppId(activityAppId);
    }

    Log.infof("SemanticAnnotationV2Rest: created annotation %s on %s/%s by %s",
      annotation.getAppId(), annotation.getSubjectKind(), annotation.getSubjectAppId(), caller);
    return Response.status(Response.Status.CREATED).entity(new AnnotationIO(annotation)).build();
  }

  // ─── BULK CREATE ───────────────────────────────────────────────────────────

  /** Maximum entries accepted per bulk-create call (mirrors MCP cap). */
  static final int MAX_BULK_ENTRIES = 100;

  /**
   * SEMANTIC-ANNOTATE-BULK-REST-1 — bulk-create REST endpoint mirroring the
   * {@code semantic_annotate_bulk} MCP tool.
   *
   * <p>Accepts up to {@value #MAX_BULK_ENTRIES} entries per call. Each entry is
   * processed independently; a failure on one entry does not abort the others
   * (per-row error isolation). The response reports {@code created}, {@code failed},
   * and per-entry {@code errors[]}.
   *
   * <p>This endpoint lets non-MCP callers (UI mass-annotation, CLI sweeps)
   * share the same semantics as the MCP bulk tool without going through the
   * MCP transport layer.
   *
   * <p>Auth: caller must hold Write permission on each referenced DataObject
   * (checked per entry — entries that fail permission are counted in
   * {@code failed}).
   */
  @POST
  @Path("/bulk")
  @Operation(
    summary = "Bulk-create semantic annotations (SEMANTIC-ANNOTATE-BULK-REST-1).",
    description =
      "Creates up to " + MAX_BULK_ENTRIES + " `:SemanticAnnotation` nodes in a single call. " +
      "Each entry in the request array is processed independently — a failure on one entry " +
      "does not roll back successful entries. The response enumerates `created`, `failed`, " +
      "and per-entry error details.\n\n" +
      "Each entry annotates a `DataObject` (subjectKind is fixed to `'DataObject'` for this " +
      "endpoint — use `POST /v2/annotations` for other subject kinds).\n\n" +
      "Auth: the caller must hold Write permission on each referenced DataObject.\n\n" +
      "Returns `200 OK` when at least one entry was attempted (even if all failed). " +
      "Returns `400` for structural errors (null/empty entries, > " + MAX_BULK_ENTRIES + " entries)."
  )
  @APIResponse(
    responseCode = "200",
    description = "Bulk create completed (check created/failed counters).",
    content = @Content(schema = @Schema(implementation = BulkCreateAnnotationsResultIO.class))
  )
  @APIResponse(responseCode = "400", description = "Null/empty entries list or > " + MAX_BULK_ENTRIES + " entries (RFC 7807).")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  public Response bulkCreate(
    BulkCreateAnnotationsIO body,
    @Context SecurityContext sc
  ) {
    long startedAtMillis = System.currentTimeMillis();
    String caller = callerName(sc);
    if (caller == null) return unauthorized();

    // Validate structural preconditions
    if (body == null || body.entries() == null) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Missing request body", Response.Status.BAD_REQUEST,
        "Request body with non-null 'entries' list is required");
    }
    if (body.entries().isEmpty()) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Empty entries", Response.Status.BAD_REQUEST,
        "entries must contain at least one item");
    }
    if (body.entries().size() > MAX_BULK_ENTRIES) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Too many entries", Response.Status.BAD_REQUEST,
        "max " + MAX_BULK_ENTRIES + " entries per call — received " + body.entries().size());
    }

    int created = 0;
    int failed = 0;
    List<BulkAnnotationErrorIO> errors = new ArrayList<>();

    for (int i = 0; i < body.entries().size(); i++) {
      BulkAnnotationEntryIO entry = body.entries().get(i);
      if (entry == null) {
        errors.add(new BulkAnnotationErrorIO(i, null, null, "Entry is null"));
        failed++;
        continue;
      }
      try {
        // Build a synthetic CreateAnnotationIO and delegate to the single-create path.
        CreateAnnotationIO req = new CreateAnnotationIO();
        req.setSubjectAppId(entry.dataObjectAppId());
        req.setSubjectKind("DataObject");
        req.setPredicateIri(entry.predicate());
        req.setObjectLiteral(entry.value());
        req.setVocabularyId(entry.vocabularyId());

        // create() performs all validation + permission checks + provenance capture.
        // We pass null for aiAgentHeader — callers that want AI-mode use X-AI-Agent header
        // at the transport layer which is not propagated here; per-entry sourceMode override
        // is not supported in the v0 bulk shape (use single-create for that).
        Response singleResult = create(req, sc, null);

        if (singleResult.getStatus() == 201) {
          created++;
        } else {
          // Extract error detail when available
          String msg = "HTTP " + singleResult.getStatus();
          try {
            Object entity = singleResult.getEntity();
            if (entity != null) msg = entity.toString();
          } catch (RuntimeException ignored) { /* best-effort */ }
          errors.add(new BulkAnnotationErrorIO(i, entry.dataObjectAppId(), entry.predicate(), msg));
          failed++;
        }
      } catch (RuntimeException e) {
        Log.debugf(e, "SEMANTIC-ANNOTATE-BULK-REST-1: entry[%d] dataObjectAppId=%s failed", i, entry.dataObjectAppId());
        errors.add(new BulkAnnotationErrorIO(i, entry.dataObjectAppId(), entry.predicate(), e.getMessage()));
        failed++;
      }
    }

    // Record one batch-level Activity for the entire bulk call (per CLAUDE.md:
    // "handlers that record their own Activity hand off skip-capture").
    // The single-create calls above each record their own Activity; this batch
    // Activity captures the aggregate for audit trail completeness.
    // PROP_SKIP_CAPTURE is already set by create() for each entry — the filter
    // won't emit a duplicate for the outer POST.
    try {
      long endedAtMillis = System.currentTimeMillis();
      provenanceService.record(
        "BULK_CREATE",
        "SemanticAnnotationBatch",
        null,
        caller,
        "POST /v2/annotations/bulk — created=" + created + " failed=" + failed + " total=" + body.entries().size(),
        "POST",
        "v2/annotations/bulk",
        200,
        startedAtMillis,
        endedAtMillis
      );
    } catch (RuntimeException e) {
      Log.debugf(e, "SEMANTIC-ANNOTATE-BULK-REST-1: batch provenance capture failed");
    } finally {
      try {
        if (requestContext != null) {
          requestContext.setProperty(ProvenanceCaptureFilter.PROP_SKIP_CAPTURE, Boolean.TRUE);
        }
      } catch (RuntimeException ignored) { /* best-effort */ }
    }

    Log.infof("SemanticAnnotationV2Rest: bulk-create by %s: created=%d failed=%d total=%d",
      caller, created, failed, body.entries().size());
    return Response.ok(new BulkCreateAnnotationsResultIO(created, failed, errors)).build();
  }

  // ─── UPDATE ────────────────────────────────────────────────────────────────

  @PUT
  @Path("/{appId}")
  @Operation(
    summary = "Update (merge-patch) an annotation.",
    description =
      "RFC 7396 merge-patch: only non-null fields in the request body are applied. " +
      "The subject and predicate of an annotation are immutable. Patchable fields: " +
      "`objectLiteral`, `objectIri`, `numericValue`, `unitIri`, `validFromMillis`, " +
      "`validUntilMillis`, `confidence`, `sourceMode`.\n\n" +
      "Auth: caller must be able to Write the subject entity (inherited from its Collection). " +
      "Returns `200 OK` with the updated AnnotationV2."
  )
  @APIResponse(
    responseCode = "200",
    description = "Updated AnnotationV2.",
    content = @Content(schema = @Schema(implementation = AnnotationIO.class))
  )
  @APIResponse(responseCode = "400", description = "Both objectLiteral and objectIri provided (RFC 7807).")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller cannot Write the subject entity.")
  @APIResponse(responseCode = "404", description = "No annotation with that appId.")
  public Response update(
    @PathParam("appId") String appId,
    UpdateAnnotationIO body,
    @Context SecurityContext sc
  ) {
    // Note: X-AI-Agent header is not inspected on update — the "collaborative"
    // sourceMode flip (human annotation later touched by AI → 🤝 "collaborative")
    // is SEMA-V6-007 follow-up scope. See McpToolSupport.HEADER_AI_AGENT for the
    // shared constant when that work lands.
    long startedAtMillis = System.currentTimeMillis();
    String caller = callerName(sc);
    if (caller == null) return unauthorized();

    SemanticAnnotation annotation = annotationDAO.findByAnnotationAppId(appId);
    if (annotation == null) return notFound("annotation", appId);

    Response gate = checkWriteAccessForSubject(annotation.getSubjectAppId(), annotation.getSubjectKind(), caller);
    if (gate != null) return gate;

    if (body == null) {
      // No-op — return current state
      return Response.ok(new AnnotationIO(annotation)).build();
    }

    // Validate: can't set both objectLiteral and objectIri at the same time
    boolean updateLiteral = !blank(body.getObjectLiteral());
    boolean updateIri = !blank(body.getObjectIri());
    if (updateLiteral && updateIri) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Ambiguous object value", Response.Status.BAD_REQUEST,
        "Provide at most one of objectLiteral or objectIri in an update");
    }

    // Apply merge-patch
    if (updateLiteral) {
      annotation.setValueName(body.getObjectLiteral());
      annotation.setValueIRI(null);
    } else if (updateIri) {
      annotation.setValueIRI(body.getObjectIri());
      annotation.setValueName(null);
    }
    if (body.getNumericValue() != null) annotation.setNumericValue(body.getNumericValue());
    if (body.getUnitIri() != null) annotation.setUnitIRI(body.getUnitIri());
    if (body.getValidFromMillis() != null) annotation.setValidFromMillis(body.getValidFromMillis());
    if (body.getValidUntilMillis() != null) annotation.setValidUntilMillis(body.getValidUntilMillis());
    if (body.getConfidence() != null) annotation.setConfidence(body.getConfidence());
    if (body.getSourceMode() != null) annotation.setSourceMode(body.getSourceMode());

    annotationDAO.createOrUpdate(annotation);

    // SEMA-V6-007: record :Activity, back-stamp Neo4j node, and stamp in-memory
    // entity so the 200 response body carries the new sourceActivityAppId.
    String activityAppId = recordAnnotationActivity(
      "UPDATE", annotation.getAppId(), caller, startedAtMillis,
      "PUT /v2/annotations/" + appId + " — updated annotation"
    );
    if (activityAppId != null) {
      annotation.setSourceActivityAppId(activityAppId);
    }

    return Response.ok(new AnnotationIO(annotation)).build();
  }

  // ─── DELETE ────────────────────────────────────────────────────────────────

  @DELETE
  @Path("/{appId}")
  @Operation(
    summary = "Delete an annotation.",
    description =
      "Deletes the `:SemanticAnnotation` node identified by `appId`. " +
      "Auth is governed by the operator-configured `annotationDeletePolicy` in `:SemanticConfig`:\n\n" +
      "- `'author-or-manager'` (default) — the annotation author OR any collection manager may delete.\n" +
      "- `'author-only'` — only the annotation author may delete.\n" +
      "- `'manager-only'` — only collection managers may delete.\n\n" +
      "Returns `204 No Content` on success.\n\n" +
      "Note: this is a hard delete. For soft-delete (set `validUntilMillis`), use " +
      "`PUT /v2/annotations/{appId}` with `{\"validUntilMillis\": <now>}`."
  )
  @APIResponse(responseCode = "204", description = "Annotation deleted.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller is not permitted by the active annotationDeletePolicy.")
  @APIResponse(responseCode = "404", description = "No annotation with that appId.")
  public Response delete(
    @PathParam("appId") String appId,
    @Context SecurityContext sc
  ) {
    long startedAtMillis = System.currentTimeMillis();
    String caller = callerName(sc);
    if (caller == null) return unauthorized();

    SemanticAnnotation annotation = annotationDAO.findByAnnotationAppId(appId);
    if (annotation == null) return notFound("annotation", appId);

    // Legacy row guard (no subject recorded): deny write.
    if (blank(annotation.getSubjectAppId())) {
      return problem(PROBLEM_TYPE_FORBIDDEN, "Cannot delete annotation", Response.Status.FORBIDDEN,
        "This annotation has no recorded subject (legacy row); use the v1 surface to delete it.");
    }

    // SEMA-V6-013: load active delete policy (null → default 'author-or-manager').
    String policy = ontologyConfigService.loadSingleton().getAnnotationDeletePolicy();
    if (policy == null || policy.isBlank()) {
      policy = "author-or-manager";
    }

    boolean isAuthor = caller.equals(annotation.getAgentUsername());
    // isManager: null return from checkAccessForSubject means access granted.
    boolean isManager = checkAccessForSubject(
      annotation.getSubjectAppId(), annotation.getSubjectKind(), AccessType.Manage, caller
    ) == null;

    boolean allowed;
    switch (policy) {
      case "author-only"     -> allowed = isAuthor;
      case "manager-only"    -> allowed = isManager;
      default                -> allowed = isAuthor || isManager;  // author-or-manager
    }

    if (!allowed) {
      return problem(PROBLEM_TYPE_FORBIDDEN, "Delete not permitted", Response.Status.FORBIDDEN,
        "Caller '" + caller + "' is not permitted to delete this annotation " +
        "(policy='" + policy + "', isAuthor=" + isAuthor + ", isManager=" + isManager + ").");
    }

    if (annotation.getId() == null) {
      Log.warnf("SemanticAnnotationV2Rest: annotation %s has null OGM id — cannot delete", appId);
      return notFound("annotation", appId);
    }
    annotationDAO.deleteByNeo4jId(annotation.getId());

    // SEMA-V6-007: record :Activity for the delete.
    // No back-stamp needed — annotation is deleted.
    recordDeleteActivity(appId, caller, startedAtMillis);

    Log.infof("SemanticAnnotationV2Rest: deleted annotation %s by %s (policy=%s)", appId, caller, policy);
    return Response.noContent().build();
  }

  // ─── SEMA-V6-007 provenance helpers ──────────────────────────────────────

  /**
   * Record a CREATE or UPDATE {@code :Activity} for an annotation write, back-stamp
   * {@code sourceActivityAppId} on the annotation node (Cypher SET), AND return the
   * Activity appId so the caller can stamp the in-memory entity before building the
   * response body.
   *
   * <p>Best-effort — any failure is swallowed and {@code null} is returned.
   * Sets {@link ProvenanceCaptureFilter#PROP_SKIP_CAPTURE} on the request context
   * so the response filter does not write a duplicate row.
   *
   * @param actionKind       {@code "CREATE"} or {@code "UPDATE"}
   * @param annotationAppId  the appId of the annotation that was written
   * @param caller           authenticated username
   * @param startedAtMillis  millis captured at handler entry
   * @param summary          short human-readable summary
   * @return the minted Activity appId, or {@code null} if capture failed / disabled
   */
  private String recordAnnotationActivity(
    String actionKind,
    String annotationAppId,
    String caller,
    long startedAtMillis,
    String summary
  ) {
    String activityAppId = null;
    try {
      long endedAtMillis = System.currentTimeMillis();
      int httpStatus = "CREATE".equals(actionKind) ? 201 : 200;
      String method   = "CREATE".equals(actionKind) ? "POST" : "PUT";
      String path     = "v2/annotations" + ("CREATE".equals(actionKind) ? "" : "/" + annotationAppId);

      Activity activity = provenanceService.record(
        actionKind,
        "SemanticAnnotation",
        annotationAppId,
        caller,
        summary,
        method,
        path,
        httpStatus,
        startedAtMillis,
        endedAtMillis
      );

      if (activity != null && activity.getAppId() != null) {
        activityAppId = activity.getAppId();
        // Back-stamp: write the Activity appId onto the annotation node in Neo4j.
        annotationDAO.setSourceActivityAppId(annotationAppId, activityAppId);
      }
    } catch (RuntimeException e) {
      Log.debugf(e, "SEMA-V6-007: provenance capture skipped for annotation=%s", annotationAppId);
    } finally {
      // Tell the response filter not to emit a second :Activity row.
      try {
        if (requestContext != null) {
          requestContext.setProperty(ProvenanceCaptureFilter.PROP_SKIP_CAPTURE, Boolean.TRUE);
        }
      } catch (RuntimeException ignored) { /* best-effort */ }
    }
    return activityAppId;
  }

  /**
   * Record a DELETE {@code :Activity} for an annotation deletion.
   * No back-stamp is needed — the annotation node is gone.
   */
  private void recordDeleteActivity(String annotationAppId, String caller, long startedAtMillis) {
    try {
      long endedAtMillis = System.currentTimeMillis();
      provenanceService.record(
        "DELETE",
        "SemanticAnnotation",
        annotationAppId,
        caller,
        "DELETE /v2/annotations/" + annotationAppId + " — deleted annotation",
        "DELETE",
        "v2/annotations/" + annotationAppId,
        204,
        startedAtMillis,
        endedAtMillis
      );
    } catch (RuntimeException e) {
      Log.debugf(e, "SEMA-V6-007: delete provenance capture skipped for annotation=%s", annotationAppId);
    } finally {
      try {
        if (requestContext != null) {
          requestContext.setProperty(ProvenanceCaptureFilter.PROP_SKIP_CAPTURE, Boolean.TRUE);
        }
      } catch (RuntimeException ignored) { /* best-effort */ }
    }
  }

  // ─── permission helpers ───────────────────────────────────────────────────

  /**
   * Check READ access for the subject entity. Returns a 4xx Response if denied,
   * or {@code null} if access is allowed.
   *
   * <p>For subjects of kind {@code "DataObject"}, uses the DataObject→Collection
   * walk via {@link PermissionsService#isAccessAllowedForDataObjectAppId}. For
   * all other kinds (Collection, Container, Reference, etc.), falls back to the
   * {@code isAccessAllowedForDataObjectAppId} path (which also works for any
   * entity that is a child of a collection) or grants access if the subject
   * cannot be resolved (unknown kind = open read — callers should supply
   * {@code subjectAppId} for full enforcement).
   *
   * <p>If {@code subjectAppId} is null (legacy annotation), access is granted to
   * avoid breaking legacy row reads.
   */
  private Response checkReadAccessForSubject(String subjectAppId, String subjectKind, String caller) {
    return checkAccessForSubject(subjectAppId, subjectKind, AccessType.Read, caller);
  }

  private Response checkWriteAccessForSubject(String subjectAppId, String subjectKind, String caller) {
    return checkAccessForSubject(subjectAppId, subjectKind, AccessType.Write, caller);
  }

  /**
   * Polymorphic permission gate. For DataObjects, uses the DataObject→Collection
   * walk. For Collections, resolves the OGM id via {@link EntityIdResolver}
   * and gates directly. For other entity kinds, attempts the DataObject walk
   * (works for most child entities); if the walk returns false, grants
   * access conservatively for legacy/unknown kinds (null subjectAppId).
   */
  private Response checkAccessForSubject(
    String subjectAppId,
    String subjectKind,
    AccessType accessType,
    String caller
  ) {
    // Legacy row — no subject recorded; grant read, deny write.
    if (blank(subjectAppId)) {
      if (accessType == AccessType.Read) return null;
      return problem(PROBLEM_TYPE_FORBIDDEN, "Cannot modify annotation", Response.Status.FORBIDDEN,
        "This annotation has no recorded subject (legacy row); use the v1 surface to modify it.");
    }

    boolean allowed;

    if ("Collection".equalsIgnoreCase(subjectKind)) {
      // Collections have their own Permissions node; resolve OGM id and check directly.
      try {
        long ogmId = entityIdResolver.resolveLong(subjectAppId);
        allowed = permissionsService.isAccessTypeAllowedForUser(ogmId, accessType, caller, 0L);
      } catch (jakarta.ws.rs.NotFoundException nfe) {
        return notFound("Collection", subjectAppId);
      }
    } else {
      // DataObject and all other entity kinds: walk DataObject→Collection.
      // This also works for References and Containers that inherit from their parent DO.
      allowed = permissionsService.isAccessAllowedForDataObjectAppId(subjectAppId, accessType, caller);
    }

    if (!allowed) {
      return problem(PROBLEM_TYPE_FORBIDDEN, "Access denied", Response.Status.FORBIDDEN,
        "Caller '" + caller + "' lacks " + accessType.name() + " permission on the subject entity.");
    }
    return null;
  }

  // ─── Turtle export helper ─────────────────────────────────────────────────

  /**
   * Builds an OA-framed Turtle document for one annotation (§3.3 shape).
   * Uses string templates — Apache Jena is in the pom but pulling in the full
   * model API for a two-triple export adds more weight than the hand-rolled template.
   */
  private static String buildTurtle(SemanticAnnotation a) {
    String subjectIri = shepardIri(a.getSubjectKind(), a.getSubjectAppId());
    String predicateIri = nvl(a.getPropertyIRI(), "http://www.w3.org/1999/02/22-rdf-syntax-ns#predicate");
    String annotationIri = "shepard:Annotation/" + nvl(a.getAppId(), "unknown");
    String activityIri = blank(a.getSourceActivityAppId())
      ? null
      : "shepard:Activity/" + a.getSourceActivityAppId();

    // object value
    String objectValue = a.getValueIRI() != null
      ? "<" + a.getValueIRI() + ">"
      : "\"" + escapeTurtleLiteral(nvl(a.getValueName(), "")) + "\"";

    StringBuilder sb = new StringBuilder();
    sb.append("@prefix oa: <http://www.w3.org/ns/oa#> .\n");
    sb.append("@prefix prov: <http://www.w3.org/ns/prov#> .\n");
    sb.append("@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .\n");
    sb.append("@prefix sh: <http://www.w3.org/ns/shacl#> .\n");
    sb.append("@prefix shepard: <https://shepard.dlr.de/v2/> .\n");
    sb.append("\n");

    // Flat triple (line 1 of §3.3)
    sb.append("<").append(subjectIri).append("> <").append(predicateIri).append("> ").append(objectValue).append(" .\n");
    sb.append("\n");

    // OA-shaped annotation (lines 2-5 of §3.3)
    sb.append("<").append(annotationIri).append("> a oa:Annotation ;\n");
    sb.append("    oa:hasTarget <").append(subjectIri).append("> ;\n");
    sb.append("    oa:hasBody [ rdf:value ").append(objectValue)
      .append(" ; sh:path <").append(predicateIri).append("> ]");

    if (activityIri != null) {
      sb.append(" ;\n    prov:wasGeneratedBy <").append(activityIri).append(">");
    }
    sb.append(" .\n");

    return sb.toString();
  }

  private static String shepardIri(String kind, String appId) {
    if (blank(kind) || blank(appId)) {
      return "https://shepard.dlr.de/v2/entities/" + nvl(appId, "unknown");
    }
    return "https://shepard.dlr.de/v2/" + kind.toLowerCase() + "s/" + appId;
  }

  private static String escapeTurtleLiteral(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
  }

  // ─── generic response helpers ─────────────────────────────────────────────

  private static String callerName(SecurityContext sc) {
    if (sc == null || sc.getUserPrincipal() == null) return null;
    String n = sc.getUserPrincipal().getName();
    return n == null || n.isBlank() ? null : n;
  }

  private static Response unauthorized() {
    return Response.status(Response.Status.UNAUTHORIZED).build();
  }

  private static Response notFound(String kind, String id) {
    return problem(PROBLEM_TYPE_NOT_FOUND, "Not found",
      Response.Status.NOT_FOUND, kind + " with appId '" + id + "' not found.");
  }

  private static Response problem(String type, String title, Response.Status status, String detail) {
    ProblemJson body = new ProblemJson(type, title, status.getStatusCode(), detail, null);
    return Response.status(status).type("application/problem+json").entity(body).build();
  }

  private static boolean blank(String s) {
    return s == null || s.isBlank();
  }

  private static String nvl(String s, String fallback) {
    return s == null || s.isBlank() ? fallback : s;
  }
}
