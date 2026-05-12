package de.dlr.shepard.v2.git.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.git.daos.GitReferenceDAO;
import de.dlr.shepard.context.references.git.entities.GitReference;
import de.dlr.shepard.context.references.git.io.GitReferenceIO;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
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
