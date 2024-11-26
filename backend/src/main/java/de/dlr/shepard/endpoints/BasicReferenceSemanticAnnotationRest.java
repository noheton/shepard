package de.dlr.shepard.endpoints;

import de.dlr.shepard.filters.Subscribable;
import de.dlr.shepard.neo4Core.io.SemanticAnnotationIO;
import de.dlr.shepard.neo4Core.services.SemanticAnnotationService;
import de.dlr.shepard.util.Constants;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(
  Constants.COLLECTIONS +
  "/{" +
  Constants.COLLECTION_ID +
  "}/" +
  Constants.DATA_OBJECTS +
  "/{" +
  Constants.DATA_OBJECT_ID +
  "}/" +
  Constants.BASIC_REFERENCES +
  "/{" +
  Constants.BASIC_REFERENCE_ID +
  "}/" +
  Constants.SEMANTIC_ANNOTATIONS
)
@RequestScoped
public class BasicReferenceSemanticAnnotationRest extends SemanticAnnotationRest {

  BasicReferenceSemanticAnnotationRest() {}

  @Inject
  public BasicReferenceSemanticAnnotationRest(SemanticAnnotationService semanticAnnotationService) {
    super(semanticAnnotationService);
  }

  @GET
  @Tag(name = Constants.SEMANTIC_ANNOTATION)
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = SemanticAnnotationIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Operation(operationId = "getAllReferenceAnnotations", description = "Get all semantic annotations")
  @Parameter(
    in = ParameterIn.PATH,
    name = Constants.COLLECTION_ID,
    schema = @Schema(type = SchemaType.INTEGER, format = "int64")
  )
  @Parameter(
    in = ParameterIn.PATH,
    name = Constants.DATA_OBJECT_ID,
    schema = @Schema(type = SchemaType.INTEGER, format = "int64")
  )
  @Parameter(name = Constants.BASIC_REFERENCE_ID)
  public Response getAllAnnotations(@PathParam(Constants.BASIC_REFERENCE_ID) long basicReferenceId) {
    return getAllByShepardId(basicReferenceId);
  }

  @GET
  @Path("{" + Constants.SEMANTIC_ANNOTATION_ID + "}")
  @Tag(name = Constants.SEMANTIC_ANNOTATION)
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = SemanticAnnotationIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Operation(operationId = "getReferenceAnnotation", description = "Get semantic annotation")
  @Parameter(
    in = ParameterIn.PATH,
    name = Constants.COLLECTION_ID,
    schema = @Schema(type = SchemaType.INTEGER, format = "int64")
  )
  @Parameter(
    in = ParameterIn.PATH,
    name = Constants.DATA_OBJECT_ID,
    schema = @Schema(type = SchemaType.INTEGER, format = "int64")
  )
  @Parameter(name = Constants.BASIC_REFERENCE_ID)
  @Parameter(name = Constants.SEMANTIC_ANNOTATION_ID)
  public Response getAnnotation(
    @PathParam(Constants.BASIC_REFERENCE_ID) long basicReferenceId,
    @PathParam(Constants.SEMANTIC_ANNOTATION_ID) long semanticAnnotationId
  ) {
    return get(semanticAnnotationId);
  }

  @POST
  @Tag(name = Constants.SEMANTIC_ANNOTATION)
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = SemanticAnnotationIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Subscribable
  @Operation(operationId = "createReferenceAnnotation", description = "Create a new semantic annotation")
  @Parameter(
    in = ParameterIn.PATH,
    name = Constants.COLLECTION_ID,
    schema = @Schema(type = SchemaType.INTEGER, format = "int64")
  )
  @Parameter(
    in = ParameterIn.PATH,
    name = Constants.DATA_OBJECT_ID,
    schema = @Schema(type = SchemaType.INTEGER, format = "int64")
  )
  @Parameter(name = Constants.BASIC_REFERENCE_ID)
  public Response createAnnotation(
    @PathParam(Constants.BASIC_REFERENCE_ID) long basicReferenceId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = SemanticAnnotationIO.class))
    ) @Valid SemanticAnnotationIO semanticAnnotation
  ) {
    return createByShepardId(basicReferenceId, semanticAnnotation);
  }

  @DELETE
  @Tag(name = Constants.SEMANTIC_ANNOTATION)
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(description = "not found", responseCode = "404")
  @Path("{" + Constants.SEMANTIC_ANNOTATION_ID + "}")
  @Subscribable
  @Operation(operationId = "deleteReferenceAnnotation", description = "Delete semantic annotation")
  @Parameter(
    in = ParameterIn.PATH,
    name = Constants.COLLECTION_ID,
    schema = @Schema(type = SchemaType.INTEGER, format = "int64")
  )
  @Parameter(
    in = ParameterIn.PATH,
    name = Constants.DATA_OBJECT_ID,
    schema = @Schema(type = SchemaType.INTEGER, format = "int64")
  )
  @Parameter(name = Constants.BASIC_REFERENCE_ID)
  @Parameter(name = Constants.SEMANTIC_ANNOTATION_ID)
  public Response deleteAnnotation(
    @PathParam(Constants.BASIC_REFERENCE_ID) long basicReferenceId,
    @PathParam(Constants.SEMANTIC_ANNOTATION_ID) long semanticAnnotationId
  ) {
    return delete(semanticAnnotationId);
  }
}
