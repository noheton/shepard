package de.dlr.shepard.v2.git.resources;

import com.fasterxml.jackson.databind.JsonNode;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.users.services.GitCredentialService;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.git.adapters.GitAdapter;
import de.dlr.shepard.context.references.git.adapters.GitAdapterException;
import de.dlr.shepard.context.references.git.adapters.GitAdapterRegistry;
import de.dlr.shepard.context.references.git.adapters.ParsedRepoUrl;
import de.dlr.shepard.context.references.git.daos.GitReferenceDAO;
import de.dlr.shepard.context.references.git.entities.GitReference;
import de.dlr.shepard.context.references.git.entities.GitReferenceMode;
import de.dlr.shepard.context.references.git.io.GitArtifactPreviewIO;
import de.dlr.shepard.context.references.git.io.GitReferenceIO;
import de.dlr.shepard.context.references.git.services.GitReferenceService;
import java.util.Optional;
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
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * {@code /v2/data-objects/{appId}/git-references} REST surface for
 * the G1a mode-(a) loose-link slice per {@code aidocs/38 §3}. Auth
 * piggybacks on DataObject permissions: Read to list / get, Write
 * to create, Write to delete.
 *
 * <p>{@code appId} routing follows the {@code /v2/} convention
 * ({@code aidocs/25 L2d}). 401 unauthenticated; 404 when the
 * DataObject or GitReference doesn't exist; 403 on permission
 * denied.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/data-objects/{dataObjectAppId}/git-references")
@RequestScoped
@Tag(name = "Git references (v2)")
public class GitReferenceRest {

  @Inject
  GitReferenceDAO gitReferenceDAO;

  @Inject
  DataObjectDAO dataObjectDAO;

  @Inject
  PermissionsService permissionsService;

  @Inject
  EntityIdResolver entityIdResolver;

  @Inject
  GitReferenceService gitReferenceService;

  @Inject
  GitCredentialService gitCredentialService;

  @Inject
  GitAdapterRegistry gitAdapterRegistry;

  @GET
  @Operation(summary = "List GitReferences hanging off a DataObject.")
  @APIResponse(
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = GitReferenceIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the DataObject.")
  @APIResponse(responseCode = "404", description = "No DataObject with that appId.")
  public Response list(
    @PathParam("dataObjectAppId") String dataObjectAppId,
    @Context SecurityContext securityContext
  ) {
    var gate = checkAccess(dataObjectAppId, AccessType.Read, securityContext);
    if (gate != null) return gate;
    List<GitReferenceIO> rows = gitReferenceDAO
      .findByDataObjectAppId(dataObjectAppId)
      .stream()
      .map(GitReferenceIO::new)
      .toList();
    return Response.ok(rows).build();
  }

  @POST
  @Operation(summary = "Create a new mode-(a) GitReference on a DataObject.")
  @APIResponse(
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = GitReferenceIO.class))
  )
  @APIResponse(responseCode = "400", description = "repoUrl missing or blank.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the DataObject.")
  @APIResponse(responseCode = "404", description = "No DataObject with that appId.")
  public Response create(
    @PathParam("dataObjectAppId") String dataObjectAppId,
    GitReferenceIO body,
    @Context SecurityContext securityContext
  ) {
    if (body == null || body.getRepoUrl() == null || body.getRepoUrl().isBlank()) {
      return Response.status(Response.Status.BAD_REQUEST).entity("repoUrl is required and must be non-blank").build();
    }
    var gate = checkAccess(dataObjectAppId, AccessType.Write, securityContext);
    if (gate != null) return gate;

    DataObject parent;
    try {
      parent = dataObjectDAO.findByNeo4jId(entityIdResolver.resolveLong(dataObjectAppId));
    } catch (NotFoundException nfe) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    if (parent == null) return Response.status(Response.Status.NOT_FOUND).build();

    GitReference gr = new GitReference(body.getRepoUrl(), body.getRef(), body.getPath());
    GitReferenceMode mode = body.getMode() == null ? GitReferenceMode.LOOSE_LINK : body.getMode();
    // TRACKED_ARTIFACT requires both ref and path so the adapter has
    // enough to fetch a single file. LOOSE_LINK has no such requirement.
    if (mode == GitReferenceMode.TRACKED_ARTIFACT) {
      if (body.getRef() == null || body.getRef().isBlank() ||
          body.getPath() == null || body.getPath().isBlank()) {
        return Response
          .status(Response.Status.BAD_REQUEST)
          .entity("TRACKED_ARTIFACT mode requires non-blank `ref` and `path`")
          .build();
      }
    }
    gr.setMode(mode);
    // PINNED_SNAPSHOT: resolve ref → SHA immediately so the snapshot is immutable from creation.
    if (mode == GitReferenceMode.PINNED_SNAPSHOT) {
      if (body.getRef() == null || body.getRef().isBlank()) {
        return Response.status(Response.Status.BAD_REQUEST)
          .entity("PINNED_SNAPSHOT mode requires a non-blank `ref` to resolve to a SHA").build();
      }
      Response snapErr = applyPinnedSnapshot(gr, body.getRef(), securityContext.getUserPrincipal().getName());
      if (snapErr != null) return snapErr;
    }
    gr.setDataObject(parent);
    GitReference saved = gitReferenceDAO.createOrUpdate(gr);
    return Response.status(Response.Status.CREATED).entity(new GitReferenceIO(saved)).build();
  }

  @GET
  @Path("/{appId}")
  @Operation(summary = "Read a single GitReference by appId.")
  @APIResponse(
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = GitReferenceIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No DataObject or GitReference with those appIds.")
  public Response read(
    @PathParam("dataObjectAppId") String dataObjectAppId,
    @PathParam("appId") String gitReferenceAppId,
    @Context SecurityContext securityContext
  ) {
    var gate = checkAccess(dataObjectAppId, AccessType.Read, securityContext);
    if (gate != null) return gate;
    GitReference gr = gitReferenceDAO.findByAppId(gitReferenceAppId);
    if (gr == null || gr.getDataObject() == null || !dataObjectAppId.equals(gr.getDataObject().getAppId())) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    return Response.ok(new GitReferenceIO(gr)).build();
  }

  @DELETE
  @Path("/{appId}")
  @Operation(summary = "Delete a GitReference.")
  @APIResponse(responseCode = "204", description = "Deleted.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No DataObject or GitReference with those appIds.")
  public Response delete(
    @PathParam("dataObjectAppId") String dataObjectAppId,
    @PathParam("appId") String gitReferenceAppId,
    @Context SecurityContext securityContext
  ) {
    var gate = checkAccess(dataObjectAppId, AccessType.Write, securityContext);
    if (gate != null) return gate;
    GitReference gr = gitReferenceDAO.findByAppId(gitReferenceAppId);
    if (gr == null || gr.getDataObject() == null || !dataObjectAppId.equals(gr.getDataObject().getAppId())) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    gitReferenceDAO.deleteByNeo4jId(gr.getId());
    return Response.noContent().build();
  }

  @PATCH
  @Path("/{appId}")
  @Consumes({ "application/merge-patch+json", MediaType.APPLICATION_JSON })
  @Operation(
    summary = "Partially update a GitReference (mode-a + mode-b fields).",
    description = "RFC 7396 JSON Merge Patch. Accepted fields: repoUrl, ref, path, mode. " +
    "Fields absent from the body are preserved; explicit JSON null clears the field " +
    "(except repoUrl — null or blank repoUrl is rejected with 400). " +
    "Returns 200 + the updated GitReferenceIO. Requires Write permission on the parent DataObject. " +
    "Switching to TRACKED_ARTIFACT requires non-blank `ref` and `path` (existing values or newly-set in the same PATCH)."
  )
  @APIResponse(
    responseCode = "200",
    description = "Updated.",
    content = @Content(schema = @Schema(implementation = GitReferenceIO.class))
  )
  @APIResponse(responseCode = "400", description = "repoUrl null or blank after patch.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No DataObject or GitReference with those appIds.")
  public Response patch(
    @PathParam("dataObjectAppId") String dataObjectAppId,
    @PathParam("appId") String gitReferenceAppId,
    @RequestBody(
      required = true,
      description = "Partial GitReference (RFC 7396). repoUrl, ref, path are patchable; read-only fields are ignored.",
      content = @Content(
        mediaType = "application/merge-patch+json",
        schema = @Schema(implementation = GitReferenceIO.class)
      )
    ) JsonNode body,
    @Context SecurityContext securityContext
  ) {
    if (body == null || !body.isObject()) {
      return Response.status(Response.Status.BAD_REQUEST).entity("PATCH body must be a JSON object").build();
    }
    var gate = checkAccess(dataObjectAppId, AccessType.Write, securityContext);
    if (gate != null) return gate;

    GitReference gr = gitReferenceDAO.findByAppId(gitReferenceAppId);
    if (gr == null || gr.getDataObject() == null || !dataObjectAppId.equals(gr.getDataObject().getAppId())) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    // Apply merge-patch: only present fields are updated; absent fields are preserved.
    if (body.has("repoUrl")) {
      JsonNode node = body.get("repoUrl");
      if (node.isNull() || (node.isTextual() && node.asText().isBlank())) {
        return Response.status(Response.Status.BAD_REQUEST).entity("repoUrl must not be null or blank").build();
      }
      gr.setRepoUrl(node.asText());
    }
    if (body.has("ref")) {
      JsonNode node = body.get("ref");
      gr.setRef(node.isNull() ? null : node.asText());
    }
    if (body.has("path")) {
      JsonNode node = body.get("path");
      gr.setPath(node.isNull() ? null : node.asText());
    }
    if (body.has("mode")) {
      JsonNode node = body.get("mode");
      if (node.isNull()) {
        gr.setMode(GitReferenceMode.LOOSE_LINK);
      } else {
        try {
          gr.setMode(GitReferenceMode.valueOf(node.asText()));
        } catch (IllegalArgumentException iae) {
          return Response.status(Response.Status.BAD_REQUEST)
            .entity("mode must be one of LOOSE_LINK, TRACKED_ARTIFACT, PINNED_SNAPSHOT")
            .build();
        }
      }
    }
    // Cross-field invariant: TRACKED_ARTIFACT needs ref + path.
    if (gr.getMode() == GitReferenceMode.TRACKED_ARTIFACT) {
      if (gr.getRef() == null || gr.getRef().isBlank() ||
          gr.getPath() == null || gr.getPath().isBlank()) {
        return Response.status(Response.Status.BAD_REQUEST)
          .entity("TRACKED_ARTIFACT mode requires non-blank `ref` and `path`")
          .build();
      }
    }
    // Cross-field invariant: PINNED_SNAPSHOT — resolve to SHA if not already pinned.
    // sha is server-managed; reject explicit client-side writes.
    if (body.has("sha")) {
      return Response.status(Response.Status.BAD_REQUEST)
        .entity("`sha` is server-managed for PINNED_SNAPSHOT and cannot be set directly").build();
    }
    if (gr.getMode() == GitReferenceMode.PINNED_SNAPSHOT && gr.getSha() == null) {
      String ref = gr.getRef();
      if (ref == null || ref.isBlank()) {
        return Response.status(Response.Status.BAD_REQUEST)
          .entity("PINNED_SNAPSHOT mode requires a non-blank `ref` to resolve to a SHA").build();
      }
      Response snapErr = applyPinnedSnapshot(gr, ref, securityContext.getUserPrincipal().getName());
      if (snapErr != null) return snapErr;
    }

    GitReference saved = gitReferenceDAO.createOrUpdate(gr);
    return Response.ok(new GitReferenceIO(saved)).build();
  }

  @GET
  @Path("/{appId}/preview")
  @Operation(
    summary = "Server-side inline preview of a tracked-artifact GitReference (G1b).",
    description = "Resolves the GitReference, picks the caller's PAT for the matching git host (via " +
    "G1-cred), routes through the per-host GitAdapter, and returns the file content (UTF-8) up to " +
    "shepard.git.preview.max-bytes (default 1 MB). All non-fatal failure modes are reported as " +
    "200 with `available=false` + a `reason` discriminator so the UI can render the explanation " +
    "inline rather than as an error. Possible reasons: not-tracked, unsupported-host, no-credential, " +
    "invalid-repo-url, fetch-failed."
  )
  @APIResponse(
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = GitArtifactPreviewIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No DataObject or GitReference with those appIds.")
  @APIResponse(responseCode = "501", description = "Host has no adapter (e.g. non-GitLab host in v1).")
  public Response preview(
    @PathParam("dataObjectAppId") String dataObjectAppId,
    @PathParam("appId") String gitReferenceAppId,
    @Context SecurityContext securityContext
  ) {
    var gate = checkAccess(dataObjectAppId, AccessType.Read, securityContext);
    if (gate != null) return gate;

    GitReference gr = gitReferenceDAO.findByAppId(gitReferenceAppId);
    if (gr == null || gr.getDataObject() == null || !dataObjectAppId.equals(gr.getDataObject().getAppId())) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    String caller = securityContext.getUserPrincipal().getName();
    GitArtifactPreviewIO out = gitReferenceService.previewArtifact(gr, caller);
    // "unsupported-host" maps to 501 per the RFC 7807 contract in the spec
    // (`git.adapter.unsupported-host`). Other "available=false" reasons
    // are non-fatal — keep them at 200 so the UI can render the message.
    if (!out.isAvailable() && "unsupported-host".equals(out.getReason())) {
      var problem = new java.util.LinkedHashMap<String, Object>();
      problem.put("type", "https://shepard.dlr.de/problems/git.adapter.unsupported-host");
      problem.put("title", "No GitAdapter is registered for this host.");
      problem.put("status", 501);
      problem.put("detail", "v1 ships a GitLab adapter only; GitHub and Gitea ship in G1d.");
      return Response.status(Response.Status.NOT_IMPLEMENTED)
        .type("application/problem+json")
        .entity(problem)
        .build();
    }
    return Response.ok(out).build();
  }

  /**
   * Resolves {@code ref} to a commit SHA via the registered {@link GitAdapter} and the caller's
   * stored PAT, then freezes it on {@code gr}. Returns {@code null} on success; returns a
   * short-circuit {@link Response} (400 / 502) on any validation or adapter failure.
   *
   * <p>Called at both POST (create) and PATCH time so the SHA is always immutable from the
   * moment the PINNED_SNAPSHOT is first persisted.
   */
  private Response applyPinnedSnapshot(GitReference gr, String ref, String callerUsername) {
    ParsedRepoUrl parsed;
    try {
      parsed = ParsedRepoUrl.parse(gr.getRepoUrl());
    } catch (GitAdapterException e) {
      return Response.status(Response.Status.BAD_REQUEST)
        .entity("Invalid repoUrl: " + e.getMessage()).build();
    }
    Optional<GitAdapter> adapterOpt = gitAdapterRegistry.findByHost(parsed.host());
    if (adapterOpt.isEmpty()) {
      return Response.status(Response.Status.BAD_REQUEST)
        .entity("PINNED_SNAPSHOT: no git adapter registered for host " + parsed.host()).build();
    }
    Optional<String> patOpt = gitCredentialService.findPatForHost(callerUsername, parsed.host());
    if (patOpt.isEmpty()) {
      return Response.status(Response.Status.BAD_REQUEST)
        .entity(
          "PINNED_SNAPSHOT: no git credential found for host " + parsed.host() +
          " — add one via /v2/me/git-credentials"
        )
        .build();
    }
    String sha;
    try {
      sha = adapterOpt.get().resolveRef(gr.getRepoUrl(), ref, patOpt.get());
    } catch (GitAdapterException e) {
      return Response.status(502)
        .entity("PINNED_SNAPSHOT: unable to resolve ref to SHA: " + e.getMessage()).build();
    }
    gr.setSha(sha);
    gr.setResolvedSha(sha);
    gr.setResolvedAtMillis(System.currentTimeMillis());
    return null; // success
  }

  /**
   * Returns null when access is allowed, otherwise a short-circuit
   * Response (401 / 403 / 404) to abort with.
   */
  private Response checkAccess(String dataObjectAppId, AccessType accessType, SecurityContext securityContext) {
    String caller = securityContext.getUserPrincipal() != null ? securityContext.getUserPrincipal().getName() : null;
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();
    long ogmId;
    try {
      ogmId = entityIdResolver.resolveLong(dataObjectAppId);
    } catch (NotFoundException nfe) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    if (!permissionsService.isAccessTypeAllowedForUser(ogmId, accessType, caller)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    return null;
  }
}
