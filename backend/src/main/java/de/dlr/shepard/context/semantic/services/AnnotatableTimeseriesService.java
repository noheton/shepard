package de.dlr.shepard.context.semantic.services;

import de.dlr.shepard.context.semantic.daos.AnnotatableTimeseriesDAO;
import de.dlr.shepard.context.semantic.entities.AnnotatableTimeseries;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.context.semantic.io.SemanticAnnotationIO;
import de.dlr.shepard.data.timeseries.services.TimeseriesService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.List;

@RequestScoped
public class AnnotatableTimeseriesService {

  private AnnotatableTimeseriesDAO dao;
  private SemanticAnnotationService semanticAnnotationService;
  private TimeseriesService timeseriesService;

  AnnotatableTimeseriesService() {}

  @Inject
  public AnnotatableTimeseriesService(
    AnnotatableTimeseriesDAO annotatableTimeseriesDAO,
    SemanticAnnotationService semanticAnnotationService,
    TimeseriesService timeseriesService
  ) {
    this.dao = annotatableTimeseriesDAO;
    this.semanticAnnotationService = semanticAnnotationService;
    this.timeseriesService = timeseriesService;
  }

  public SemanticAnnotation createAnnotation(long containerId, int timeseriesId, SemanticAnnotationIO annotationIO) {
    timeseriesService.getTimeseriesById(timeseriesId);

    var annotatableTimeseries = dao.findByTimeseries(containerId, timeseriesId);
    if (annotatableTimeseries == null) {
      annotatableTimeseries = new AnnotatableTimeseries(containerId, timeseriesId, new ArrayList<>());
    }

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

  public void deleteAnnotation(int timeseriesId, long annotationId) {
    timeseriesService.getTimeseriesById(timeseriesId);

    dao.deleteAnnotation(annotationId);
  }

  public List<SemanticAnnotation> getAnnotations(long containerId, int timeseriesId) {
    timeseriesService.getTimeseriesById(timeseriesId);

    var annotatableTimeseries = dao.findByTimeseries(containerId, timeseriesId);
    if (annotatableTimeseries != null) {
      return annotatableTimeseries.getAnnotations();
    }
    throw new NotFoundException();
  }

  public SemanticAnnotation getAnnotationById(long containerId, int timeseriesId, long annotationId) {
    timeseriesService.getTimeseriesById(timeseriesId);

    return dao.getAnnotationById(annotationId);
  }

  public void clearSession() {
    dao.clearSession();
  }
}
