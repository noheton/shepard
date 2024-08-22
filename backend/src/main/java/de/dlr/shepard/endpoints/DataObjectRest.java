package de.dlr.shepard.endpoints;

import de.dlr.shepard.filters.Subscribable;
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.io.DataObjectIO;
import de.dlr.shepard.neo4Core.orderBy.DataObjectAttributes;
import de.dlr.shepard.neo4Core.services.DataObjectService;
import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.QueryParamHelper;
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
@Path(Constants.COLLECTIONS + "/{" + Constants.COLLECTION_ID + "}/" + Constants.DATAOBJECTS)
@RequestScoped
public class DataObjectRest {

  private DataObjectService dataObjectService;

  @Context
  private SecurityContext securityContext;

  DataObjectRest() {}

  @Inject
  public DataObjectRest(DataObjectService dataObjectService) {
    this.dataObjectService = dataObjectService;
  }

  @GET
  @Tag(name = Constants.DATAOBJECT)
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
  public Response getAllDataObjects(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @QueryParam(Constants.QP_NAME) String name,
    @QueryParam(Constants.QP_PAGE) Integer page,
    @QueryParam(Constants.QP_SIZE) Integer size,
    @QueryParam(Constants.QP_PARENT_ID) Long parentId,
    @QueryParam(Constants.QP_PREDECESSOR_ID) Long predecessorId,
    @QueryParam(Constants.QP_SUCCESSOR_ID) Long successorId,
    @QueryParam(Constants.QP_ORDER_BY_ATTRIBUTE) DataObjectAttributes orderBy,
    @QueryParam(Constants.QP_ORDER_DESC) Boolean orderDesc
  ) {
    var paramsWithShepardIds = new QueryParamHelper();
    if (name != null) paramsWithShepardIds = paramsWithShepardIds.withName(name);
    if (page != null && size != null) paramsWithShepardIds = paramsWithShepardIds.withPageAndSize(page, size);
    if (parentId != null) paramsWithShepardIds = paramsWithShepardIds.withParentId(parentId);
    if (predecessorId != null) paramsWithShepardIds = paramsWithShepardIds.withPredecessorId(predecessorId);
    if (successorId != null) paramsWithShepardIds = paramsWithShepardIds.withSuccessorId(successorId);
    if (orderBy != null) paramsWithShepardIds = paramsWithShepardIds.withOrderByAttribute(orderBy, orderDesc);

    var dataObjects = dataObjectService.getAllDataObjectsByShepardIds(collectionId, paramsWithShepardIds);
    var result = new ArrayList<DataObjectIO>(dataObjects.size());

    for (var dataObject : dataObjects) {
      result.add(new DataObjectIO(dataObject));
    }
    return Response.ok(result).build();
  }

  @GET
  @Path("/{" + Constants.DATAOBJECT_ID + "}")
  @Tag(name = Constants.DATAOBJECT)
  @Operation(description = "Get dataObject")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = DataObjectIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATAOBJECT_ID)
  public Response getDataObject(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATAOBJECT_ID) long dataObjectId
  ) {
    DataObject dataObject = dataObjectService.getDataObjectByShepardId(dataObjectId);
    return Response.ok(new DataObjectIO(dataObject)).build();
  }

  @POST
  @Subscribable
  @Tag(name = Constants.DATAOBJECT)
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
    DataObject newDataObject = dataObjectService.createDataObjectByCollectionShepardId(
      collectionId,
      dataObject,
      securityContext.getUserPrincipal().getName()
    );
    return Response.ok(new DataObjectIO(newDataObject)).status(Status.CREATED).build();
  }

  @PUT
  @Path("/{" + Constants.DATAOBJECT_ID + "}")
  @Subscribable
  @Tag(name = Constants.DATAOBJECT)
  @Operation(description = "Update dataObject")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = DataObjectIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATAOBJECT_ID)
  public Response updateDataObject(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = DataObjectIO.class))
    ) @Valid DataObjectIO dataObject
  ) {
    DataObject updatedDataObject = dataObjectService.updateDataObjectByShepardId(
      dataObjectId,
      dataObject,
      securityContext.getUserPrincipal().getName()
    );
    if (updatedDataObject == null) {
      return Response.status(Status.NOT_FOUND).build();
    }
    return Response.ok(new DataObjectIO(updatedDataObject)).build();
  }

  @DELETE
  @Path("/{" + Constants.DATAOBJECT_ID + "}")
  @Subscribable
  @Tag(name = Constants.DATAOBJECT)
  @Operation(description = "Delete dataObject")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATAOBJECT_ID)
  public Response deleteDataObject(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATAOBJECT_ID) long dataObjectId
  ) {
    return dataObjectService.deleteDataObjectByShepardId(dataObjectId, securityContext.getUserPrincipal().getName())
      ? Response.status(Status.NO_CONTENT).build()
      : Response.status(Status.INTERNAL_SERVER_ERROR).build();
  }
}
