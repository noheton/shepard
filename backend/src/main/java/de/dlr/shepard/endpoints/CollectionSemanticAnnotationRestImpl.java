package de.dlr.shepard.endpoints;

import de.dlr.shepard.filters.Subscribable;
import de.dlr.shepard.neo4Core.io.SemanticAnnotationIO;
import de.dlr.shepard.util.Constants;
import io.swagger.v3.oas.annotations.Operation;
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
@Path(Constants.COLLECTIONS + "/{" + Constants.COLLECTION_ID + "}/" + Constants.SEMANTIC_ANNOTATIONS)
public class CollectionSemanticAnnotationRestImpl extends SemanticAnnotationRestImpl implements SemanticAnnotationRest {

	@GET
	@Override
	@Operation(operationId = "getAllCollectionAnnotations", description = "Get all semantic annotations")
	public Response getAllAnnotations(@PathParam(Constants.COLLECTION_ID) long collectionId) {
		return getAllByShepardId(collectionId);
	}

	@GET
	@Path("{" + Constants.SEMANTIC_ANNOTATION_ID + "}")
	@Override
	@Operation(operationId = "getCollectionAnnotation", description = "Get semantic annotation")
	public Response getAnnotation(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.SEMANTIC_ANNOTATION_ID) long semanticAnnotationId) {
		return get(semanticAnnotationId);
	}

	@POST
	@Subscribable
	@Override
	@Operation(operationId = "createCollectionAnnotation", description = "Create a new semantic annotation")
	public Response createAnnotation(@PathParam(Constants.COLLECTION_ID) long collectionId,
			SemanticAnnotationIO semanticAnnotation) {
		return createByShepardId(collectionId, semanticAnnotation);
	}

	@DELETE
	@Path("{" + Constants.SEMANTIC_ANNOTATION_ID + "}")
	@Subscribable
	@Override
	@Operation(operationId = "deleteCollectionAnnotation", description = "Delete semantic annotation")
	public Response deleteAnnotation(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.SEMANTIC_ANNOTATION_ID) long semanticAnnotationId) {
		return delete(semanticAnnotationId);
	}

}
