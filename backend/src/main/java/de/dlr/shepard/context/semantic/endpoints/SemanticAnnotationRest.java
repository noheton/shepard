package de.dlr.shepard.context.semantic.endpoints;

import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.neo4j.entities.BasicEntity;
import de.dlr.shepard.context.semantic.io.SemanticAnnotationIO;
import de.dlr.shepard.context.semantic.services.SemanticAnnotationService;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.util.ArrayList;

public abstract class SemanticAnnotationRest {

  @Inject
  SemanticAnnotationService semanticAnnotationService;

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

  /**
   * Assert that provided semantic annotation id actually belongs to this BasicEntity (collection, ...)
   *
   * @param entity
   * @param semanticAnnotationId
   * @throws InvalidPathException
   */
  protected void assertSemanticAnnotationBelongsToEntity(BasicEntity entity, long semanticAnnotationId) {
    semanticAnnotationService.getAnnotationByNeo4jId(semanticAnnotationId);
    if (
      !entity
        .getAnnotations()
        .stream()
        .filter(annotation -> annotation.getId().equals(semanticAnnotationId))
        .findFirst()
        .isPresent()
    ) {
      String errorMsg = "ID ERROR - There is no association between annotation and entity";
      Log.error(errorMsg);
      throw new InvalidPathException(errorMsg);
    }
  }
}
