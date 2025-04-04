package de.dlr.shepard.context.collection.endpoints;

import de.dlr.shepard.common.filters.Subscribable;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.collection.services.DataObjectService;
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

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(Constants.COLLECTIONS + "/{" + Constants.COLLECTION_ID + "}/" + Constants.DATA_OBJECTS)
@RequestScoped
public class DataObjectRest {

  @Inject
  DataObjectService dataObjectService;

  @Context
  private SecurityContext securityContext;

  @GET
  @Tag(name = Constants.DATA_OBJECT)
  @Operation(description = "Get all dataObjects")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = DataObjectIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.QP_NAME)
  @Parameter(name = Constants.QP_PAGE)
  @Parameter(name = Constants.QP_SIZE)
  @Parameter(name = Constants.QP_PARENT_ID)
  @Parameter(name = Constants.QP_PREDECESSOR_ID)
  @Parameter(name = Constants.QP_SUCCESSOR_ID)
  @Parameter(name = Constants.QP_ORDER_BY_ATTRIBUTE)
  @Parameter(name = Constants.QP_ORDER_DESC)
  @Parameter(name = Constants.VERSION_UID)
  public Response getAllDataObjects(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @QueryParam(Constants.QP_NAME) String name,
    @QueryParam(Constants.QP_PAGE) Integer page,
    @QueryParam(Constants.QP_SIZE) Integer size,
    @QueryParam(Constants.QP_PARENT_ID) Long parentId,
    @QueryParam(Constants.QP_PREDECESSOR_ID) Long predecessorId,
    @QueryParam(Constants.QP_SUCCESSOR_ID) Long successorId,
    @QueryParam(Constants.QP_ORDER_BY_ATTRIBUTE) DataObjectAttributes orderBy,
    @QueryParam(Constants.QP_ORDER_DESC) Boolean orderDesc,
    @QueryParam(Constants.VERSION_UID) UUID versionUID
  ) {
    var paramsWithShepardIds = new QueryParamHelper();
    if (name != null) paramsWithShepardIds = paramsWithShepardIds.withName(name);
    if (page != null && size != null) paramsWithShepardIds = paramsWithShepardIds.withPageAndSize(page, size);
    if (parentId != null) paramsWithShepardIds = paramsWithShepardIds.withParentId(parentId);
    if (predecessorId != null) paramsWithShepardIds = paramsWithShepardIds.withPredecessorId(predecessorId);
    if (successorId != null) paramsWithShepardIds = paramsWithShepardIds.withSuccessorId(successorId);
    if (orderBy != null) paramsWithShepardIds = paramsWithShepardIds.withOrderByAttribute(orderBy, orderDesc);

    var dataObjects = dataObjectService.getAllDataObjectsByShepardIds(collectionId, paramsWithShepardIds, versionUID);
    var result = new ArrayList<DataObjectIO>(dataObjects.size());

    for (var dataObject : dataObjects) {
      result.add(new DataObjectIO(dataObject));
    }
    return Response.ok(result).build();
  }

  @GET
  @Path("/{" + Constants.DATA_OBJECT_ID + "}")
  @Tag(name = Constants.DATA_OBJECT)
  @Operation(description = "Get dataObject")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = DataObjectIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  @Parameter(name = Constants.VERSION_UID)
  public Response getDataObject(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) long dataObjectId,
    @QueryParam(Constants.VERSION_UID) UUID versionUID
  ) {
    DataObject dataObject = dataObjectService.getDataObject(collectionId, dataObjectId, versionUID);
    return Response.ok(new DataObjectIO(dataObject)).build();
  }

  @POST
  @Subscribable
  @Tag(name = Constants.DATA_OBJECT)
  @Operation(description = "Create a new dataObject")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = DataObjectIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  public Response createDataObject(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = DataObjectIO.class))
    ) @Valid DataObjectIO dataObject
  ) {
    DataObject newDataObject = dataObjectService.createDataObject(collectionId, dataObject);
    return Response.ok(new DataObjectIO(newDataObject)).status(Status.CREATED).build();
  }

  @PUT
  @Path("/{" + Constants.DATA_OBJECT_ID + "}")
  @Subscribable
  @Tag(name = Constants.DATA_OBJECT)
  @Operation(description = "Update dataObject")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = DataObjectIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  public Response updateDataObject(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) long dataObjectId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = DataObjectIO.class))
    ) @Valid DataObjectIO dataObject
  ) {
    DataObject updatedDataObject = dataObjectService.updateDataObject(collectionId, dataObjectId, dataObject);
    return Response.ok(new DataObjectIO(updatedDataObject)).build();
  }

  @DELETE
  @Path("/{" + Constants.DATA_OBJECT_ID + "}")
  @Subscribable
  @Tag(name = Constants.DATA_OBJECT)
  @Operation(description = "Delete dataObject")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  public Response deleteDataObject(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) long dataObjectId
  ) {
    dataObjectService.deleteDataObject(collectionId, dataObjectId);
    return Response.status(Status.NO_CONTENT).build();
  }
}
