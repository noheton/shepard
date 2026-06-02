package de.dlr.shepard.v2.collection.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.v2.collection.daos.SubCollectionsDAO;
import de.dlr.shepard.v2.collection.io.SubCollectionEntryIO;
import de.dlr.shepard.v2.collection.io.SubCollectionsIO;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.ArrayList;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * PROJ-REST-1 — sub-collections query endpoint.
 *
 * <p>Route:
 * <pre>
 *   GET /v2/collections/{collectionAppId}/sub-collections
 * </pre>
 *
 * <p>Returns a {@link SubCollectionsIO} payload describing:
 * <ul>
 *   <li>Whether the parent Collection is a "project"
 *       ({@code parentIsProject = true} when it has a
 *       {@code urn:shepard:project = "true"} annotation).</li>
 *   <li>All programme names from {@code urn:shepard:programme} annotations
 *       on the parent ({@code programmes}).</li>
 *   <li>The list of sub-collections — every Collection in the system that
 *       carries a {@code urn:shepard:partOf = <parentAppId>} semantic
 *       annotation, in the default trimmed shape.</li>
 * </ul>
 *
 * <p><b>Trim vs full shape.</b> The default ("trim") response returns only
 * the fields in {@link SubCollectionEntryIO}. Supplying {@code ?include=full}
 * is reserved for a future slice (PROJ-REST-1b) that returns the full
 * {@link CollectionIO} shape for each child; in this initial slice the
 * parameter is accepted but ignored — the trim shape is always returned.
 *
 * <p><b>Auth.</b> Identical to {@code GET /v2/collections/{collectionAppId}}:
 * the caller must have Read permission on the parent Collection. The sub-
 * collections list is NOT permission-filtered in the initial slice (any child
 * visible to the Cypher traversal is included). A future permission-filter
 * slice can walk {@code isAccessTypeAllowedForUser} per child appId.
 *
 * <p><b>Spec.</b> {@code aidocs/integrations/121 §3.1}.
 */
@Path("/v2/collections/{collectionAppId}/sub-collections")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Authenticated
@Tag(name = "Collections — sub-collections (PROJ-REST-1)")
public class CollectionSubCollectionsRest {

  @Inject
  SubCollectionsDAO subCollectionsDAO;

  @Inject
  PermissionsService permissionsService;

  @Inject
  EntityIdResolver entityIdResolver;

  @Inject
  CollectionService collectionService;

  @GET
  @Operation(
    summary = "List sub-collections of a parent Collection (PROJ-REST-1).",
    description =
      "Returns a `SubCollectionsIO` shape containing:\n" +
      "  - `parentAppId` — echoes the path parameter.\n" +
      "  - `parentIsProject` — `true` when the parent has a " +
      "`urn:shepard:project = \"true\"` semantic annotation.\n" +
      "  - `programmes` — all `value` strings from `urn:shepard:programme` " +
      "annotations on the parent; empty array when none.\n" +
      "  - `subCollections` — Collections that carry a " +
      "`urn:shepard:partOf = <parentAppId>` semantic annotation, in the default " +
      "trimmed shape (appId, id, name, heroImage, doCount, lastActivity, " +
      "ownerGroup, alsoMemberOf). Empty array when no such child exists.\n\n" +
      "The `?include=full` query parameter is accepted but reserved for a " +
      "future slice (PROJ-REST-1b) — the trim shape is always returned in " +
      "this version.\n\n" +
      "`alsoMemberOf` on each child lists the appIds of OTHER parent projects " +
      "the child also belongs to (excluding this parent) — useful for rendering " +
      "a cross-project membership badge.\n\n" +
      "Auth: Read permission on the parent Collection. " +
      "`404` when the parent does not exist."
  )
  @APIResponse(
    responseCode = "200",
    description = "Sub-collections view for the parent Collection (subCollections may be empty).",
    content = @Content(schema = @Schema(implementation = SubCollectionsIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the Collection.")
  @APIResponse(responseCode = "404", description = "No Collection with that appId.")
  public Response get(
    @PathParam("collectionAppId") String collectionAppId,
    @QueryParam("include") @DefaultValue("trim") String include,
    @Context SecurityContext sc
  ) {
    // 401 — unauthenticated
    String caller = sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();

    // 404 — unknown appId
    long ogmId;
    try {
      ogmId = entityIdResolver.resolveLong(collectionAppId);
    } catch (NotFoundException nfe) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    // 403 — insufficient permission
    if (!permissionsService.isAccessTypeAllowedForUser(ogmId, AccessType.Read, caller, 0L)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    // Execute the Cypher traversal
    SubCollectionsIO result = subCollectionsDAO.findSubCollections(collectionAppId);
    if (result == null) {
      // This branch is a safety net; the EntityIdResolver check above already
      // verified the Collection exists, so this should not be reached in practice.
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    // ?include=full reserved for PROJ-REST-1b — trim shape is always returned
    // for now; suppress unused-variable lint by a no-op reference.
    if ("full".equalsIgnoreCase(include)) {
      // Future: resolve each child appId to a CollectionIO via collectionService.
      // For now: silently return the same trim shape (documented in Javadoc above).
      result.setSubCollections(result.getSubCollections() == null
        ? new ArrayList<>()
        : result.getSubCollections());
    }

    return Response.ok(result)
      .header("Cache-Control", "max-age=60, must-revalidate")
      .build();
  }
}
