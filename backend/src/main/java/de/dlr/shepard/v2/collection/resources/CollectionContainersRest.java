package de.dlr.shepard.v2.collection.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.v2.collection.daos.CollectionContainersDAO;
import de.dlr.shepard.v2.collection.io.ContainerSummaryIO;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
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
 * CC2 — list containers referenced within a Collection.
 *
 * <p>Route:
 * <ul>
 *   <li>{@code GET /v2/collections/{collectionAppId}/referenced-containers}
 *       — returns distinct containers reached via
 *       Collection → DataObject → Reference → Container.
 *       Requires Read permission on the Collection.</li>
 * </ul>
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/collections/{collectionAppId}/referenced-containers")
@RequestScoped
@Tag(name = "Collections — referenced containers (CC2)")
public class CollectionContainersRest {

  @Inject
  CollectionContainersDAO containersDAO;

  @Inject
  PermissionsService permissionsService;

  @Inject
  EntityIdResolver entityIdResolver;

  @GET
  @Operation(
    summary = "List containers referenced by data objects in this collection (CC2).",
    description =
      "Walks Collection → DataObject → Reference → Container and returns " +
      "one entry per distinct container. Returns an empty array when the collection " +
      "has no data objects or none of them reference a container.\n\n" +
      "Auth: Read permission on the Collection."
  )
  @APIResponse(
    responseCode = "200",
    description = "Distinct containers referenced within the collection (may be empty).",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = ContainerSummaryIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the Collection.")
  @APIResponse(responseCode = "404", description = "No Collection with that appId.")
  public Response list(
    @PathParam("collectionAppId") String collectionAppId,
    @Context SecurityContext sc
  ) {
    String caller = sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();

    long ogmId;
    try {
      ogmId = entityIdResolver.resolveLong(collectionAppId);
    } catch (NotFoundException nfe) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    if (!permissionsService.isAccessTypeAllowedForUser(ogmId, AccessType.Read, caller)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    List<ContainerSummaryIO> containers = containersDAO.findByCollectionAppId(collectionAppId);
    return Response.ok(containers).build();
  }
}
