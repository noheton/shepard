package de.dlr.shepard.context.collection.endpoints;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.filters.Subscribable;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.collection.services.DataObjectService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
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
import java.util.Set;
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

  @Inject
  Validator validator;

  @Inject
  ObjectMapper objectMapper;

  @GET
  @Tag(name = Constants.DATA_OBJECT)
  @Operation(description = "Get all dataObjects")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = DataObjectIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "404", description = "not found")
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
    @PathParam(Constants.COLLECTION_ID) @NotNull @PositiveOrZero Long collectionId,
    @QueryParam(Constants.QP_NAME) String name,
    @QueryParam(Constants.QP_PAGE) @PositiveOrZero Integer page,
    @QueryParam(Constants.QP_SIZE) @Positive Integer size,
    @QueryParam(Constants.QP_PARENT_ID) Long parentId,
    @QueryParam(Constants.QP_PREDECESSOR_ID) Long predecessorId,
    @QueryParam(Constants.QP_SUCCESSOR_ID) Long successorId,
    @QueryParam(Constants.QP_ORDER_BY_ATTRIBUTE) DataObjectAttributes orderBy,
    @QueryParam(Constants.QP_ORDER_DESC) Boolean orderDesc,
    @QueryParam(Constants.VERSION_UID) @org.hibernate.validator.constraints.UUID String versionUID
  ) {
    UUID versionUUID = null;
    if (versionUID != null) {
      versionUUID = UUID.fromString(versionUID);
    }

    var paramsWithShepardIds = new QueryParamHelper();
    if (name != null) paramsWithShepardIds = paramsWithShepardIds.withName(name);
    if (page != null && size != null) paramsWithShepardIds = paramsWithShepardIds.withPageAndSize(page, size);
    if (parentId != null) paramsWithShepardIds = paramsWithShepardIds.withParentId(parentId);
    if (predecessorId != null) paramsWithShepardIds = paramsWithShepardIds.withPredecessorId(predecessorId);
    if (successorId != null) paramsWithShepardIds = paramsWithShepardIds.withSuccessorId(successorId);
    if (orderBy != null) paramsWithShepardIds = paramsWithShepardIds.withOrderByAttribute(orderBy, orderDesc);

    var dataObjects = dataObjectService.getAllDataObjectsByShepardIds(collectionId, paramsWithShepardIds, versionUUID);
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
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  @Parameter(name = Constants.VERSION_UID)
  public Response getDataObject(
    @PathParam(Constants.COLLECTION_ID) @NotNull @PositiveOrZero Long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) @NotNull @PositiveOrZero Long dataObjectId,
    @QueryParam(Constants.VERSION_UID) @org.hibernate.validator.constraints.UUID String versionUID
  ) {
    UUID versionUUID = null;
    if (versionUID != null) {
      versionUUID = UUID.fromString(versionUID);
    }

    DataObject dataObject = dataObjectService.getDataObject(collectionId, dataObjectId, versionUUID);
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
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.COLLECTION_ID)
  public Response createDataObject(
    @PathParam(Constants.COLLECTION_ID) @NotNull @PositiveOrZero Long collectionId,
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
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  public Response updateDataObject(
    @PathParam(Constants.COLLECTION_ID) @NotNull @PositiveOrZero Long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) @NotNull @PositiveOrZero Long dataObjectId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = DataObjectIO.class))
    ) @Valid DataObjectIO dataObject
  ) {
    DataObject updatedDataObject = dataObjectService.updateDataObject(collectionId, dataObjectId, dataObject);
    return Response.ok(new DataObjectIO(updatedDataObject)).build();
  }

  /**
   * P21 - partial-update primitive on DataObject per strategy (c) in
   * backlog P21 (see aidocs/16-dispatcher-backlog.md and aidocs/26-crud-consistency.md
   * finding #1). Ships PATCH additively in /v1/ alongside the existing PUT, which
   * keeps its full-replace semantics unchanged for backwards compatibility. Uses
   * RFC 7396 JSON Merge Patch: missing top-level fields are preserved, present
   * fields are replaced, explicit JSON null clears the field. Permission check
   * matches PUT (WRITE on the data object); Bean Validation runs against the merged
   * result, not the partial input. Mirrors the P21 Collection pilot exactly.
   */
  @PATCH
  @Path("/{" + Constants.DATA_OBJECT_ID + "}")
  @Consumes({ Constants.APPLICATION_MERGE_PATCH_JSON, MediaType.APPLICATION_JSON })
  @Subscribable
  @Tag(name = Constants.DATA_OBJECT)
  @Operation(
    summary = "Partially update dataObject",
    description = "Applies an RFC 7396 JSON Merge Patch to the data object. The request body is a partial " +
    "DataObject: fields present in the body replace the corresponding fields on the entity, fields absent " +
    "from the body are left unchanged, and explicit JSON null clears the field. The merged result is then " +
    "Bean-Validated; constraint violations on the final state return 400. Returns the full updated entity. " +
    "Accepts both application/merge-patch+json (preferred, per RFC 7396) and application/json in /v1/; " +
    "future /v2/ APIs will require application/merge-patch+json."
  )
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = DataObjectIO.class))
  )
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  public Response patchDataObject(
    @PathParam(Constants.COLLECTION_ID) @NotNull @PositiveOrZero Long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) @NotNull @PositiveOrZero Long dataObjectId,
    @RequestBody(
      required = true,
      description = "Partial DataObject (RFC 7396). Every field is optional; absent fields are preserved.",
      content = @Content(
        mediaType = Constants.APPLICATION_MERGE_PATCH_JSON,
        schema = @Schema(implementation = DataObjectIO.class)
      )
    ) JsonNode patch
  ) {
    if (patch == null || !patch.isObject()) {
      throw new InvalidBodyException("PATCH body must be a JSON object (RFC 7396 JSON Merge Patch)");
    }

    DataObject existing = dataObjectService.getDataObject(collectionId, dataObjectId);
    DataObjectIO merged = new DataObjectIO(existing);
    try {
      objectMapper.readerForUpdating(merged).readValue(patch);
    } catch (JsonProcessingException e) {
      throw new InvalidBodyException("Invalid JSON Merge Patch body: %s".formatted(e.getOriginalMessage()));
    } catch (IOException e) {
      throw new InvalidBodyException("Could not read JSON Merge Patch body: %s".formatted(e.getMessage()));
    }

    Set<ConstraintViolation<DataObjectIO>> violations = validator.validate(merged);
    if (!violations.isEmpty()) {
      throw new ConstraintViolationException(violations);
    }

    DataObject updatedDataObject = dataObjectService.updateDataObject(collectionId, dataObjectId, merged);
    return Response.ok(new DataObjectIO(updatedDataObject)).build();
  }

  @DELETE
  @Path("/{" + Constants.DATA_OBJECT_ID + "}")
  @Subscribable
  @Tag(name = Constants.DATA_OBJECT)
  @Operation(description = "Delete dataObject")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  public Response deleteDataObject(
    @PathParam(Constants.COLLECTION_ID) @NotNull @PositiveOrZero Long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) @NotNull @PositiveOrZero Long dataObjectId
  ) {
    dataObjectService.deleteDataObject(collectionId, dataObjectId);
    return Response.status(Status.NO_CONTENT).build();
  }
}
