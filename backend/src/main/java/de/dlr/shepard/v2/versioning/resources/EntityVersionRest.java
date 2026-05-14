package de.dlr.shepard.v2.versioning.resources;

import de.dlr.shepard.auth.permission.io.PermissionsIO;
import de.dlr.shepard.auth.permission.model.Permissions;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.publish.PublishableKind;
import de.dlr.shepard.publish.PublishableKindRegistry;
import de.dlr.shepard.v2.versioning.io.EntityVersionCreateIO;
import de.dlr.shepard.v2.versioning.io.EntityVersionIO;
import de.dlr.shepard.v2.versioning.io.EntityVersionListIO;
import de.dlr.shepard.versioning.EntityVersion;
import de.dlr.shepard.versioning.EntityVersionException;
import de.dlr.shepard.versioning.EntityVersionService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
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
 * ENT1a REST surface per {@code aidocs/16} ENT1a. Mounts the five
 * version-management endpoints under
 * {@code /v2/{kind}/{appId}/versions/...} where {@code {kind}} is one
 * of {@code data-objects} / {@code collections} (sourced from
 * {@link PublishableKindRegistry} — extending to Bundle / File /
 * Reference is a one-row registry edit, no URL-shape change per the
 * "single point of truth" pattern KIP1a established).
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /v2/{kind}/{appId}/versions} — mint a new
 *       version. Body {@link EntityVersionCreateIO}; both fields
 *       optional. Auth: caller must hold Write or Manage on the
 *       parent entity ({@link AccessType#Write} already admits both
 *       per the legacy {@code rolesGrantAccess}).</li>
 *   <li>{@code GET /v2/{kind}/{appId}/versions} — list every version
 *       visible to the caller, ordered newest-first. Auth: per-version
 *       Reader on each row's ACL (filtered).</li>
 *   <li>{@code GET /v2/{kind}/{appId}/versions/{label}} — fetch a
 *       single version. Auth: per-version Reader.</li>
 *   <li>{@code PATCH /v2/{kind}/{appId}/versions/{label}/permissions}
 *       — flip the per-version ACL. Auth: per-version Manager.</li>
 *   <li>{@code DELETE /v2/{kind}/{appId}/versions/{label}} — remove a
 *       version. Auth: per-version Manager. Refuses to remove the
 *       last remaining version (409 {@code versions.cannot-delete-only}).</li>
 * </ul>
 *
 * <p>RFC 7807 problem types:
 * {@code versions.not-found} (404),
 * {@code versions.label.duplicate} (409),
 * {@code versions.label.invalid} (400),
 * {@code versions.cannot-delete-only} (409),
 * {@code versions.kind.unsupported} (404).
 *
 * <p>PROV1a hook: every mutation (POST / PATCH / DELETE) is captured
 * into {@code :Activity} automatically by
 * {@link de.dlr.shepard.provenance.filters.ProvenanceCaptureFilter}
 * (admin-style endpoints default-opt-in). No extra wiring here.
 */
@Path("/v2/{kind}/{appId}/versions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Tag(name = "Entity versions (v2)")
public class EntityVersionRest {

  static final String PROBLEM_TYPE_NOT_FOUND = "https://shepard.dlr.de/problems/versions.not-found";
  static final String PROBLEM_TYPE_LABEL_DUPLICATE = "https://shepard.dlr.de/problems/versions.label.duplicate";
  static final String PROBLEM_TYPE_LABEL_INVALID = "https://shepard.dlr.de/problems/versions.label.invalid";
  static final String PROBLEM_TYPE_CANNOT_DELETE_ONLY = "https://shepard.dlr.de/problems/versions.cannot-delete-only";
  static final String PROBLEM_TYPE_KIND_UNSUPPORTED = "https://shepard.dlr.de/problems/versions.kind.unsupported";

  @Inject
  PublishableKindRegistry kindRegistry;

  @Inject
  EntityVersionService versionService;

  @Inject
  PermissionsService permissionsService;

  @Inject
  EntityIdResolver entityIdResolver;

  // ─── POST: create ────────────────────────────────────────────────────

  @POST
  @Operation(
    summary = "Create a new EntityVersion on a Collection or DataObject.",
    description = "Mint a new version row. The body is optional — both `label` and `note` are " +
    "nullable. When `label` is null the server suggests `v<n+1>` (e.g. v3 for the third version). " +
    "When supplied, the label must match `[a-zA-Z0-9][a-zA-Z0-9.\\-+]{0,63}` and not be purely " +
    "numeric. A new version inherits the previous version's ACL on creation; the operator can " +
    "flip per-version ACL afterwards via PATCH .../permissions."
  )
  @APIResponse(
    responseCode = "201",
    description = "Freshly-created EntityVersion.",
    content = @Content(schema = @Schema(implementation = EntityVersionIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write/Manage permission on the parent entity.")
  @APIResponse(
    responseCode = "400",
    description = "Label or note shape is invalid (RFC 7807 versions.label.invalid).",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemJson.class))
  )
  @APIResponse(
    responseCode = "404",
    description = "Unsupported `{kind}` segment or parent entity not found (RFC 7807).",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemJson.class))
  )
  @APIResponse(
    responseCode = "409",
    description = "Label collides with an existing version on this parent (RFC 7807).",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemJson.class))
  )
  public Response create(
    @Parameter(description = "Entity-kind URL segment ('data-objects' or 'collections').", required = true)
    @PathParam("kind") String kind,
    @Parameter(description = "AppId of the parent entity.", required = true) @PathParam("appId") String appId,
    EntityVersionCreateIO body,
    @Context SecurityContext securityContext
  ) {
    String caller = callerOrNull(securityContext);
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();

    Optional<PublishableKind> kindOpt = resolveKind(kind);
    if (kindOpt.isEmpty()) return kindUnsupportedProblem(kind);
    String singularKind = singularKindOf(kindOpt.get());

    long parentOgmId;
    try {
      parentOgmId = entityIdResolver.resolveLong(appId);
    } catch (NotFoundException nfe) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    if (!permissionsService.isAccessTypeAllowedForUser(parentOgmId, AccessType.Write, caller)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    EntityVersionCreateIO io = body == null ? EntityVersionCreateIO.empty() : body;
    try {
      EntityVersion saved = versionService.createVersion(singularKind, appId, io.label(), io.note(), caller);
      return Response.status(Response.Status.CREATED).entity(EntityVersionIO.from(saved)).build();
    } catch (EntityVersionException eve) {
      return projectExceptionToResponse(eve);
    }
  }

  // ─── GET: list ──────────────────────────────────────────────────────

  @GET
  @Operation(
    summary = "List every EntityVersion on a Collection or DataObject visible to the caller.",
    description = "Returns versions ordered by `versionOrdinal` DESC (newest-first). Per-version " +
    "ACL filters the response — only rows where the caller has at least Reader appear."
  )
  @APIResponse(
    responseCode = "200",
    description = "List of versions (filtered by per-version ACL).",
    content = @Content(schema = @Schema(implementation = EntityVersionListIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(
    responseCode = "404",
    description = "Unsupported `{kind}` segment (RFC 7807).",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemJson.class))
  )
  public Response list(
    @PathParam("kind") String kind,
    @PathParam("appId") String appId,
    @Context SecurityContext securityContext
  ) {
    String caller = callerOrNull(securityContext);
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();

    Optional<PublishableKind> kindOpt = resolveKind(kind);
    if (kindOpt.isEmpty()) return kindUnsupportedProblem(kind);
    String singularKind = singularKindOf(kindOpt.get());

    try {
      List<EntityVersion> visible = versionService.listVersions(singularKind, appId, caller);
      List<EntityVersionIO> rows = new ArrayList<>(visible.size());
      for (EntityVersion v : visible) rows.add(EntityVersionIO.from(v));
      return Response.ok(new EntityVersionListIO(rows)).build();
    } catch (EntityVersionException eve) {
      return projectExceptionToResponse(eve);
    }
  }

  // ─── GET: single version ─────────────────────────────────────────────

  @GET
  @Path("/{label}")
  @Operation(
    summary = "Fetch a single EntityVersion by label.",
    description = "404 when missing; 403 when the caller lacks Reader on the version's ACL."
  )
  @APIResponse(
    responseCode = "200",
    description = "The version row.",
    content = @Content(schema = @Schema(implementation = EntityVersionIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Reader on the version's ACL.")
  @APIResponse(
    responseCode = "404",
    description = "Version not found (RFC 7807).",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemJson.class))
  )
  public Response getOne(
    @PathParam("kind") String kind,
    @PathParam("appId") String appId,
    @PathParam("label") String label,
    @Context SecurityContext securityContext
  ) {
    String caller = callerOrNull(securityContext);
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();

    Optional<PublishableKind> kindOpt = resolveKind(kind);
    if (kindOpt.isEmpty()) return kindUnsupportedProblem(kind);
    String singularKind = singularKindOf(kindOpt.get());

    try {
      EntityVersion v = versionService.getVersion(singularKind, appId, label, caller);
      return Response.ok(EntityVersionIO.from(v)).build();
    } catch (EntityVersionException eve) {
      return projectExceptionToResponse(eve);
    }
  }

  // ─── PATCH: per-version permissions ──────────────────────────────────

  @PATCH
  @Path("/{label}/permissions")
  @Operation(
    summary = "Flip the per-version ACL on an EntityVersion.",
    description = "Caller must hold Manage on the version's ACL. Ownership transfer (changing the " +
    "`owner` field) requires being the current owner. RFC 7807 problem envelopes for the failure " +
    "modes. PROV1a captures this PATCH as an :Activity row automatically."
  )
  @APIResponse(
    responseCode = "200",
    description = "Updated permissions of the version.",
    content = @Content(schema = @Schema(implementation = PermissionsIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Manage on the version's ACL.")
  @APIResponse(
    responseCode = "404",
    description = "Version not found (RFC 7807).",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemJson.class))
  )
  public Response patchPermissions(
    @PathParam("kind") String kind,
    @PathParam("appId") String appId,
    @PathParam("label") String label,
    PermissionsIO body,
    @Context SecurityContext securityContext
  ) {
    String caller = callerOrNull(securityContext);
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();

    Optional<PublishableKind> kindOpt = resolveKind(kind);
    if (kindOpt.isEmpty()) return kindUnsupportedProblem(kind);
    String singularKind = singularKindOf(kindOpt.get());

    try {
      Permissions updated = versionService.patchVersionPermissions(singularKind, appId, label, body, caller);
      return Response.ok(new PermissionsIO(updated)).build();
    } catch (EntityVersionException eve) {
      return projectExceptionToResponse(eve);
    }
  }

  // ─── DELETE ──────────────────────────────────────────────────────────

  @DELETE
  @Path("/{label}")
  @Operation(
    summary = "Delete an EntityVersion.",
    description = "Caller must hold Manage on the version's ACL. Refuses to remove the last " +
    "remaining version (409 versions.cannot-delete-only). The version's :Permissions node is " +
    "deleted alongside the version."
  )
  @APIResponse(responseCode = "204", description = "Version deleted.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Manage on the version's ACL.")
  @APIResponse(
    responseCode = "404",
    description = "Version not found (RFC 7807).",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemJson.class))
  )
  @APIResponse(
    responseCode = "409",
    description = "Last remaining version cannot be deleted (RFC 7807).",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemJson.class))
  )
  public Response delete(
    @PathParam("kind") String kind,
    @PathParam("appId") String appId,
    @PathParam("label") String label,
    @Context SecurityContext securityContext
  ) {
    String caller = callerOrNull(securityContext);
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();

    Optional<PublishableKind> kindOpt = resolveKind(kind);
    if (kindOpt.isEmpty()) return kindUnsupportedProblem(kind);
    String singularKind = singularKindOf(kindOpt.get());

    try {
      versionService.deleteVersion(singularKind, appId, label, caller);
      return Response.noContent().build();
    } catch (EntityVersionException eve) {
      return projectExceptionToResponse(eve);
    }
  }

  // ─── helpers ─────────────────────────────────────────────────────────

  /**
   * Resolve a URL-segment to its singular kind ({@code "data-objects"} →
   * {@code "data-object"}; {@code "collections"} → {@code "collection"}).
   * Mirrors {@link EntityVersionService#resolveKindFromSegment(String)};
   * placed here too so the REST layer doesn't reach into the service's
   * internals.
   */
  static String singularKindOf(PublishableKind kind) {
    return switch (kind) {
      case DATA_OBJECTS -> "data-object";
      case COLLECTIONS -> "collection";
    };
  }

  Optional<PublishableKind> resolveKind(String urlSegment) {
    return kindRegistry.bySegment(urlSegment);
  }

  private static String callerOrNull(SecurityContext sc) {
    if (sc == null || sc.getUserPrincipal() == null) return null;
    String name = sc.getUserPrincipal().getName();
    return name == null || name.isBlank() ? null : name;
  }

  private Response kindUnsupportedProblem(String kind) {
    String supported = String.join(", ", kindRegistry.supportedSegments());
    return problem(
      Response.Status.NOT_FOUND,
      PROBLEM_TYPE_KIND_UNSUPPORTED,
      "Unsupported entity-version kind",
      "No version-capable kind matches URL segment '" + kind + "'. Supported: " + supported + "."
    );
  }

  Response projectExceptionToResponse(EntityVersionException eve) {
    return switch (eve.reason()) {
      case NOT_FOUND -> problem(Response.Status.NOT_FOUND, PROBLEM_TYPE_NOT_FOUND, "Version not found", eve.getMessage());
      case LABEL_DUPLICATE -> problem(
        Response.Status.CONFLICT,
        PROBLEM_TYPE_LABEL_DUPLICATE,
        "Version label already exists",
        eve.getMessage()
      );
      case LABEL_INVALID -> problem(
        Response.Status.BAD_REQUEST,
        PROBLEM_TYPE_LABEL_INVALID,
        "Version label has invalid shape",
        eve.getMessage()
      );
      case CANNOT_DELETE_ONLY -> problem(
        Response.Status.CONFLICT,
        PROBLEM_TYPE_CANNOT_DELETE_ONLY,
        "Cannot delete the only remaining version",
        eve.getMessage()
      );
      case KIND_UNSUPPORTED -> problem(
        Response.Status.NOT_FOUND,
        PROBLEM_TYPE_KIND_UNSUPPORTED,
        "Unsupported entity-version kind",
        eve.getMessage()
      );
      case FORBIDDEN -> Response.status(Response.Status.FORBIDDEN).build();
    };
  }

  private static Response problem(Response.Status status, String type, String title, String detail) {
    ProblemJson envelope = new ProblemJson(type, title, status.getStatusCode(), detail, null);
    return Response.status(status).type("application/problem+json").entity(envelope).build();
  }
}
