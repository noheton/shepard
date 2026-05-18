package de.dlr.shepard.context.references.basicreference.endpoints;

import de.dlr.shepard.common.filters.Subscribable;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.context.references.basicreference.io.BasicReferenceIO;
import de.dlr.shepard.context.references.basicreference.services.BasicReferenceService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.util.ArrayList;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(
  Constants.SHEPARD_API +
  "/" +
  Constants.COLLECTIONS +
  "/{" +
  Constants.COLLECTION_ID +
  "}/" +
  Constants.DATA_OBJECTS +
  "/{" +
  Constants.DATA_OBJECT_ID +
  "}/" +
  Constants.BASIC_REFERENCES
)
@RequestScoped
public class BasicReferenceRest {

  @Inject
  BasicReferenceService basicReferenceService;

  @GET
  @Tag(name = Constants.BASIC_REFERENCE)
  @Operation(
    summary = "List all references on a DataObject (any kind).",
    description =
      "Returns the union of all reference kinds — Timeseries, File, " +
      "StructuredData, URI, Git, DataObject, Collection — attached to the " +
      "DataObject. Use the kind-specific endpoints (e.g. /timeseriesReferences) " +
      "when you only want one kind. Pagination: 'page' / 'size'. " +
      "Requires Read on the parent DataObject."
  )
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = BasicReferenceIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  @Parameter(name = Constants.QP_NAME)
  @Parameter(name = Constants.QP_PAGE)
  @Parameter(name = Constants.QP_SIZE)
  @Parameter(name = Constants.QP_ORDER_BY_ATTRIBUTE)
  @Parameter(name = Constants.QP_ORDER_DESC)
  public Response getAllReferences(
    @PathParam(Constants.COLLECTION_ID) @NotNull @PositiveOrZero Long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) @NotNull @PositiveOrZero Long dataObjectId,
    @QueryParam(Constants.QP_NAME) String name,
    @QueryParam(Constants.QP_PAGE) @PositiveOrZero Integer page,
    @QueryParam(Constants.QP_SIZE) @Positive Integer size,
    @QueryParam(Constants.QP_ORDER_BY_ATTRIBUTE) BasicReferenceAttributes orderBy,
    @QueryParam(Constants.QP_ORDER_DESC) Boolean orderDesc
  ) {
    var params = new QueryParamHelper();
    if (name != null) params = params.withName(name);
    if (page != null && size != null) params = params.withPageAndSize(page, size);
    if (orderBy != null) params = params.withOrderByAttribute(orderBy, orderDesc);
    var references = basicReferenceService.getAllBasicReferences(collectionId, dataObjectId, params);
    var result = new ArrayList<BasicReferenceIO>(references.size());

    for (var ref : references) {
      result.add(new BasicReferenceIO(ref));
    }
    return Response.ok(result).build();
  }

  @GET
  @Path("/{" + Constants.BASIC_REFERENCE_ID + "}")
  @Tag(name = Constants.BASIC_REFERENCE)
  @Operation(
    summary = "Get any reference by id (polymorphic).",
    description =
      "Returns one reference identified by 'referenceId' regardless of kind. " +
      "The response is the common BasicReference shape (id, name, description, " +
      "attributes, createdBy/createdAt, …); kind-specific fields are returned " +
      "by the kind-specific GET endpoints. Requires Read on the parent DataObject."
  )
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = BasicReferenceIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  @Parameter(name = Constants.BASIC_REFERENCE_ID)
  public Response getBasicReference(
    @PathParam(Constants.COLLECTION_ID) @NotNull @PositiveOrZero Long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) @NotNull @PositiveOrZero Long dataObjectId,
    @PathParam(Constants.BASIC_REFERENCE_ID) @NotNull @PositiveOrZero Long referenceId
  ) {
    BasicReference basicReference = basicReferenceService.getReference(collectionId, dataObjectId, referenceId);
    return Response.ok(new BasicReferenceIO(basicReference)).build();
  }

  @DELETE
  @Path("/{" + Constants.BASIC_REFERENCE_ID + "}")
  @Subscribable
  @Tag(name = Constants.BASIC_REFERENCE)
  @Operation(
    summary = "Delete any reference by id (polymorphic).",
    description =
      "Soft-deletes the reference identified by 'referenceId' regardless of " +
      "kind. The payload it points at (container, URI, etc.) is NOT deleted " +
      "— only the reference edge. Returns 204 when removed (or already gone). " +
      "Requires Write on the parent DataObject."
  )
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  @Parameter(name = Constants.BASIC_REFERENCE_ID)
  public Response deleteBasicReference(
    @PathParam(Constants.COLLECTION_ID) @NotNull @PositiveOrZero Long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) @NotNull @PositiveOrZero Long dataObjectId,
    @PathParam(Constants.BASIC_REFERENCE_ID) @NotNull @PositiveOrZero Long basicReferenceId
  ) {
    basicReferenceService.deleteReference(collectionId, dataObjectId, basicReferenceId);
    return Response.status(Status.NO_CONTENT).build();
  }
}
