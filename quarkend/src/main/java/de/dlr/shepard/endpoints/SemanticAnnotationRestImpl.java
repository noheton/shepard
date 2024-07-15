package de.dlr.shepard.endpoints;

import java.util.ArrayList;

import de.dlr.shepard.neo4Core.io.SemanticAnnotationIO;
import de.dlr.shepard.neo4Core.services.SemanticAnnotationService;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

public abstract class SemanticAnnotationRestImpl {

	private SemanticAnnotationService semanticAnnotationService = new SemanticAnnotationService();

	protected Response getAllByShepardId(long shepardId) {
		var annotations = semanticAnnotationService.getAllAnnotationsByShepardId(shepardId);
		var result = new ArrayList<SemanticAnnotationIO>(annotations.size());
		for (var reference : annotations) {
			result.add(new SemanticAnnotationIO(reference));
		}
		return Response.ok(result).build();
	}

	protected Response get(long semanticAnnotationId) {
		var result = semanticAnnotationService.getAnnotationByNeo4jId(semanticAnnotationId);
		return Response.ok(new SemanticAnnotationIO(result)).build();
	}

	protected Response createByShepardId(long entityShepardId, SemanticAnnotationIO semanticAnnotation) {
		var result = semanticAnnotationService.createAnnotationByShepardId(entityShepardId, semanticAnnotation);
		return Response.ok(new SemanticAnnotationIO(result)).status(Status.CREATED).build();
	}

	protected Response delete(long semanticAnnotationId) {
		var result = semanticAnnotationService.deleteAnnotationByNeo4jId(semanticAnnotationId);
		return result ? Response.status(Status.NO_CONTENT).build()
				: Response.status(Status.INTERNAL_SERVER_ERROR).build();
	}

}
