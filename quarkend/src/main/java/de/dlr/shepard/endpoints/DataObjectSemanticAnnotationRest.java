package de.dlr.shepard.endpoints;

import de.dlr.shepard.filters.Subscribable;
import de.dlr.shepard.neo4Core.io.SemanticAnnotationIO;
import de.dlr.shepard.util.Constants;
import jakarta.enterprise.context.RequestScoped;
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
@Path(
  Constants.COLLECTIONS +
  "/{" +
  Constants.COLLECTION_ID +
  "}/" +
  Constants.DATAOBJECTS +
  "/{" +
  Constants.DATAOBJECT_ID +
  "}/" +
  Constants.SEMANTIC_ANNOTATIONS
)
@RequestScoped
public class DataObjectSemanticAnnotationRest extends SemanticAnnotationRest {

  @GET
  @Tag(name = Constants.SEMANTIC_ANNOTATION)
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = SemanticAnnotationIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Operation(
    operationId = "getAllDataObjectAnnotations",
    description = "Get all semantic annotations"
    // TODO: Fix parameters
    /*
		parameters = {
		  @Parameter(
			in = ParameterIn.PATH,
			name = Constants.COLLECTION_ID,
			schema = @Schema(type = SchemaType.INTEGER, format = "int64")
		  ),
		}*/
  )
  public Response getAllAnnotations(@PathParam(Constants.DATAOBJECT_ID) long dataObjectId) {
    return getAllByShepardId(dataObjectId);
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
  @Operation(
    operationId = "getDataObjectAnnotation",
    description = "Get semantic annotation"
    // TODO: Fix parameters
    /*parameters = {
		  @Parameter(
			in = ParameterIn.PATH,
			name = Constants.COLLECTION_ID,
			schema = @Schema(type =  SchemaType.INTEGER, format = "int64")
		  ),
		}*/
  )
  public Response getAnnotation(
    @PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
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
  @Operation(
    operationId = "createDataObjectAnnotation",
    description = "Create a new semantic annotation"
    // TODO: Fix parameters
    /*parameters = {
		  @Parameter(
			in = ParameterIn.PATH,
			name = Constants.COLLECTION_ID,
			schema = @Schema(type = SchemaType.INTEGER, format = "int64")
		  ),
		}*/
  )
  public Response createAnnotation(
    @PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = SemanticAnnotationIO.class))
    ) @Valid SemanticAnnotationIO semanticAnnotation
  ) {
    return createByShepardId(dataObjectId, semanticAnnotation);
  }

  @DELETE
  @Tag(name = Constants.SEMANTIC_ANNOTATION)
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(description = "not found", responseCode = "404")
  @Path("{" + Constants.SEMANTIC_ANNOTATION_ID + "}")
  @Subscribable
  @Operation(
    operationId = "deleteDataObjectAnnotation",
    description = "Delete semantic annotation"
    // TODO: Fix parameters
    /*parameters = {
			@Parameter(
				in = ParameterIn.PATH,
				name = Constants.COLLECTION_ID,
				schema = @Schema(type = SchemaType.INTEGER, format = "int64")
			),
		}*/
  )
  public Response deleteAnnotation(
    @PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
    @PathParam(Constants.SEMANTIC_ANNOTATION_ID) long semanticAnnotationId
  ) {
    return delete(semanticAnnotationId);
  }
}
