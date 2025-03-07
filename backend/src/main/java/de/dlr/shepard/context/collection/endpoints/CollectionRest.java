package de.dlr.shepard.context.collection.endpoints;

import de.dlr.shepard.auth.permission.entities.Permissions;
import de.dlr.shepard.auth.permission.io.PermissionsIO;
import de.dlr.shepard.auth.permission.io.Roles;
import de.dlr.shepard.common.filters.Subscribable;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.context.export.ExportService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path(Constants.COLLECTIONS)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
public class CollectionRest {

  private CollectionService collectionService;
  private ExportService exportService;

  @Context
  private SecurityContext securityContext;

  CollectionRest() {}

  @Inject
  public CollectionRest(CollectionService collectionService, ExportService exportService) {
    this.collectionService = collectionService;
    this.exportService = exportService;
  }

  @GET
  @Tag(name = Constants.COLLECTION)
  @Operation(description = "Get all collections")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = CollectionIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.QP_NAME)
  @Parameter(name = Constants.QP_PAGE)
  @Parameter(name = Constants.QP_SIZE)
  @Parameter(name = Constants.QP_ORDER_BY_ATTRIBUTE)
  @Parameter(name = Constants.QP_ORDER_DESC)
  public Response getAllCollections(
    @QueryParam(Constants.QP_NAME) String name,
    @QueryParam(Constants.QP_PAGE) Integer page,
    @QueryParam(Constants.QP_SIZE) Integer size,
    @QueryParam(Constants.QP_ORDER_BY_ATTRIBUTE) DataObjectAttributes orderBy,
    @QueryParam(Constants.QP_ORDER_DESC) Boolean orderDesc
  ) {
    var params = new QueryParamHelper();
    if (name != null) params = params.withName(name);
    if (page != null && size != null) params = params.withPageAndSize(page, size);
    if (orderBy != null) params = params.withOrderByAttribute(orderBy, orderDesc);
    var collections = collectionService.getAllCollectionsByShepardId(
      params,
      securityContext.getUserPrincipal().getName()
    );

    var result = new ArrayList<CollectionIO>(collections.size());
    for (var collection : collections) {
      result.add(new CollectionIO(collection));
    }
    return Response.ok(result).build();
  }

  @GET
  @Path("/{" + Constants.COLLECTION_ID + "}")
  @Tag(name = Constants.COLLECTION)
  @Operation(description = "Get collection")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = CollectionIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.VERSION_UID)
  public Response getCollection(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @QueryParam(Constants.VERSION_UID) UUID versionUID
  ) {
    Collection collection = collectionService.getCollectionWithDataObjectsAndIncomingReferences(
      collectionId,
      versionUID
    );
    return Response.ok(new CollectionIO(collection)).build();
  }

  @PUT
  @Path("/{" + Constants.COLLECTION_ID + "}")
  @Subscribable
  @Tag(name = Constants.COLLECTION)
  @Operation(description = "Update collection")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = CollectionIO.class))
  )
  @Parameter(name = Constants.COLLECTION_ID)
  @APIResponse(description = "not found", responseCode = "404")
  public Response updateCollection(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = CollectionIO.class))
    ) @Valid CollectionIO collection
  ) {
    Collection updatedCollection = collectionService.updateCollectionByShepardId(
      collectionId,
      collection,
      securityContext.getUserPrincipal().getName()
    );
    return Response.ok(new CollectionIO(updatedCollection)).build();
  }

  @DELETE
  @Path("/{" + Constants.COLLECTION_ID + "}")
  @Subscribable
  @Tag(name = Constants.COLLECTION)
  @Operation(description = "Delete collection")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  public Response deleteCollection(@PathParam(Constants.COLLECTION_ID) long collectionId) {
    return collectionService.deleteCollectionByShepardId(collectionId, securityContext.getUserPrincipal().getName())
      ? Response.status(Status.NO_CONTENT).build()
      : Response.status(Status.INTERNAL_SERVER_ERROR).build();
  }

  @GET
  @Path("/{" + Constants.COLLECTION_ID + "}/" + Constants.PERMISSIONS)
  @Tag(name = Constants.COLLECTION)
  @Operation(description = "Get permissions")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = PermissionsIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  public Response getCollectionPermissions(@PathParam(Constants.COLLECTION_ID) long collectionId) {
    Permissions permissions = collectionService.getCollectionPermissions(
      collectionId,
      securityContext.getUserPrincipal().getName()
    );

    if (permissions == null) return Response.status(Status.NOT_FOUND).build();
    return Response.ok(new PermissionsIO(permissions)).build();
  }

  @PUT
  @Path("/{" + Constants.COLLECTION_ID + "}/" + Constants.PERMISSIONS)
  @Tag(name = Constants.COLLECTION)
  @Operation(description = "Edit permissions")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = PermissionsIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  public Response editCollectionPermissions(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = PermissionsIO.class))
    ) @Valid PermissionsIO newPermissions
  ) {
    Permissions updatedPermissions = collectionService.updateCollectionPermissions(
      newPermissions,
      collectionId,
      securityContext.getUserPrincipal().getName()
    );

    return Response.ok(new PermissionsIO(updatedPermissions)).build();
  }

  @GET
  @Path("/{" + Constants.COLLECTION_ID + "}/" + Constants.ROLES)
  @Tag(name = Constants.COLLECTION)
  @Operation(description = "Get roles")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = Roles.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  public Response getCollectionRoles(@PathParam(Constants.COLLECTION_ID) long collectionId) {
    Roles roles = collectionService.getCollectionRoles(collectionId, securityContext.getUserPrincipal().getName());
    return Response.ok(roles).build();
  }

  @POST
  @Tag(name = Constants.COLLECTION)
  @Operation(description = "Create a new collection")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = CollectionIO.class))
  )
  public Response createCollection(
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = CollectionIO.class))
    ) @Valid CollectionIO collection
  ) {
    Collection newCollection = collectionService.createCollection(
      collection,
      securityContext.getUserPrincipal().getName()
    );
    return Response.ok(new CollectionIO(newCollection)).status(Status.CREATED).build();
  }

  @GET
  @Path("/{" + Constants.COLLECTION_ID + "}/" + Constants.EXPORT)
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  @Tag(name = Constants.COLLECTION)
  @Operation(description = "Export Collection as RoCrate")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(
      mediaType = MediaType.APPLICATION_OCTET_STREAM,
      schema = @Schema(type = SchemaType.STRING, format = "binary")
    )
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  public Response exportCollection(@PathParam(Constants.COLLECTION_ID) long collectionId) throws IOException {
    var is = exportService.exportCollectionByShepardId(collectionId, securityContext.getUserPrincipal().getName());
    return is != null
      ? Response.ok(is, MediaType.APPLICATION_OCTET_STREAM)
        .header("Content-Disposition", "attachment; filename=\"export.zip\"")
        .build()
      : Response.status(Status.NOT_FOUND).build();
  }
}
