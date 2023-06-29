package de.dlr.shepard.endpoints;

import de.dlr.shepard.filters.Subscribable;
import de.dlr.shepard.neo4Core.io.SemanticAnnotationIO;
import de.dlr.shepard.util.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(Constants.COLLECTIONS + "/{" + Constants.COLLECTION_ID + "}/" + Constants.DATAOBJECTS + "/{"
		+ Constants.DATAOBJECT_ID + "}/" + Constants.BASIC_REFERENCES + "/{" + Constants.BASIC_REFERENCE_ID + "}/"
		+ Constants.SEMANTIC_ANNOTATIONS)
public class BasicReferenceSemanticAnnotationRestImpl extends SemanticAnnotationRestImpl
		implements SemanticAnnotationRest {

	@GET
	@Override
	@Operation(operationId = "getAllReferenceAnnotations", description = "Get all semantic annotations", parameters = {
			@Parameter(in = ParameterIn.PATH, name = Constants.COLLECTION_ID, schema = @Schema(type = "integer", format = "int64")),
			@Parameter(in = ParameterIn.PATH, name = Constants.DATAOBJECT_ID, schema = @Schema(type = "integer", format = "int64")) })
	public Response getAllAnnotations(@PathParam(Constants.BASIC_REFERENCE_ID) long basicReferenceId) {
		return getAll(basicReferenceId);
	}

	@GET
	@Path("{" + Constants.SEMANTIC_ANNOTATION_ID + "}")
	@Override
	@Operation(operationId = "getReferenceAnnotation", description = "Get semantic annotation", parameters = {
			@Parameter(in = ParameterIn.PATH, name = Constants.COLLECTION_ID, schema = @Schema(type = "integer", format = "int64")),
			@Parameter(in = ParameterIn.PATH, name = Constants.DATAOBJECT_ID, schema = @Schema(type = "integer", format = "int64")) })
	public Response getAnnotation(@PathParam(Constants.BASIC_REFERENCE_ID) long basicReferenceId,
			@PathParam(Constants.SEMANTIC_ANNOTATION_ID) long semanticAnnotationId) {
		return get(semanticAnnotationId);
	}

	@POST
	@Subscribable
	@Override
	@Operation(operationId = "createReferenceAnnotation", description = "Create a new semantic annotation", parameters = {
			@Parameter(in = ParameterIn.PATH, name = Constants.COLLECTION_ID, schema = @Schema(type = "integer", format = "int64")),
			@Parameter(in = ParameterIn.PATH, name = Constants.DATAOBJECT_ID, schema = @Schema(type = "integer", format = "int64")) })
	public Response createAnnotation(@PathParam(Constants.BASIC_REFERENCE_ID) long basicReferenceId,
			SemanticAnnotationIO semanticAnnotation) {
		return create(basicReferenceId, semanticAnnotation);
	}

	@DELETE
	@Path("{" + Constants.SEMANTIC_ANNOTATION_ID + "}")
	@Subscribable
	@Override
	@Operation(operationId = "deleteReferenceAnnotation", description = "Delete semantic annotation", parameters = {
			@Parameter(in = ParameterIn.PATH, name = Constants.COLLECTION_ID, schema = @Schema(type = "integer", format = "int64")),
			@Parameter(in = ParameterIn.PATH, name = Constants.DATAOBJECT_ID, schema = @Schema(type = "integer", format = "int64")) })
	public Response deleteAnnotation(@PathParam(Constants.BASIC_REFERENCE_ID) long basicReferenceId,
			@PathParam(Constants.SEMANTIC_ANNOTATION_ID) long semanticAnnotationId) {
		return delete(semanticAnnotationId);
	}

}
