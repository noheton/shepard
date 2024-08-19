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
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(Constants.COLLECTIONS + "/{" + Constants.COLLECTION_ID + "}/" + Constants.SEMANTIC_ANNOTATIONS)
@RequestScoped
public class CollectionSemanticAnnotationRest extends SemanticAnnotationRest {

  CollectionSemanticAnnotationRest() {}

  @Inject
  public CollectionSemanticAnnotationRest(SemanticAnnotationService semanticAnnotationService) {
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
  @Operation(operationId = "getAllCollectionAnnotations", description = "Get all semantic annotations")
  public Response getAllAnnotations(@PathParam(Constants.COLLECTION_ID) long collectionId) {
    return getAllByShepardId(collectionId);
  }

  @GET
  @Tag(name = Constants.SEMANTIC_ANNOTATION)
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = SemanticAnnotationIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Path("{" + Constants.SEMANTIC_ANNOTATION_ID + "}")
  @Operation(operationId = "getCollectionAnnotation", description = "Get semantic annotation")
  public Response getAnnotation(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
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
  @Operation(operationId = "createCollectionAnnotation", description = "Create a new semantic annotation")
  public Response createAnnotation(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = SemanticAnnotationIO.class))
    ) @Valid SemanticAnnotationIO semanticAnnotation
  ) {
    return createByShepardId(collectionId, semanticAnnotation);
  }

  @DELETE
  @Tag(name = Constants.SEMANTIC_ANNOTATION)
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(description = "not found", responseCode = "404")
  @Path("{" + Constants.SEMANTIC_ANNOTATION_ID + "}")
  @Subscribable
  @Operation(operationId = "deleteCollectionAnnotation", description = "Delete semantic annotation")
  public Response deleteAnnotation(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.SEMANTIC_ANNOTATION_ID) long semanticAnnotationId
  ) {
    return delete(semanticAnnotationId);
  }
}
