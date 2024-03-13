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
		+ Constants.DATAOBJECT_ID + "}/" + Constants.SEMANTIC_ANNOTATIONS)
public class DataObjectSemanticAnnotationRestImpl extends SemanticAnnotationRestImpl implements SemanticAnnotationRest {

	@GET
	@Override
	@Operation(operationId = "getAllDataObjectAnnotations", description = "Get all semantic annotations", parameters = {
			@Parameter(in = ParameterIn.PATH, name = Constants.COLLECTION_ID, schema = @Schema(type = "integer", format = "int64")) })
	public Response getAllAnnotations(@PathParam(Constants.DATAOBJECT_ID) long dataObjectId) {
		return getAllByShepardId(dataObjectId);
	}

	@GET
	@Path("{" + Constants.SEMANTIC_ANNOTATION_ID + "}")
	@Override
	@Operation(operationId = "getDataObjectAnnotation", description = "Get semantic annotation", parameters = {
			@Parameter(in = ParameterIn.PATH, name = Constants.COLLECTION_ID, schema = @Schema(type = "integer", format = "int64")) })
	public Response getAnnotation(@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@PathParam(Constants.SEMANTIC_ANNOTATION_ID) long semanticAnnotationId) {
		return get(semanticAnnotationId);
	}

	@POST
	@Subscribable
	@Override
	@Operation(operationId = "createDataObjectAnnotation", description = "Create a new semantic annotation", parameters = {
			@Parameter(in = ParameterIn.PATH, name = Constants.COLLECTION_ID, schema = @Schema(type = "integer", format = "int64")) })
	public Response createAnnotation(@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			SemanticAnnotationIO semanticAnnotation) {
		return createByShepardId(dataObjectId, semanticAnnotation);
	}

	@DELETE
	@Path("{" + Constants.SEMANTIC_ANNOTATION_ID + "}")
	@Subscribable
	@Override
	@Operation(operationId = "deleteDataObjectAnnotation", description = "Delete semantic annotation", parameters = {
			@Parameter(in = ParameterIn.PATH, name = Constants.COLLECTION_ID, schema = @Schema(type = "integer", format = "int64")) })
	public Response deleteAnnotation(@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@PathParam(Constants.SEMANTIC_ANNOTATION_ID) long semanticAnnotationId) {
		return delete(semanticAnnotationId);
	}

}
