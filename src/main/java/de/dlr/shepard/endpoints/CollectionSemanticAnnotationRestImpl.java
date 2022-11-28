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
@Path(Constants.COLLECTIONS + "/{" + Constants.COLLECTION_ID + "}/" + Constants.SEMANTIC_ANNOTATIONS)
public class CollectionSemanticAnnotationRestImpl extends ASemanticAnnotationRestImpl
		implements CollectionSemanticAnnotationRest {

	@GET
	@Override
	public Response getAllCollectionAnnotations(@PathParam(Constants.COLLECTION_ID) long collectionId) {
		return getAll(collectionId);
	}

	@GET
	@Path("{" + Constants.SEMANTIC_ANNOTATION_ID + "}")
	@Override
	public Response getCollectionAnnotation(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.SEMANTIC_ANNOTATION_ID) long semanticAnnotationId) {
		return get(semanticAnnotationId);
	}

	@POST
	@Subscribable
	@Override
	public Response createCollectionAnnotation(@PathParam(Constants.COLLECTION_ID) long collectionId,
			SemanticAnnotationIO semanticAnnotation) {
		return create(collectionId, semanticAnnotation);
	}

	@DELETE
	@Path("{" + Constants.SEMANTIC_ANNOTATION_ID + "}")
	@Subscribable
	@Override
	public Response deleteCollectionAnnotation(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.SEMANTIC_ANNOTATION_ID) long semanticAnnotationId) {
		return delete(semanticAnnotationId);
	}

}
