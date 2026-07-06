package de.dlr.shepard.context.semantic.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.context.semantic.entities.AnnotatableTimeseries;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.context.semantic.entities.SemanticRepository;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.NotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;
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

  /**
   * Look up an AnnotatableTimeseries by its application-level UUID (channel shepardId).
   * Set by {@code TimeseriesSemanticDualWriteService} on channel creation (TS-SEMANTIC-01).
   * Legacy v1 nodes without appId are invisible here.
   */
  public Optional<AnnotatableTimeseries> findByAppId(String appId) {
    if (appId == null || appId.isBlank()) return Optional.empty();
    var filter = new Filter("appId", ComparisonOperator.EQUALS, appId);
    return this.session.loadAll(AnnotatableTimeseries.class, filter, 2)
      .stream()
      .findFirst();
  }

  public long countAnnotationsByAppId(String channelShepardId) {
    if (channelShepardId == null || channelShepardId.isBlank()) return 0L;
    String query =
      "MATCH (at:AnnotatableTimeseries {appId: $channelShepardId})-[:has_annotation]->(a:SemanticAnnotation) " +
      "RETURN count(a) AS total";
    for (var row : session.query(query, Map.of("channelShepardId", channelShepardId)).queryResults()) {
      Object val = row.get("total");
      if (val instanceof Number n) return n.longValue();
    }
    return 0L;
  }

  public List<SemanticAnnotation> findAnnotationsByAppId(String channelShepardId, long skip, int limit) {
    if (channelShepardId == null || channelShepardId.isBlank()) return List.of();
    String query =
      "MATCH (at:AnnotatableTimeseries {appId: $channelShepardId})-[:has_annotation]->(a:SemanticAnnotation) " +
      "RETURN a ORDER BY a.appId SKIP $skip LIMIT $limit";
    Map<String, Object> params = new HashMap<>();
    params.put("channelShepardId", channelShepardId);
    params.put("skip", skip);
    params.put("limit", (long) limit);
    return StreamSupport
      .stream(session.query(SemanticAnnotation.class, query, params).spliterator(), false)
      .toList();
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

  public void deleteAnnotationByAppId(String annotationAppId) {
    var filter = new Filter("appId", ComparisonOperator.EQUALS, annotationAppId);
    var annotation = session.loadAll(SemanticAnnotation.class, filter, 1)
      .stream()
      .findFirst()
      .orElseThrow(() -> new NotFoundException("No semantic annotation found with appId " + annotationAppId));
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
