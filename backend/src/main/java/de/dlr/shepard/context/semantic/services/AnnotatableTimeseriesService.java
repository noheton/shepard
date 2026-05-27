package de.dlr.shepard.context.semantic.services;

import de.dlr.shepard.context.semantic.daos.AnnotatableTimeseriesDAO;
import de.dlr.shepard.context.semantic.entities.AnnotatableTimeseries;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.context.semantic.io.SemanticAnnotationIO;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.data.timeseries.services.TimeseriesService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.Collections;
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
    annotatableTimeseries.addAnnotation(annotation);
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

  // ── TS-SEMANTIC-REST: channelShepardId-keyed methods ─────────────────────

  /**
   * List semantic annotations for a channel identified by its UUID v7 shepardId.
   * Returns an empty list when the channel exists in Postgres but has no
   * AnnotatableTimeseries node yet (created only after the first dual-write).
   */
  public List<SemanticAnnotation> getAnnotationsByChannelShepardId(long containerId, String channelShepardId) {
    if (channelShepardId == null || channelShepardId.isBlank()) {
      throw new BadRequestException("channelShepardId must not be blank");
    }
    timeseriesContainerService.getContainer(containerId); // 404 if missing
    return dao.findByAppId(channelShepardId)
      .map(AnnotatableTimeseries::getAnnotations)
      .orElse(Collections.emptyList());
  }

  /**
   * Create a new semantic annotation on a channel identified by UUID v7 shepardId.
   * Requires write permission on the container.
   */
  public SemanticAnnotation createAnnotationForChannel(long containerId, String channelShepardId, SemanticAnnotationIO annotationIO) {
    if (channelShepardId == null || channelShepardId.isBlank()) {
      throw new BadRequestException("channelShepardId must not be blank");
    }
    timeseriesContainerService.getContainer(containerId); // 404 if missing
    timeseriesContainerService.assertIsAllowedToEditContainer(containerId);

    var annotatableTimeseries = dao.findByAppId(channelShepardId)
      .orElseThrow(() -> new NotFoundException(
        "No AnnotatableTimeseries found for channelShepardId=" + channelShepardId +
        ". Channel must be created via the timeseries upload/write path first (TS-SEMANTIC-01 dual-write)."
      ));

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
    annotatableTimeseries.addAnnotation(annotation);
    dao.createOrUpdate(annotatableTimeseries);

    return annotation;
  }

  /**
   * Delete a semantic annotation from a channel identified by UUID v7 shepardId.
   * Requires write permission on the container.
   */
  public void deleteAnnotationForChannel(long containerId, String channelShepardId, long annotationId) {
    if (channelShepardId == null || channelShepardId.isBlank()) {
      throw new BadRequestException("channelShepardId must not be blank");
    }
    timeseriesContainerService.getContainer(containerId); // 404 if missing
    timeseriesContainerService.assertIsAllowedToEditContainer(containerId);
    dao.deleteAnnotation(annotationId);
  }

  public void clearSession() {
    dao.clearSession();
  }
}
