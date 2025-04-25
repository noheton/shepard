package de.dlr.shepard.context.semantic.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.context.semantic.entities.AnnotatableTimeseries;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.context.semantic.entities.SemanticRepository;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.NotFoundException;
import java.util.Optional;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;

@RequestScoped
public class AnnotatableTimeseriesDAO extends GenericDAO<AnnotatableTimeseries> {

  public Optional<AnnotatableTimeseries> findByTimeseries(long containerId, int timeseriesId) {
    var containerFilter = new Filter("containerId", ComparisonOperator.EQUALS, containerId);
    var timeseriesFilter = new Filter("timeseriesId", ComparisonOperator.EQUALS, timeseriesId);
    return this.session.loadAll(AnnotatableTimeseries.class, containerFilter.and(timeseriesFilter), 2)
      .stream()
      .findFirst();
  }

  public SemanticAnnotation getAnnotationById(long annotationId) {
    var entity = this.session.load(SemanticAnnotation.class, annotationId, 2);
    if (entity != null) return entity;

    throw new NotFoundException("No semantic annotation found with id " + annotationId);
  }

  public void deleteAnnotation(long annotationId) {
    var annotation = getAnnotationById(annotationId);
    session.delete(annotation);
  }

  public SemanticRepository getSemanticRepositoryById(long semanticRepositoryId) {
    var entity = session.load(SemanticRepository.class, semanticRepositoryId);
    if (entity != null) return entity;

    throw new NotFoundException("No semantic repository found with id " + semanticRepositoryId);
  }

  @Override
  public AnnotatableTimeseries createOrUpdate(AnnotatableTimeseries entity) {
    session.save(entity, 2);
    return entity;
  }

  @Override
  public Class<AnnotatableTimeseries> getEntityType() {
    return AnnotatableTimeseries.class;
  }
}
