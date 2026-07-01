package de.dlr.shepard.v2.importer.resources;

import de.dlr.shepard.auth.bootstrap.BootstrapTokenInitializer;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.daos.CollectionPropertiesDAO;
import de.dlr.shepard.context.semantic.daos.SemanticAnnotationDAO;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.v2.importer.daos.ImportPlanDAO;
import de.dlr.shepard.v2.importer.entities.ImportPlan;
import de.dlr.shepard.v2.importer.io.ImportContextIO;
import de.dlr.shepard.v2.importer.io.ImportContextIO.AnnotationSummaryIO;
import de.dlr.shepard.v2.importer.io.ImportContextIO.SemanticGraphIO;
import de.dlr.shepard.v2.importer.io.ImportManifestIO;
import de.dlr.shepard.v2.importer.io.ImportPlanIO;
import de.dlr.shepard.v2.importer.io.ImportPlanIO.ImportSummaryIO;
import de.dlr.shepard.v2.importer.services.ImportValidationService;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * IMP1 — {@code /v2/import}: dry-run import validation and plan inspection.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /v2/import/validate} — validate an import manifest; issues a commitId
 *       (plan seal) on success.  Returns 422 with error details when the manifest has hard
 *       errors.  No data is written to the database in the error case.</li>
 *   <li>{@code GET /v2/import/plans/{commitId}} — retrieve a previously-issued plan by its
 *       commitId.  Returns 404 when no matching plan exists.</li>
 * </ul>
 *
 * <p>The commitId must be presented to {@code POST /v2/import/jobs} (out of scope for this
 * task — stub only) to execute the actual import.  The plan expires 24 hours after
 * issuance; expired plans are still queryable via the GET endpoint but the jobs endpoint
 * will reject them.
 *
 * <p>Auth: callers must have {@link AccessType#Write} on the target Collection for
 * {@code POST /v2/import/validate}.  The GET endpoint requires only authentication.
 *
 * <p>In-tree rationale: the import seam is in-tree (rather than
 * {@code shepard-plugin-importer}) because it is core infrastructure — it defines the
 * commitId / plan-seal contract that plugin-level importers will depend on.  The plugin
 * layer (importer library, per-format adapters) builds on top of this seam.
 */
@Path("/v2/import")
@Authenticated
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Import")
public class ImportV2Rest {

  private static final String PT_NOT_FOUND    = "/problems/import.not-found";
  private static final String PT_BAD_REQUEST  = "/problems/import.bad-request";
  private static final String PT_UNAUTHORIZED = "/problems/import.unauthorized";
  private static final String PT_FORBIDDEN    = "/problems/import.forbidden";

  @Inject
  ImportValidationService validationService;

  @Inject
  ImportPlanDAO importPlanDAO;

  @Inject
  PermissionsService permissionsService;

  @Inject
  CollectionPropertiesDAO collectionPropertiesDAO;

  @Inject
  SemanticAnnotationDAO semanticAnnotationDAO;

  // ─── POST /validate ───────────────────────────────────────────────────────

  @POST
  @Path("/validate")
  @Operation(
    operationId = "validateImport",
    summary = "Dry-run validate an import manifest (IMP1)",
    description =
      "Validates the manifest without writing any DataObjects, Containers, or References. " +
      "On success, returns a commitId (plan seal) that must be presented to " +
      "POST /v2/import/jobs to execute the import. " +
      "The commitId is bound to the collection state at validation time — " +
      "if the collection changes before the import runs, the jobs endpoint will reject the plan. " +
      "Plans expire 24 hours after issuance.",
    extensions = @Extension(name = "x-agent-hint", value = "Dry-run import — returns validation errors without committing. Commit with sealImport using the returned commitId.")
  )
  @APIResponse(responseCode = "200", description = "Manifest valid — commitId issued")
  @APIResponse(responseCode = "401", description = "Authentication required")
  @APIResponse(responseCode = "403", description = "Caller does not have Write on the target Collection")
  @APIResponse(responseCode = "404", description = "Target Collection not found")
  @APIResponse(responseCode = "422", description = "Manifest has hard errors — no commitId issued")
  public Response validate(
    @Valid ImportManifestIO manifest,
    @Context SecurityContext sc
  ) {
    String caller = caller(sc);
    if (caller == null) return unauthorized();

    // Permission check: caller must have Write on the target Collection.
    Optional<Long> collOgmId = collectionPropertiesDAO.findCollectionIdByAppId(
      manifest.collectionAppId());
    if (collOgmId.isEmpty()) {
      return problem(PT_NOT_FOUND, "Collection not found", Response.Status.NOT_FOUND,
        "Collection not found: " + manifest.collectionAppId());
    }
    if (!permissionsService.isAccessTypeAllowedForUser(
        collOgmId.get(), AccessType.Write, caller)) {
      return problem(PT_FORBIDDEN, "Write permission required", Response.Status.FORBIDDEN,
        "caller lacks Write permission on the target collection");
    }

    ImportPlan plan = validationService.validate(manifest, caller);
    List<String> errors = validationService.extractErrors(plan);
    ImportPlanIO io = toIO(plan, errors);

    if (!errors.isEmpty()) {
      return Response.status(422).type("application/problem+json").entity(io).build();
    }
    return Response.ok(io).build();
  }

  // ─── GET /plans/{commitId} ────────────────────────────────────────────────

  @GET
  @Path("/plans/{commitId}")
  @Operation(
    operationId = "getPlan",
    summary = "Get an import plan by commitId (IMP1)",
    description =
      "Returns the validation plan associated with a commitId. " +
      "Expired plans are still returned with status=EXPIRED; " +
      "the jobs endpoint rejects them."
  )
  @APIResponse(responseCode = "200", description = "Plan found")
  @APIResponse(responseCode = "401", description = "Authentication required")
  @APIResponse(responseCode = "404", description = "No plan with this commitId")
  public Response getPlan(
    @PathParam("commitId") String commitId,
    @Context SecurityContext sc
  ) {
    if (caller(sc) == null) return unauthorized();

    ImportPlan plan = importPlanDAO.findByCommitId(commitId);
    if (plan == null) return problem(PT_NOT_FOUND, "Plan not found", Response.Status.NOT_FOUND,
      "no plan found for commitId: " + commitId);

    return Response.ok(toIO(plan, List.of())).build();
  }

  // ─── GET /context ─────────────────────────────────────────────────────────

  @GET
  @Path("/context")
  @Operation(
    operationId = "getContext",
    summary = "Get the current collection state for importers/agents (IMP1)",
    description =
      "Returns a snapshot of the target collection — DataObject count and a " +
      "SHA-256 fingerprint of the collection state — so that an importer or " +
      "agent can generate a manifest that is consistent with the collection " +
      "as it currently exists.\n\n" +
      "When `includeSemanticGraph=true` is supplied, the response also includes " +
      "the semantic annotations currently attached to DataObjects in the " +
      "collection, giving agents the vocabulary they need to annotate new " +
      "DataObjects consistently with existing terms.\n\n" +
      "The `includeSemanticGraph=false` (default) path is fast — it executes " +
      "a single Cypher count query and skips any annotation lookup.\n\n" +
      "Auth: Read on the target Collection. 404 when the `collectionAppId` " +
      "does not resolve."
  )
  @APIResponse(
    responseCode = "200",
    description = "Collection context snapshot.",
    content = @Content(schema = @Schema(implementation = ImportContextIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the Collection.")
  @APIResponse(responseCode = "404", description = "No Collection with that appId.")
  public Response getContext(
    @Parameter(
      description =
        "appId (UUID v7) of the target Collection. Required. Returns 404 when " +
        "no Collection with this appId exists. The caller must have Read permission.",
      required = true
    )
    @QueryParam("collectionAppId") String collectionAppId,
    @Parameter(
      description =
        "When true, the response body includes the full semantic-annotation graph " +
        "of the target Collection (predicate IRIs, object values, vocabulary ids). " +
        "Defaults to false. The false path executes a single Cypher count query " +
        "and skips all annotation look-ups, making it significantly cheaper for " +
        "importers that do not need annotation context."
    )
    @QueryParam("includeSemanticGraph") @DefaultValue("false") boolean includeSemanticGraph,
    @Context SecurityContext sc
  ) {
    String caller = caller(sc);
    if (caller == null) return unauthorized();

    if (collectionAppId == null || collectionAppId.isBlank()) {
      return problem(PT_BAD_REQUEST, "Bad request", Response.Status.BAD_REQUEST,
        "collectionAppId query parameter is required");
    }

    Optional<Long> collOgmId = collectionPropertiesDAO.findCollectionIdByAppId(collectionAppId);
    if (collOgmId.isEmpty()) {
      return problem(PT_NOT_FOUND, "Collection not found", Response.Status.NOT_FOUND,
        "Collection not found: " + collectionAppId);
    }
    if (!permissionsService.isAccessTypeAllowedForUser(collOgmId.get(), AccessType.Read, caller)) {
      return problem(PT_FORBIDDEN, "Read permission required", Response.Status.FORBIDDEN,
        "caller lacks Read permission on the target collection");
    }

    // Count DataObjects and compute fingerprint (single Cypher round-trip).
    long dataObjectCount = importPlanDAO.countDataObjects(collectionAppId);
    String rawFingerprint = importPlanDAO.getRawCollectionFingerprintInput(collectionAppId);
    String fingerprint = "sha256:" + BootstrapTokenInitializer.sha256Hex(rawFingerprint);

    SemanticGraphIO semanticGraph = null;
    if (includeSemanticGraph) {
      List<SemanticAnnotation> annotations =
        semanticAnnotationDAO.findByCollectionAppId(collectionAppId);
      List<AnnotationSummaryIO> summaries = annotations.stream()
        .map(a -> new AnnotationSummaryIO(
          a.getAppId(),
          a.getPropertyName(),
          a.getValueName(),
          a.getPropertyIRI(),
          a.getValueIRI()
        ))
        .toList();
      semanticGraph = new SemanticGraphIO(summaries);
    }

    ImportContextIO ctx = new ImportContextIO(
      collectionAppId,
      dataObjectCount,
      fingerprint,
      semanticGraph
    );
    return Response.ok(ctx).build();
  }

  // ─── Helpers ──────────────────────────────────────────────────────────────

  private ImportPlanIO toIO(ImportPlan plan, List<String> errors) {
    ImportSummaryIO summary = validationService.extractSummary(plan);
    List<String> warnings = validationService.extractWarnings(plan);

    String expiresAt = plan.getExpiresAt() != null
      ? epochMillisToIso8601(plan.getExpiresAt())
      : null;

    // Invalidated plans have no commitId.
    String commitId = "INVALIDATED".equals(plan.getStatus()) ? null : plan.getCommitId();

    return new ImportPlanIO(commitId, plan.getStatus(), expiresAt, summary, warnings, errors);
  }

  private static Response problem(String type, String title, Response.Status status, String detail) {
    ProblemJson body = new ProblemJson(type, title, status.getStatusCode(), detail, null);
    return Response.status(status).type("application/problem+json").entity(body).build();
  }

  private static String caller(SecurityContext sc) {
    return sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
  }

  private static Response unauthorized() {
    return problem(PT_UNAUTHORIZED, "Authentication required", Response.Status.UNAUTHORIZED,
      "authentication required");
  }

  private static String epochMillisToIso8601(long epochMillis) {
    return DateTimeFormatter.ISO_INSTANT.format(
      Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC)
    );
  }
}
