package de.dlr.shepard.context.semantic.services;

import de.dlr.shepard.context.semantic.daos.AnnotatableTimeseriesDAO;
import de.dlr.shepard.context.semantic.entities.AnnotatableTimeseries;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.context.semantic.io.SemanticAnnotationIO;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.data.timeseries.services.TimeseriesService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RequestScoped
public class AnnotatableTimeseriesService {

  @Inject
  AnnotatableTimeseriesDAO dao;

  @Inject
  SemanticAnnotationService semanticAnnotationService;

  @Inject
  TimeseriesService timeseriesService;

  @Inject
  TimeseriesContainerService timeseriesContainerService;

  public SemanticAnnotation createAnnotation(long containerId, int timeseriesId, SemanticAnnotationIO annotationIO) {
    timeseriesService.getTimeseriesById(containerId, timeseriesId);
    timeseriesContainerService.assertIsAllowedToEditContainer(containerId);

    var annotatableTimeseries = dao
      .findByTimeseries(containerId, timeseriesId)
      .orElse(new AnnotatableTimeseries(containerId, timeseriesId, new ArrayList<>()));

    var propertyRepository = dao.getSemanticRepositoryById(annotationIO.getPropertyRepositoryId());
    var valueRepository = dao.getSemanticRepositoryById(annotationIO.getValueRepositoryId());

    var annotation = new SemanticAnnotation();
    var propertyName = semanticAnnotationService.validateTerm(propertyRepository, annotationIO.getPropertyIRI());
    var valueName = semanticAnnotationService.validateTerm(valueRepository, annotationIO.getValueIRI());
    annotation.setPropertyRepository(propertyRepository);
    annotation.setPropertyIRI(annotationIO.getPropertyIRI());
    annotation.setValueRepository(valueRepository);
    annotation.setValueIRI(annotationIO.getValueIRI());
    annotation.setPropertyName(propertyName);
    annotation.setValueName(valueName);

    annotatableTimeseries.getAnnotations().add(annotation);
    dao.createOrUpdate(annotatableTimeseries);

    return annotation;
  }

  public void deleteAnnotation(long containerId, int timeseriesId, long annotationId) {
    timeseriesService.getTimeseriesById(containerId, timeseriesId);
    timeseriesContainerService.assertIsAllowedToEditContainer(containerId);

    dao.deleteAnnotation(annotationId);
  }

  public List<SemanticAnnotation> getAnnotations(long containerId, int timeseriesId) {
    timeseriesService.getTimeseriesById(containerId, timeseriesId);

    Optional<AnnotatableTimeseries> annotatableTimeseries = dao.findByTimeseries(containerId, timeseriesId);
    if (annotatableTimeseries.isPresent()) {
      return annotatableTimeseries.get().getAnnotations();
    } else {
      return new ArrayList<>();
    }
  }

  public SemanticAnnotation getAnnotationById(long containerId, int timeseriesId, long annotationId) {
    timeseriesService.getTimeseriesById(containerId, timeseriesId);

    return dao.getAnnotationById(annotationId);
  }

  public void clearSession() {
    dao.clearSession();
  }
}
