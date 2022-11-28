package de.dlr.shepard.endpoints;

import de.dlr.shepard.filters.Subscribable;
import de.dlr.shepard.neo4Core.io.SemanticAnnotationIO;
import de.dlr.shepard.util.Constants;
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
public class BasicReferenceSemanticAnnotationRestImpl extends ASemanticAnnotationRestImpl
		implements BasicReferenceSemanticAnnotationRest {

	@GET
	@Override
	public Response getAllReferenceAnnotations(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@PathParam(Constants.BASIC_REFERENCE_ID) long basicReferenceId) {
		return getAll(basicReferenceId);
	}

	@GET
	@Path("{" + Constants.SEMANTIC_ANNOTATION_ID + "}")
	@Override
	public Response getReferenceAnnotation(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@PathParam(Constants.BASIC_REFERENCE_ID) long basicReferenceId,
			@PathParam(Constants.SEMANTIC_ANNOTATION_ID) long semanticAnnotationId) {
		return get(semanticAnnotationId);
	}

	@POST
	@Subscribable
	@Override
	public Response createReferenceAnnotation(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@PathParam(Constants.BASIC_REFERENCE_ID) long basicReferenceId, SemanticAnnotationIO semanticAnnotation) {
		return create(basicReferenceId, semanticAnnotation);
	}

	@DELETE
	@Path("{" + Constants.SEMANTIC_ANNOTATION_ID + "}")
	@Subscribable
	@Override
	public Response deleteReferenceAnnotation(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@PathParam(Constants.BASIC_REFERENCE_ID) long basicReferenceId,
			@PathParam(Constants.SEMANTIC_ANNOTATION_ID) long semanticAnnotationId) {
		return delete(semanticAnnotationId);
	}

}
