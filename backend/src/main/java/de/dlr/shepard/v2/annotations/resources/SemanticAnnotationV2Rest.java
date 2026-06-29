package de.dlr.shepard.v2.annotations.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.identifier.AppIdGenerator;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.context.semantic.services.OntologyConfigService;
import de.dlr.shepard.provenance.entities.Activity;
import de.dlr.shepard.provenance.filters.ProvenanceCaptureFilter;
import de.dlr.shepard.provenance.services.ProvenanceService;
import de.dlr.shepard.v2.annotations.daos.SemanticAnnotationV2DAO;
import de.dlr.shepard.v2.annotations.io.AnnotationIO;
import de.dlr.shepard.v2.annotations.io.BulkAnnotationResultIO;
import de.dlr.shepard.v2.annotations.io.BulkAnnotationResultItemIO;
import de.dlr.shepard.v2.annotations.io.BulkCreateAnnotationIO;
import de.dlr.shepard.v2.annotations.io.CreateAnnotationIO;
import de.dlr.shepard.v2.annotations.io.UpdateAnnotationIO;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import de.dlr.shepard.v2.project.services.ProjectAnnotationConstraints;
import de.dlr.shepard.v2.references.services.ReferencesV2Service;
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
import java.util.Optional;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
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
@Tag(name = "Semantic annotations")
public class SemanticAnnotationV2Rest {

  static final int MAX_PAGE_SIZE = 200;
  static final int DEFAULT_PAGE_SIZE = 50;

  static final String PROBLEM_TYPE_UNAUTHORIZED = "/problems/annotations.unauthorized";
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

  /** F9 — resolves Reference appIds to their parent DataObject for permission gating. */
  @Inject
  ReferencesV2Service referencesService;

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
    operationId = "listAnnotations",
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
    description = "PagedResponse of AnnotationV2 matching the filters.",
    content = @Content(schema = @Schema(implementation = PagedResponseIO.class))
  )
  @APIResponse(responseCode = "400", description = "Bad pagination params (RFC 7807).")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  public Response list(
    @Parameter(description =
      "Filter by subject entity appId (UUID v7). Only annotations on this entity are returned. "
      + "Supply to trigger a single entity-level permission check at the list boundary rather than "
      + "per-row checks across the full result set.")
    @QueryParam("subjectAppId") String subjectAppId,
    @Parameter(description =
      "Narrow to a specific subject entity kind (e.g. 'DataObject', 'Collection', 'Container'). "
      + "Combined with subjectAppId to disambiguate subjects across entity types.")
    @QueryParam("subjectKind") String subjectKind,
    @Parameter(description =
      "Filter by predicate IRI. Only annotations using exactly this predicate IRI are returned "
      + "(e.g. 'urn:shepard:project:partOf', 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type').")
    @QueryParam("predicateIri") String predicateIri,
    @Parameter(description =
      "Filter by vocabulary identifier. Only annotations whose predicate belongs to this "
      + "vocabulary are returned.")
    @QueryParam("vocabId") String vocabId,
    @Parameter(description = "Zero-based page index (default 0).")
    @QueryParam("page") @DefaultValue("0") int page,
    @Parameter(description = "Page size — number of annotations per page (default 50, range 1–200).")
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

    List<AnnotationIO> items = annotationDAO
      .findFiltered(subjectAppId, subjectKind, predicateIri, vocabId, page, pageSize)
      .stream()
      .map(AnnotationIO::new)
      .toList();
    long total = annotationDAO.countFiltered(subjectAppId, subjectKind, predicateIri, vocabId);
    return Response.ok(new PagedResponseIO<>(items, total, page, pageSize)).build();
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
    description = "PagedResponse of AnnotationV2 matching the text query.",
    content = @Content(schema = @Schema(implementation = PagedResponseIO.class))
  )
  @APIResponse(responseCode = "400", description = "Query string is blank (RFC 7807).")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  public Response find(
    @Parameter(required = true, description =
      "Case-insensitive substring to search for. Must be non-blank. Matched against "
      + "annotation value names and predicate names.")
    @QueryParam("q") String q,
    @Parameter(description =
      "Narrow results to annotations whose predicate belongs to this vocabulary identifier. "
      + "Optional — omit to search across all vocabularies.")
    @QueryParam("vocabId") String vocabId,
    @Parameter(description = "Zero-based page index (default 0).")
    @QueryParam("page") @DefaultValue("0") int page,
    @Parameter(description = "Page size — number of annotations per page (default 50, range 1–200).")
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

    List<AnnotationIO> items = annotationDAO
      .textSearch(q, vocabId, page, pageSize)
      .stream()
      .map(AnnotationIO::new)
      .toList();
    long total = annotationDAO.countTextSearch(q, vocabId);
    return Response.ok(new PagedResponseIO<>(items, total, page, pageSize)).build();
  }

  // ─── GET BY appId ──────────────────────────────────────────────────────────

  @GET
  @Path("/{appId}")
  @Operation(
    operationId = "getAnnotation",
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
    operationId = "createAnnotation",
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

    // PROJ-SEMA-DUAL-OWNERSHIP-1 — for partOf writes, require Write on the
    // parent Project too (subject-Write was already checked above).
    String parentDeny = projectAnnotationConstraints.checkParentWritePermission(
      body.getPredicateIri(), body.getObjectLiteral(), caller,
      sc != null && sc.isUserInRole(de.dlr.shepard.common.util.Constants.INSTANCE_ADMIN_ROLE));
    if (parentDeny != null) {
      return problem(PROBLEM_TYPE_FORBIDDEN, "Parent Write required",
        Response.Status.FORBIDDEN, parentDeny);
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

  @POST
  @Path("/bulk")
  @Operation(
    operationId = "bulkCreateAnnotations",
    summary = "Create up to 100 semantic annotations in one request.",
    description =
      "SEMANTIC-ANNOTATE-BULK-REST-1 — batch create endpoint mirroring the MCP " +
      "`semantic_annotate_bulk` tool. Accepts up to " + BulkCreateAnnotationIO.MAX_SIZE +
      " annotation specs (same shape as POST /v2/annotations). Each spec is processed " +
      "independently; a failure in one does not abort the rest.\n\n" +
      "Returns HTTP 207 Multi-Status with per-spec outcomes in the `results` array. " +
      "The response carries `created` (success count), `failed` (error count), and " +
      "per-row `status` plus either `appId`/`subjectAppId` (success) or " +
      "`errorCode`/`errorMessage` (failure).\n\n" +
      "Provenance: the ProvenanceCaptureFilter records one :Activity for the whole " +
      "bulk request (not one per annotation), consistent with the DataObject batch pattern."
  )
  @APIResponse(
    responseCode = "207",
    description = "Multi-Status: per-spec outcome for each annotation in the request list.",
    content = @Content(schema = @Schema(implementation = BulkAnnotationResultIO.class))
  )
  @APIResponse(responseCode = "400", description = "Body missing, empty list, or list exceeds 100 specs (RFC 7807).")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  public Response bulkCreate(
    BulkCreateAnnotationIO body,
    @Context SecurityContext sc,
    @HeaderParam("X-AI-Agent") String aiAgentHeader
  ) {
    String caller = callerName(sc);
    if (caller == null) return unauthorized();

    if (body == null || body.getAnnotations() == null || body.getAnnotations().isEmpty()) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Missing annotations", Response.Status.BAD_REQUEST,
        "Request body must contain at least one annotation spec in the 'annotations' list");
    }
    if (body.getAnnotations().size() > BulkCreateAnnotationIO.MAX_SIZE) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Too many annotations", Response.Status.BAD_REQUEST,
        "Bulk create accepts at most " + BulkCreateAnnotationIO.MAX_SIZE +
        " specs per request; received " + body.getAnnotations().size());
    }

    boolean aiHeader = aiAgentHeader != null && !aiAgentHeader.isBlank();
    boolean isAdmin = sc != null && sc.isUserInRole(de.dlr.shepard.common.util.Constants.INSTANCE_ADMIN_ROLE);

    List<BulkAnnotationResultItemIO> results = new ArrayList<>(body.getAnnotations().size());
    int created = 0;
    int failed = 0;

    List<CreateAnnotationIO> specs = body.getAnnotations();
    for (int i = 0; i < specs.size(); i++) {
      CreateAnnotationIO spec = specs.get(i);
      try {
        // Validate required fields
        if (blank(spec.getSubjectAppId())) {
          results.add(BulkAnnotationResultItemIO.error(i, "MISSING_FIELD", "subjectAppId is required"));
          failed++;
          continue;
        }
        if (blank(spec.getSubjectKind())) {
          results.add(BulkAnnotationResultItemIO.error(i, "MISSING_FIELD", "subjectKind is required"));
          failed++;
          continue;
        }
        if (blank(spec.getPredicateIri())) {
          results.add(BulkAnnotationResultItemIO.error(i, "MISSING_FIELD", "predicateIri is required"));
          failed++;
          continue;
        }
        boolean hasLiteral = !blank(spec.getObjectLiteral());
        boolean hasIri = !blank(spec.getObjectIri());
        if (!hasLiteral && !hasIri) {
          results.add(BulkAnnotationResultItemIO.error(i, "MISSING_FIELD",
            "Exactly one of objectLiteral or objectIri must be provided"));
          failed++;
          continue;
        }
        if (hasLiteral && hasIri) {
          results.add(BulkAnnotationResultItemIO.error(i, "INVALID_INPUT",
            "Provide exactly one of objectLiteral or objectIri, not both"));
          failed++;
          continue;
        }

        // Permission check
        Response gate = checkWriteAccessForSubject(spec.getSubjectAppId(), spec.getSubjectKind(), caller);
        if (gate != null) {
          results.add(BulkAnnotationResultItemIO.error(i, "FORBIDDEN",
            "Caller lacks Write permission on subject " + spec.getSubjectAppId()));
          failed++;
          continue;
        }

        // PROJ-SEMA-WRITE-GATE-1 — project annotation constraints
        String shaclViolation = projectAnnotationConstraints.check(
          spec.getSubjectAppId(), spec.getSubjectKind(), spec.getPredicateIri(),
          spec.getObjectLiteral(), spec.getObjectIri());
        if (shaclViolation != null) {
          results.add(BulkAnnotationResultItemIO.error(i, "CONSTRAINT_VIOLATION", shaclViolation));
          failed++;
          continue;
        }

        // PROJ-SEMA-DUAL-OWNERSHIP-1 — for partOf writes, require Write on the parent Project
        String parentDeny = projectAnnotationConstraints.checkParentWritePermission(
          spec.getPredicateIri(), spec.getObjectLiteral(), caller, isAdmin);
        if (parentDeny != null) {
          results.add(BulkAnnotationResultItemIO.error(i, "FORBIDDEN", parentDeny));
          failed++;
          continue;
        }

        SemanticAnnotation annotation = new SemanticAnnotation();
        annotation.setAppId(AppIdGenerator.next());
        annotation.setSubjectKind(spec.getSubjectKind());
        annotation.setSubjectAppId(spec.getSubjectAppId());
        annotation.setPropertyIRI(spec.getPredicateIri());
        annotation.setPropertyName(spec.getPredicateLabel());
        annotation.setVocabularyId(spec.getVocabularyId());
        annotation.setValueIRI(spec.getObjectIri());
        annotation.setValueName(hasLiteral ? spec.getObjectLiteral() : null);
        annotation.setNumericValue(spec.getNumericValue());
        annotation.setUnitIRI(spec.getUnitIri());
        String resolvedSourceMode = spec.getSourceMode() != null
          ? spec.getSourceMode()
          : (aiHeader ? "ai" : "human");
        annotation.setSourceMode(resolvedSourceMode);
        annotation.setSourceActivityAppId(spec.getSourceActivityAppId());
        annotation.setAgentUsername(caller);
        annotation.setValidFromMillis(spec.getValidFromMillis());
        annotation.setValidUntilMillis(spec.getValidUntilMillis());
        annotation.setConfidence(spec.getConfidence() != null ? spec.getConfidence() : 1.0);

        annotationDAO.createOrUpdate(annotation);

        results.add(BulkAnnotationResultItemIO.success(i, annotation.getAppId(), annotation.getSubjectAppId()));
        created++;
      } catch (RuntimeException e) {
        Log.debugf(e, "SemanticAnnotationV2Rest: bulk spec[%d] failed", i);
        results.add(BulkAnnotationResultItemIO.error(i, "INTERNAL_ERROR",
          e.getMessage() != null ? e.getMessage() : "Unexpected error processing spec at index " + i));
        failed++;
      }
    }

    Log.infof("SemanticAnnotationV2Rest: bulk-created %d/%d annotations for %s",
      created, specs.size(), caller);
    // ProvenanceCaptureFilter fires once for the 207 — no per-row recordAnnotationActivity().
    return Response.status(207).entity(new BulkAnnotationResultIO(created, failed, results)).build();
  }

  // ─── UPDATE ────────────────────────────────────────────────────────────────

  @PUT
  @Path("/{appId}")
  @Operation(
    operationId = "updateAnnotation",
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

    // PROJ-SEMA-DUAL-OWNERSHIP-1 — for partOf annotations whose target is
    // being mutated, require Write on the new parent Project too. The
    // predicate itself is immutable per the @Operation contract, so the
    // existing annotation's predicate is authoritative; we only check when
    // the literal value is being changed.
    if (updateLiteral) {
      String parentDeny = projectAnnotationConstraints.checkParentWritePermission(
        annotation.getPropertyIRI(), body.getObjectLiteral(), caller,
        sc != null && sc.isUserInRole(de.dlr.shepard.common.util.Constants.INSTANCE_ADMIN_ROLE));
      if (parentDeny != null) {
        return problem(PROBLEM_TYPE_FORBIDDEN, "Parent Write required",
          Response.Status.FORBIDDEN, parentDeny);
      }
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
    operationId = "deleteAnnotation",
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

    // Resolve the subject's Neo4j labels to pick the right permission gate. This
    // is label-driven (not the caller-supplied subjectKind string) so a
    // mislabeled or omitted subjectKind can't bypass or wrongly deny the gate.
    long ogmId;
    java.util.List<String> labels;
    try {
      var res = entityIdResolver.resolveWithLabels(subjectAppId);
      ogmId = res.ogmId();
      labels = res.labels();
    } catch (jakarta.ws.rs.NotFoundException nfe) {
      // Subject is not a resolvable Neo4j entity (e.g. a TimescaleDB channel
      // shepardId). Per the entity-scoped-discovery contract, an unresolvable
      // subject is open for Read (the underlying data is gated by its container)
      // and closed for Write.
      if (accessType == AccessType.Read) return null;
      return problem(PROBLEM_TYPE_FORBIDDEN, "Access denied", Response.Status.FORBIDDEN,
        "Subject '" + subjectAppId + "' could not be resolved; cannot grant "
          + accessType.name() + " permission.");
    }

    // Collections AND every *Container kind carry their OWN Permissions node, so
    // gate them directly on that node (a PublicReadable container is readable by
    // any authenticated caller). DataObjects, References and other children
    // inherit from their parent Collection via the DataObject→Collection walk.
    boolean ownsPermissionsNode =
      labels.contains("Collection") || labels.stream().anyMatch(l -> l.endsWith("Container"));
    boolean isChannel =
      labels.contains("AnnotatableTimeseries") || labels.contains("Timeseries");
    if (ownsPermissionsNode) {
      // Use the 3-arg overload so currentIat() is resolved internally — same as every
      // other Collection/Container permission check in v2.  The 4-arg form with jwtIat=0L
      // is for API-key / non-JWT callers; using it on a @Authenticated endpoint created a
      // stale cache key that caused the collection owner to receive 403 (RESEED-FIND-MISC a).
      allowed = permissionsService.isAccessTypeAllowedForUser(ogmId, accessType, caller);
    } else if (isChannel) {
      // A timeseries channel (AnnotatableTimeseries bridge node) has no own
      // Permissions node and no Neo4j edge to its container, so neither the direct
      // check nor the DataObject walk can gate it. Its data is already gated at the
      // container level, so annotation reads are open; writes via this REST path
      // are denied (channel annotations are written by the dual-write service).
      // TODO follow-up: resolve container via channel_metadata to allow container
      // writers to annotate channels through the REST surface.
      if (accessType == AccessType.Read) return null;
      return problem(PROBLEM_TYPE_FORBIDDEN, "Access denied", Response.Status.FORBIDDEN,
        "Channel annotations cannot be modified through this endpoint.");
    } else {
      // F9: Reference nodes are NOT DataObject nodes — isAccessAllowedForDataObjectAppId
      // queries MATCH (c:Collection)-[:HAS_DATAOBJECT]->(d:DataObject {appId:…}), which
      // never matches a Reference appId.  Detect Reference labels and walk to the parent
      // DataObject first; fall through to the DataObject walk for everything else.
      boolean isReference = labels.stream().anyMatch(l -> l.endsWith("Reference"));
      if (isReference) {
        Optional<ReferencesV2Service.ResolvedReference> refOpt =
          referencesService.resolveByAppId(subjectAppId);
        if (refOpt.isEmpty()) {
          if (accessType == AccessType.Read) return null;
          return problem(PROBLEM_TYPE_FORBIDDEN, "Access denied", Response.Status.FORBIDDEN,
            "Reference '" + subjectAppId + "' could not be resolved; cannot grant Write access.");
        }
        DataObject parent = refOpt.get().reference().getDataObject();
        if (parent == null) {
          if (accessType == AccessType.Read) return null;
          return problem(PROBLEM_TYPE_FORBIDDEN, "Access denied", Response.Status.FORBIDDEN,
            "Reference '" + subjectAppId + "' has no parent DataObject; cannot grant Write access.");
        }
        String doAppId = parent.getAppId();
        if (doAppId != null) {
          allowed = permissionsService.isAccessAllowedForDataObjectAppId(doAppId, accessType, caller);
        } else {
          allowed = permissionsService.isAccessTypeAllowedForUser(parent.getId(), accessType, caller, 0L);
        }
      } else {
        allowed = permissionsService.isAccessAllowedForDataObjectAppId(subjectAppId, accessType, caller);
      }
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
    return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required",
        Response.Status.UNAUTHORIZED, "Authentication is required to access annotations.");
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
