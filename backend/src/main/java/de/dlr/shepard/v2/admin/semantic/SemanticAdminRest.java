package de.dlr.shepard.v2.admin.semantic;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.semantic.OntologyRefreshService;
import de.dlr.shepard.context.semantic.OntologyRefreshService.BundleError;
import de.dlr.shepard.context.semantic.OntologyRefreshService.RefreshOutcome;
import de.dlr.shepard.context.semantic.OntologySeedService;
import de.dlr.shepard.context.semantic.OntologySeedService.OntologyEntry;
import de.dlr.shepard.context.semantic.services.OntologyConfigService;
import de.dlr.shepard.context.semantic.services.OntologyConfigService.BundleView;
import de.dlr.shepard.context.semantic.services.OntologyConfigService.RemoveResult;
import de.dlr.shepard.context.semantic.services.OntologyConfigService.SetEnabledResult;
import de.dlr.shepard.context.semantic.services.OntologyConfigService.UploadMetadata;
import de.dlr.shepard.context.semantic.services.OntologyConfigService.UploadResult;
import de.dlr.shepard.v2.admin.semantic.io.OntologyBundleIO;
import de.dlr.shepard.v2.admin.semantic.io.OntologyBundleListIO;
import de.dlr.shepard.v2.admin.semantic.io.RefreshOntologiesRequestIO;
import de.dlr.shepard.v2.admin.semantic.io.RefreshOntologiesResultIO;
import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

/**
 * N1c — operator endpoint that re-fetches the bundled ontologies from
 * each manifest entry's pinned {@code canonicalUrl}, recomputes the
 * SHA-256, and re-imports into n10s when the hash differs (or when
 * {@code force=true}).
 *
 * <p>The complement to N1b's startup pre-seed (ADR-0019). Pre-seed
 * ships minimum-viable Turtle stubs in the JAR so the casual
 * annotation flow works on day one; this endpoint lets an operator
 * pull in the full canonical Turtle without waiting for the next
 * shepard release.
 *
 * <p>Returns {@link RefreshOntologiesResultIO} on success — per-bundle
 * failures live in the {@code errors[]} array with 200 OK overall.
 * RFC 7807 {@link ProblemJson} is reserved for the auth-denied paths.
 *
 * <p>The endpoint is intentionally <b>operator-only</b> — there's no
 * frontend exposure. Refresh is a controlled-cadence action; making
 * it routine-clickable would invite accidental re-imports against
 * slow / rate-limited canonical hosts.
 *
 * @see OntologyRefreshService
 * @see de.dlr.shepard.context.semantic.OntologySeedService
 */
@Path("/v2/admin/semantic")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
@Tag(name = "Admin")
public class SemanticAdminRest {

  /** RFC 7807 type-URI for the auth-denied path. */
  static final String PROBLEM_TYPE_AUTH = "/problems/auth.denied";

  /** RFC 7807 type-URI prefix for N1c2 bundle-management failures. */
  static final String PROBLEM_TYPE_BUNDLE_NOT_FOUND = "/problems/semantic.bundle.not-found";
  static final String PROBLEM_TYPE_BUNDLE_REQUIRED = "/problems/semantic.bundle.required";
  static final String PROBLEM_TYPE_BUNDLE_DUPLICATE = "/problems/semantic.bundle.duplicate-id";
  static final String PROBLEM_TYPE_BUNDLE_BUILTIN = "/problems/semantic.bundle.builtin-not-removable";
  static final String PROBLEM_TYPE_BUNDLE_INVALID_TTL = "/problems/semantic.bundle.invalid-ttl";
  static final String PROBLEM_TYPE_BUNDLE_TOO_LARGE = "/problems/semantic.bundle.too-large";
  static final String PROBLEM_TYPE_BUNDLE_BAD_METADATA = "/problems/semantic.bundle.bad-metadata";

  @Inject
  OntologyRefreshService refreshService;

  @Inject
  OntologyConfigService configService;

  @Inject
  AuthenticationContext authenticationContext;

  /**
   * Seed-service handle for {@link OntologyEntry#loadManifest()}.
   * Lazy-instantiated rather than {@code @Inject}-d because the
   * production seed service is constructed by the bootstrap hook,
   * not by CDI. Tests override via the package-private setter.
   */
  OntologySeedService seedServiceForManifest;

  /** Test seam — replace the seed service used to load the manifest. */
  void setSeedServiceForManifest(OntologySeedService svc) {
    this.seedServiceForManifest = svc;
  }

  private List<OntologyEntry> builtinManifest() {
    OntologySeedService s = seedServiceForManifest;
    if (s == null) {
      s = new OntologySeedService();
      this.seedServiceForManifest = s;
    }
    return s.loadManifest();
  }

  /**
   * Defence-in-depth role check. Mirrors {@code HdfAdminRest} —
   * {@code @RolesAllowed} catches the canonical paths, the manual
   * check guards against test-only paths that bypass the JAX-RS
   * filter chain.
   */
  private static void requireInstanceAdmin(SecurityContext securityContext) {
    if (securityContext == null || securityContext.getUserPrincipal() == null) {
      throw new InvalidAuthException("Authentication required");
    }
    if (!securityContext.isUserInRole(Constants.INSTANCE_ADMIN_ROLE)) {
      throw new InvalidAuthException("instance-admin role required");
    }
  }

  @POST
  @Path("/refresh-ontologies")
  @Operation(
    summary = "Refresh bundled ontologies against their pinned canonical URLs.",
    description = "Walks every bundle in ontologies-manifest.json (or the subset named in " +
    "request.bundles), fetches each bundle's canonicalUrl, recomputes its SHA-256, and " +
    "re-imports into n10s when the hash differs. Use after a major ontology release, or " +
    "to land the full canonical Turtle in bulk over the shipped minimum-viable stubs " +
    "(per N1b / ADR-0019). Best-effort per bundle — partial failures return 200 with errors[]."
  )
  @APIResponse(
    responseCode = "200",
    description = "Refresh attempted. Per-bundle failures live in errors[].",
    content = @Content(schema = @Schema(implementation = RefreshOntologiesResultIO.class))
  )
  @APIResponse(
    responseCode = "401",
    description = "Authentication required (RFC 7807).",
    content = @Content(schema = @Schema(implementation = ProblemJson.class))
  )
  @APIResponse(
    responseCode = "403",
    description = "Caller lacks the instance-admin role (RFC 7807).",
    content = @Content(schema = @Schema(implementation = ProblemJson.class))
  )
  public Response refreshOntologies(
    RefreshOntologiesRequestIO body,
    @Context SecurityContext securityContext
  ) {
    try {
      requireInstanceAdmin(securityContext);
    } catch (InvalidAuthException denied) {
      Status status = denied.getMessage() != null && denied.getMessage().contains("Authentication required")
        ? Status.UNAUTHORIZED
        : Status.FORBIDDEN;
      return problem(
        PROBLEM_TYPE_AUTH,
        denied.getMessage(),
        status,
        denied.getMessage()
      );
    }

    final RefreshOntologiesRequestIO req = body == null ? new RefreshOntologiesRequestIO() : body;
    Log.infof(
      "SemanticAdminRest: refresh-ontologies invoked (bundles=%s, force=%s)",
      req.getBundles(),
      req.isForce()
    );

    RefreshOutcome outcome = refreshService.refresh(req.getBundles(), req.isForce());

    List<RefreshOntologiesResultIO.Error> errors = new ArrayList<>(outcome.errors.size());
    for (BundleError e : outcome.errors) {
      errors.add(new RefreshOntologiesResultIO.Error(e.bundle, e.reason));
    }
    RefreshOntologiesResultIO result = new RefreshOntologiesResultIO(
      outcome.requested,
      outcome.refreshed,
      outcome.alreadyCurrent,
      errors
    );
    return Response.ok(result).build();
  }

  // ─── N1c2: list / enable / disable / upload / delete ────────────────────

  @GET
  @Path("/ontologies")
  @Operation(
    summary = "List every pre-seeded + operator-uploaded ontology bundle.",
    description = "Built-ins first (manifest declaration order), then user-uploaded bundles " +
    "(id ASC). Each row's `enabled` is the effective state under the precedence rules " +
    "(required wins; otherwise 'not in runtime disabledBundles ∪ deploy-time skip-bundles')."
  )
  @APIResponse(
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = OntologyBundleListIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required (RFC 7807).")
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role (RFC 7807).")
  public Response listOntologies(@Context SecurityContext securityContext) {
    Response denied = guardAdmin(securityContext);
    if (denied != null) return denied;

    List<OntologyEntry> manifest;
    try {
      manifest = builtinManifest();
    } catch (RuntimeException ex) {
      Log.errorf(ex, "SemanticAdminRest: failed to load built-in manifest for /ontologies");
      manifest = List.of();
    }

    List<BundleView> merged = configService.listMerged(manifest);
    List<OntologyBundleIO> rows = new ArrayList<>(merged.size());
    for (BundleView v : merged) rows.add(OntologyBundleIO.from(v));
    return Response.ok(new OntologyBundleListIO(rows)).build();
  }

  @POST
  @Path("/ontologies/{bundleId}/disable")
  @Operation(
    summary = "Runtime-disable an ontology bundle.",
    description = "Adds the id to :SemanticConfig.disabledBundles. The bundle stops seeding on " +
    "the next startup. Refused (409 RFC 7807 semantic.bundle.required) for bundles whose " +
    "manifest entry carries required=true (prov-o, obo-relations today)."
  )
  @APIResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = OntologyBundleIO.class)))
  @APIResponse(responseCode = "404", description = "Unknown bundle id (RFC 7807).")
  @APIResponse(responseCode = "409", description = "Bundle is required and cannot be disabled (RFC 7807).")
  public Response disableOntology(
    @PathParam("bundleId") String bundleId,
    @Context SecurityContext securityContext
  ) {
    Response denied = guardAdmin(securityContext);
    if (denied != null) return denied;
    return flipEnabled(bundleId, false, securityContext);
  }

  @POST
  @Path("/ontologies/{bundleId}/enable")
  @Operation(
    summary = "Runtime-enable a previously-disabled ontology bundle.",
    description = "Removes the id from :SemanticConfig.disabledBundles. No-op if the id is not " +
    "currently disabled. The bundle seeds on the next startup."
  )
  @APIResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = OntologyBundleIO.class)))
  @APIResponse(responseCode = "404", description = "Unknown bundle id (RFC 7807).")
  public Response enableOntology(
    @PathParam("bundleId") String bundleId,
    @Context SecurityContext securityContext
  ) {
    Response denied = guardAdmin(securityContext);
    if (denied != null) return denied;
    return flipEnabled(bundleId, true, securityContext);
  }

  /** Shared body for enable/disable — applies the flip and renders the merged row. */
  private Response flipEnabled(String bundleId, boolean enabled, SecurityContext securityContext) {
    List<OntologyEntry> manifest;
    try {
      manifest = builtinManifest();
    } catch (RuntimeException ex) {
      Log.errorf(ex, "SemanticAdminRest: failed to load built-in manifest for flip");
      manifest = List.of();
    }
    String actor = callerName(securityContext);
    SetEnabledResult result = configService.setBundleEnabled(bundleId, enabled, actor, manifest);
    switch (result) {
      case NOT_FOUND:
        return problem(
          PROBLEM_TYPE_BUNDLE_NOT_FOUND,
          "Unknown bundle id",
          Status.NOT_FOUND,
          "No bundle '" + bundleId + "' in the built-in or user catalogue."
        );
      case REQUIRED_CANNOT_DISABLE:
        return problem(
          PROBLEM_TYPE_BUNDLE_REQUIRED,
          "Bundle is required",
          Status.CONFLICT,
          "Bundle '" + bundleId + "' is required and cannot be disabled."
        );
      case OK:
      default:
        Optional<BundleView> view = configService.findBundle(bundleId, manifest);
        if (view.isEmpty()) {
          // Shouldn't happen — we just confirmed it exists — but guard anyway.
          return problem(
            PROBLEM_TYPE_BUNDLE_NOT_FOUND,
            "Unknown bundle id",
            Status.NOT_FOUND,
            "No bundle '" + bundleId + "' in the built-in or user catalogue."
          );
        }
        return Response.ok(OntologyBundleIO.from(view.get())).build();
    }
  }

  @POST
  @Path("/ontologies")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @Operation(
    summary = "Upload an operator-supplied ontology bundle.",
    description = "Multipart: file=<ttl bytes> plus metadata={\"id\":...,\"name\":...," +
    "\"iriPrefix\":...,\"canonicalUrl\":...,\"license\":...}. Server SHA-256s the bytes, " +
    "writes them to <shepard.semantic.internal.user-bundles-dir>/<id>.ttl, persists a " +
    ":UserOntologyBundle catalogue row. The bundle joins the seed loop on next startup. " +
    "Validation: id must match ^[a-z0-9][a-z0-9_-]{0,63}$ and not collide with a built-in " +
    "or another user bundle; payload ≤ 10 MB; payload must look like Turtle."
  )
  @APIResponse(responseCode = "201", content = @Content(schema = @Schema(implementation = OntologyBundleIO.class)))
  @APIResponse(responseCode = "400", description = "Invalid TTL or missing metadata (RFC 7807).")
  @APIResponse(responseCode = "409", description = "Bundle id collides with a built-in or existing user upload (RFC 7807).")
  public Response uploadOntology(
    @RestForm("file") FileUpload upload,
    @RestForm("metadata") String metadataJson,
    @Context SecurityContext securityContext
  ) {
    Response denied = guardAdmin(securityContext);
    if (denied != null) return denied;

    if (upload == null || upload.uploadedFile() == null) {
      return problem(
        PROBLEM_TYPE_BUNDLE_BAD_METADATA,
        "Missing file part",
        Status.BAD_REQUEST,
        "Multipart upload must include a 'file' part with the TTL bytes."
      );
    }

    UploadMetadata meta;
    try {
      meta = parseMetadata(metadataJson);
    } catch (RuntimeException ex) {
      return problem(
        PROBLEM_TYPE_BUNDLE_BAD_METADATA,
        "Invalid metadata",
        Status.BAD_REQUEST,
        "Could not parse 'metadata' part as JSON: " + ex.getMessage()
      );
    }

    byte[] payload;
    try {
      payload = Files.readAllBytes(upload.uploadedFile());
    } catch (IOException ex) {
      return problem(
        PROBLEM_TYPE_BUNDLE_INVALID_TTL,
        "Could not read uploaded payload",
        Status.BAD_REQUEST,
        ex.getMessage()
      );
    }

    List<OntologyEntry> manifest;
    try {
      manifest = builtinManifest();
    } catch (RuntimeException ex) {
      Log.errorf(ex, "SemanticAdminRest: failed to load built-in manifest for upload");
      manifest = List.of();
    }
    String actor = callerName(securityContext);
    UploadResult result = configService.uploadBundle(payload, meta, actor, manifest);

    switch (result.status) {
      case CREATED:
        Optional<BundleView> view = configService.findBundle(result.saved.getBundleId(), manifest);
        OntologyBundleIO body = view.map(OntologyBundleIO::from).orElseGet(() -> {
          OntologyBundleIO fallback = new OntologyBundleIO();
          fallback.setId(result.saved.getBundleId());
          fallback.setSource("user");
          fallback.setEnabled(true);
          fallback.setSha256(result.saved.getSha256());
          fallback.setByteSize(result.saved.getByteSize() == null ? 0L : result.saved.getByteSize());
          return fallback;
        });
        return Response.status(Status.CREATED).entity(body).build();
      case DUPLICATE_ID:
        return problem(
          PROBLEM_TYPE_BUNDLE_DUPLICATE,
          "Duplicate bundle id",
          Status.CONFLICT,
          result.reason
        );
      case TOO_LARGE:
        return problem(
          PROBLEM_TYPE_BUNDLE_TOO_LARGE,
          "Payload too large",
          Status.BAD_REQUEST,
          result.reason
        );
      case INVALID_TTL:
        return problem(
          PROBLEM_TYPE_BUNDLE_INVALID_TTL,
          "Invalid Turtle payload",
          Status.BAD_REQUEST,
          result.reason
        );
      case BAD_METADATA:
        return problem(
          PROBLEM_TYPE_BUNDLE_BAD_METADATA,
          "Invalid metadata",
          Status.BAD_REQUEST,
          result.reason
        );
      case IO_ERROR:
      default:
        return problem(
          PROBLEM_TYPE_BUNDLE_INVALID_TTL,
          "Upload failed",
          Status.INTERNAL_SERVER_ERROR,
          result.reason
        );
    }
  }

  @DELETE
  @Path("/ontologies/{bundleId}")
  @Operation(
    summary = "Remove an operator-uploaded ontology bundle.",
    description = "Drops the on-disk TTL + the :UserOntologyBundle catalogue row. Refused " +
    "(409 RFC 7807 semantic.bundle.builtin-not-removable) for built-in bundle ids — those " +
    "ship in the JAR and update via release upgrades."
  )
  @APIResponse(responseCode = "204", description = "Bundle removed.")
  @APIResponse(responseCode = "404", description = "Unknown bundle id (RFC 7807).")
  @APIResponse(responseCode = "409", description = "Bundle is built-in and cannot be removed (RFC 7807).")
  public Response deleteOntology(
    @PathParam("bundleId") String bundleId,
    @Context SecurityContext securityContext
  ) {
    Response denied = guardAdmin(securityContext);
    if (denied != null) return denied;

    List<OntologyEntry> manifest;
    try {
      manifest = builtinManifest();
    } catch (RuntimeException ex) {
      Log.errorf(ex, "SemanticAdminRest: failed to load built-in manifest for delete");
      manifest = List.of();
    }
    String actor = callerName(securityContext);
    RemoveResult result = configService.removeBundle(bundleId, actor, manifest);
    switch (result) {
      case REMOVED:
        return Response.noContent().build();
      case NOT_FOUND:
        return problem(
          PROBLEM_TYPE_BUNDLE_NOT_FOUND,
          "Unknown bundle id",
          Status.NOT_FOUND,
          "No user bundle '" + bundleId + "' in the catalogue."
        );
      case BUILTIN_NOT_REMOVABLE:
      default:
        return problem(
          PROBLEM_TYPE_BUNDLE_BUILTIN,
          "Built-in bundle is not removable",
          Status.CONFLICT,
          "Bundle '" + bundleId + "' is built-in; ships in the JAR. Remove via release upgrade."
        );
    }
  }

  // ─── helpers ────────────────────────────────────────────────────────────

  /** Returns a 7807 response when auth fails; null when the caller is good. */
  private Response guardAdmin(SecurityContext securityContext) {
    try {
      requireInstanceAdmin(securityContext);
      return null;
    } catch (InvalidAuthException denied) {
      Status status = denied.getMessage() != null && denied.getMessage().contains("Authentication required")
        ? Status.UNAUTHORIZED
        : Status.FORBIDDEN;
      return problem(
        PROBLEM_TYPE_AUTH,
        denied.getMessage(),
        status,
        denied.getMessage()
      );
    }
  }

  private static String callerName(SecurityContext sc) {
    if (sc == null || sc.getUserPrincipal() == null) return null;
    String n = sc.getUserPrincipal().getName();
    return n == null || n.isBlank() ? null : n;
  }

  /** Parse the multipart 'metadata' JSON part into the service-layer DTO. */
  private static UploadMetadata parseMetadata(String json) {
    if (json == null || json.isBlank()) {
      throw new IllegalArgumentException("metadata part is required");
    }
    try {
      var node = new ObjectMapper().readTree(json);
      String id = textOrNull(node, "id");
      String name = textOrNull(node, "name");
      String iriPrefix = textOrNull(node, "iriPrefix");
      String canonicalUrl = textOrNull(node, "canonicalUrl");
      String license = textOrNull(node, "license");
      return new UploadMetadata(id, name, iriPrefix, canonicalUrl, license);
    } catch (IOException ex) {
      throw new IllegalArgumentException(ex.getMessage(), ex);
    }
  }

  private static String textOrNull(com.fasterxml.jackson.databind.JsonNode root, String f) {
    var v = root.get(f);
    if (v == null || v.isNull()) return null;
    return v.isTextual() ? v.asText() : v.toString();
  }

  private Response problem(String type, String title, Status status, String detail) {
    ProblemJson body = new ProblemJson(type, title, status.getStatusCode(), detail, null);
    return Response.status(status).type("application/problem+json").entity(body).build();
  }
}
