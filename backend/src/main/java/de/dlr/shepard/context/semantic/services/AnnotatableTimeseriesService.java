package de.dlr.shepard.context.semantic.services;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.semantic.daos.AnnotatableTimeseriesDAO;
import de.dlr.shepard.context.semantic.entities.AnnotatableTimeseries;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.context.semantic.io.SemanticAnnotationIO;
import de.dlr.shepard.data.timeseries.repositories.TsChannelResolver;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.data.timeseries.services.TimeseriesService;
import de.dlr.shepard.v2.timeseriescontainer.io.ChannelAxisAnnotationIO;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

  @Inject
  TsChannelResolver tsChannelResolver;

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

  // ── TS-AXIS-AUTO ────────────────────────────────────────────────────────────

  /**
   * Return all SemanticAnnotations linked to the AnnotatableTimeseries node
   * identified by the given channel shepardId. Returns an empty list if no
   * node exists yet (the channel has never been annotated).
   *
   * @param containerId container permission gate (must be accessible to caller)
   * @param shepardId   the channel's stable UUID identity
   * @return list of annotations; may be empty but never null
   */
  public List<SemanticAnnotation> getAnnotationsForChannel(long containerId, UUID shepardId) {
    timeseriesContainerService.getContainer(containerId);
    return dao.findByAppId(shepardId.toString())
        .map(AnnotatableTimeseries::getAnnotations)
        .orElse(List.of());
  }

  /**
   * Write a {@link ChannelAxisAnnotationIO} as a {@link SemanticAnnotation} on the
   * AnnotatableTimeseries node for the given channel.
   *
   * <p>The annotation uses {@link Constants#TS_AXIS_PREDICATE} as {@code propertyIRI}
   * and the caller-supplied {@code value} (e.g. "x", "rot_a") as {@code valueIRI}.
   * No semantic repository or ontology validation is performed — axis roles are
   * first-class Shepard tokens, not ontology terms.
   *
   * <p>If the channel does not yet have an AnnotatableTimeseries node, one is created
   * on-the-fly and its {@code appId} is seeded from the Postgres {@code shepardId}.
   * This guarantees that the Neo4j node is always consistent with the Postgres row.
   *
   * <p>Any existing annotation for the same {@code propertyIRI} + {@code value} pair
   * is <em>not</em> deduplicated here; callers must check before writing if idempotency
   * is required (the seed script uses {@code annotate_spatial_roles} which replaces
   * the full axis set atomically).
   *
   * @param containerId container permission gate (caller must have write permission)
   * @param shepardId   the channel's stable UUID identity
   * @param body        the axis role to assign
   * @return the created SemanticAnnotation node
   * @throws NotFoundException if no channel with that shepardId exists in this container
   */
  public SemanticAnnotation createAnnotationForChannel(
      long containerId, UUID shepardId, ChannelAxisAnnotationIO body) {

    timeseriesContainerService.getContainer(containerId);
    timeseriesContainerService.assertIsAllowedToEditContainer(containerId);

    // Verify the channel exists in this container
    var channelEntity = tsChannelResolver.findByShepardId(shepardId)
        .filter(e -> e.getContainerId() == containerId)
        .orElseThrow(() -> new NotFoundException(
            "No channel with shepardId " + shepardId + " in container " + containerId));

    // Resolve or create the AnnotatableTimeseries bridge node
    var annotatableTimeseries = dao.findByAppId(shepardId.toString())
        .orElseGet(() -> {
          var node = new AnnotatableTimeseries(containerId, channelEntity.getId(), new ArrayList<>());
          node.setAppId(shepardId.toString());
          return node;
        });

    // Build the annotation — no repository, no ontology validation
    var annotation = new SemanticAnnotation();
    annotation.setPropertyIRI(Constants.TS_AXIS_PREDICATE);
    annotation.setPropertyName("spatial:axis");
    annotation.setValueIRI(body.value());
    annotation.setValueName(body.value());

    annotatableTimeseries.addAnnotation(annotation);
    dao.createOrUpdate(annotatableTimeseries);

    return annotation;
  }
}
