package de.dlr.shepard.context.semantic.endpoints;

import de.dlr.shepard.common.filters.Subscribable;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.context.references.basicreference.services.BasicReferenceService;
import de.dlr.shepard.context.semantic.io.SemanticAnnotationIO;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
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
  Constants.BASIC_REFERENCES +
  "/{" +
  Constants.BASIC_REFERENCE_ID +
  "}/" +
  Constants.SEMANTIC_ANNOTATIONS
)
@RequestScoped
public class BasicReferenceSemanticAnnotationRest extends SemanticAnnotationRest {

  @Inject
  CollectionService collectionService;

  @Inject
  BasicReferenceService basicReferenceService;

  @GET
  @Tag(name = Constants.SEMANTIC_ANNOTATION)
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = SemanticAnnotationIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
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
  public Response getAllAnnotations(
    @PathParam(Constants.COLLECTION_ID) @NotNull @PositiveOrZero Long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) @NotNull @PositiveOrZero Long dataObjectId,
    @PathParam(Constants.BASIC_REFERENCE_ID) @NotNull @PositiveOrZero Long basicReferenceId
  ) {
    basicReferenceService.getReference(collectionId, dataObjectId, basicReferenceId);
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
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
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
    @PathParam(Constants.COLLECTION_ID) @NotNull @PositiveOrZero Long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) @NotNull @PositiveOrZero Long dataObjectId,
    @PathParam(Constants.BASIC_REFERENCE_ID) @NotNull @PositiveOrZero Long basicReferenceId,
    @PathParam(Constants.SEMANTIC_ANNOTATION_ID) @NotNull @PositiveOrZero Long semanticAnnotationId
  ) {
    BasicReference basicReference = basicReferenceService.getReference(collectionId, dataObjectId, basicReferenceId);
    assertSemanticAnnotationBelongsToEntity(basicReference, semanticAnnotationId);
    return get(semanticAnnotationId);
  }

  @POST
  @Tag(name = Constants.SEMANTIC_ANNOTATION)
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = SemanticAnnotationIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
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
    @PathParam(Constants.COLLECTION_ID) @NotNull @PositiveOrZero Long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) @NotNull @PositiveOrZero Long dataObjectId,
    @PathParam(Constants.BASIC_REFERENCE_ID) @NotNull @PositiveOrZero Long basicReferenceId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = SemanticAnnotationIO.class))
    ) @Valid SemanticAnnotationIO semanticAnnotation
  ) {
    basicReferenceService.getReference(collectionId, dataObjectId, basicReferenceId);
    collectionService.assertIsAllowedToEditCollection(collectionId);
    return createByShepardId(basicReferenceId, semanticAnnotation);
  }

  @DELETE
  @Tag(name = Constants.SEMANTIC_ANNOTATION)
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
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
    @PathParam(Constants.COLLECTION_ID) @NotNull @PositiveOrZero Long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) @NotNull @PositiveOrZero Long dataObjectId,
    @PathParam(Constants.BASIC_REFERENCE_ID) @NotNull @PositiveOrZero Long basicReferenceId,
    @PathParam(Constants.SEMANTIC_ANNOTATION_ID) @NotNull @PositiveOrZero Long semanticAnnotationId
  ) {
    BasicReference basicReference = basicReferenceService.getReference(collectionId, dataObjectId, basicReferenceId);
    collectionService.assertIsAllowedToEditCollection(collectionId);
    assertSemanticAnnotationBelongsToEntity(basicReference, semanticAnnotationId);
    return delete(semanticAnnotationId);
  }
}
