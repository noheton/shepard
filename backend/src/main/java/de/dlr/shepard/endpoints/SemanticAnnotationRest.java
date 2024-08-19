package de.dlr.shepard.endpoints;

import de.dlr.shepard.neo4Core.io.SemanticAnnotationIO;
import de.dlr.shepard.neo4Core.services.SemanticAnnotationService;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.util.ArrayList;

public abstract class SemanticAnnotationRest {

  private SemanticAnnotationService semanticAnnotationService;

  SemanticAnnotationRest() {}

  @Inject
  public SemanticAnnotationRest(SemanticAnnotationService semanticAnnotationService) {
    this.semanticAnnotationService = semanticAnnotationService;
  }

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
    return result ? Response.status(Status.NO_CONTENT).build() : Response.status(Status.INTERNAL_SERVER_ERROR).build();
  }
}
