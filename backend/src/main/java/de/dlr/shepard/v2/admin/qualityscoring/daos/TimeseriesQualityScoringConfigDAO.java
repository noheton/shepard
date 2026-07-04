package de.dlr.shepard.v2.admin.qualityscoring.daos;

import de.dlr.shepard.common.identifier.AppIdGenerator;
import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.v2.admin.qualityscoring.entities.TimeseriesQualityScoringConfig;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.neo4j.ogm.session.Session;

/**
 * FTOGGLE-QS-1 — DAO for the {@link TimeseriesQualityScoringConfig} singleton.
 *
 * <p>Mirrors the {@code ThermographyConfigDAO} / {@code JupyterConfigDAO}
 * shape — fresh OGM session per call to avoid startup-ordering races.
 */
@ApplicationScoped
public class TimeseriesQualityScoringConfigDAO extends GenericDAO<TimeseriesQualityScoringConfig> {

  @Override
  public Class<TimeseriesQualityScoringConfig> getEntityType() {
    return TimeseriesQualityScoringConfig.class;
  }

  /**
   * Load the singleton, returning {@link Optional#empty()} when none has
   * been seeded yet. Uses a fresh OGM session to avoid startup-ordering
   * races.
   */
  public Optional<TimeseriesQualityScoringConfig> findSingleton() {
    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) {
      return Optional.empty();
    }
    Collection<TimeseriesQualityScoringConfig> all = live.loadAll(
      TimeseriesQualityScoringConfig.class, 1);
    if (all == null || all.isEmpty()) {
      return Optional.empty();
    }
    return List.copyOf(all)
      .stream()
      .min((a, b) -> Long.compare(
        a.getId() == null ? 0L : a.getId(),
        b.getId() == null ? 0L : b.getId()));
  }

  /**
   * Persist the entity using a fresh OGM session. Mints an appId when absent.
   */
  @Override
  public TimeseriesQualityScoringConfig createOrUpdate(TimeseriesQualityScoringConfig entity) {
    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) {
      throw new IllegalStateException(
        "Neo4j session unavailable — cannot persist :TimeseriesQualityScoringConfig");
    }
    if (entity.getAppId() == null) {
      entity.setAppId(AppIdGenerator.next());
    }
    live.save(entity, 1);
    return entity;
  }
}
