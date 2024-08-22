package de.dlr.shepard.endpoints;

import de.dlr.shepard.filters.Subscribable;
import de.dlr.shepard.neo4Core.entities.BasicReference;
import de.dlr.shepard.neo4Core.io.BasicReferenceIO;
import de.dlr.shepard.neo4Core.orderBy.BasicReferenceAttributes;
import de.dlr.shepard.neo4Core.services.BasicReferenceService;
import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.QueryParamHelper;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
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
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(
  Constants.COLLECTIONS +
  "/{" +
  Constants.COLLECTION_ID +
  "}/" +
  Constants.DATAOBJECTS +
  "/{" +
  Constants.DATAOBJECT_ID +
  "}/" +
  Constants.BASIC_REFERENCES
)
@RequestScoped
public class BasicReferenceRest {

  private BasicReferenceService basicReferenceService;

  @Context
  private SecurityContext securityContext;

  BasicReferenceRest() {}

  @Inject
  public BasicReferenceRest(BasicReferenceService basicReferenceService) {
    this.basicReferenceService = basicReferenceService;
  }

  @GET
  @Tag(name = Constants.BASIC_REFERENCE)
  @Operation(description = "Get all references")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = BasicReferenceIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATAOBJECT_ID)
  @Parameter(name = Constants.QP_NAME)
  @Parameter(name = Constants.QP_PAGE)
  @Parameter(name = Constants.QP_SIZE)
  @Parameter(name = Constants.QP_ORDER_BY_ATTRIBUTE)
  @Parameter(name = Constants.QP_ORDER_DESC)
  public Response getAllReferences(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
    @QueryParam(Constants.QP_NAME) String name,
    @QueryParam(Constants.QP_PAGE) Integer page,
    @QueryParam(Constants.QP_SIZE) Integer size,
    @QueryParam(Constants.QP_ORDER_BY_ATTRIBUTE) BasicReferenceAttributes orderBy,
    @QueryParam(Constants.QP_ORDER_DESC) Boolean orderDesc
  ) {
    var params = new QueryParamHelper();
    if (name != null) params = params.withName(name);
    if (page != null && size != null) params = params.withPageAndSize(page, size);
    if (orderBy != null) params = params.withOrderByAttribute(orderBy, orderDesc);
    var references = basicReferenceService.getAllBasicReferencesByDataObjectShepardId(dataObjectId, params);
    var result = new ArrayList<BasicReferenceIO>(references.size());

    for (var ref : references) {
      result.add(new BasicReferenceIO(ref));
    }
    return Response.ok(result).build();
  }

  @GET
  @Path("/{" + Constants.BASIC_REFERENCE_ID + "}")
  @Tag(name = Constants.BASIC_REFERENCE)
  @Operation(description = "Get reference")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = BasicReferenceIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATAOBJECT_ID)
  @Parameter(name = Constants.BASIC_REFERENCE_ID)
  public Response getBasicReference(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
    @PathParam(Constants.BASIC_REFERENCE_ID) long referenceId
  ) {
    BasicReference basicReference = basicReferenceService.getReferenceByShepardId(referenceId);
    return Response.ok(new BasicReferenceIO(basicReference)).build();
  }

  @DELETE
  @Path("/{" + Constants.BASIC_REFERENCE_ID + "}")
  @Subscribable
  @Tag(name = Constants.BASIC_REFERENCE)
  @Operation(description = "Delete reference")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATAOBJECT_ID)
  @Parameter(name = Constants.BASIC_REFERENCE_ID)
  public Response deleteBasicReference(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
    @PathParam(Constants.BASIC_REFERENCE_ID) long basicReferenceId
  ) {
    return basicReferenceService.deleteReferenceByShepardId(
        basicReferenceId,
        securityContext.getUserPrincipal().getName()
      )
      ? Response.status(Status.NO_CONTENT).build()
      : Response.status(Status.INTERNAL_SERVER_ERROR).build();
  }
}
