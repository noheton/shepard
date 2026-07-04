package de.dlr.shepard.v2.admin.qualityscoring.services;

import de.dlr.shepard.v2.admin.qualityscoring.daos.TimeseriesQualityScoringConfigDAO;
import de.dlr.shepard.v2.admin.qualityscoring.entities.TimeseriesQualityScoringConfig;
import de.dlr.shepard.v2.admin.qualityscoring.io.TimeseriesQualityScoringConfigIO;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.RequestContextController;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * FTOGGLE-QS-1 — service layer for the {@code :TimeseriesQualityScoringConfig}
 * singleton. Mirrors the {@code ThermographyConfigService} shape.
 *
 * <p>Seeds the singleton on startup from deploy-time defaults; exposes
 * {@link #isEnabled()} and {@link #effectiveBatchSize()} so the AI1c
 * {@code TimeseriesQualityScoringJob} can read runtime-mutable values without
 * re-reading {@code @ConfigProperty} on every tick.
 */
@ApplicationScoped
public class TimeseriesQualityScoringConfigService {

  @Inject
  TimeseriesQualityScoringConfigDAO dao;

  @Inject
  RequestContextController requestContextController;

  @ConfigProperty(name = "shepard.timeseries.quality-scoring.enabled", defaultValue = "false")
  boolean defaultEnabled;

  @ConfigProperty(name = "shepard.timeseries.quality-scoring.batch-size", defaultValue = "100")
  int defaultBatchSize;

  void onStart(@Observes StartupEvent event) {
    boolean activated = requestContextController.activate();
    try {
      seedIfNeeded();
    } catch (RuntimeException e) {
      Log.warnf(e,
        "FTOGGLE-QS-1: could not seed :TimeseriesQualityScoringConfig on startup; " +
        "job will use deploy-time defaults");
    } finally {
      if (activated) {
        requestContextController.deactivate();
      }
    }
  }

  public synchronized TimeseriesQualityScoringConfig seedIfNeeded() {
    return dao.findSingleton().orElseGet(() -> {
      TimeseriesQualityScoringConfig seed = new TimeseriesQualityScoringConfig();
      seed.setEnabled(null);
      seed.setBatchSize(null);
      TimeseriesQualityScoringConfig saved = dao.createOrUpdate(seed);
      Log.infof(
        "FTOGGLE-QS-1: seeded :TimeseriesQualityScoringConfig (appId=%s, " +
        "enabled=<default %b>, batchSize=<default %d>)",
        saved.getAppId(), defaultEnabled, defaultBatchSize);
      return saved;
    });
  }

  public TimeseriesQualityScoringConfig current() {
    return dao.findSingleton().orElseGet(this::seedIfNeeded);
  }

  /** Effective config IO with all null fields resolved to deploy-time defaults. */
  public TimeseriesQualityScoringConfigIO getConfig() {
    return TimeseriesQualityScoringConfigIO.from(current(), defaultEnabled, defaultBatchSize);
  }

  /** Runtime-effective enabled flag — used by the job. */
  public boolean isEnabled() {
    TimeseriesQualityScoringConfig cfg = dao.findSingleton().orElse(null);
    if (cfg == null || cfg.getEnabled() == null) return defaultEnabled;
    return cfg.getEnabled();
  }

  /** Runtime-effective batch size — used by the job. */
  public int effectiveBatchSize() {
    TimeseriesQualityScoringConfig cfg = dao.findSingleton().orElse(null);
    if (cfg == null || cfg.getBatchSize() == null) return defaultBatchSize;
    return cfg.getBatchSize();
  }

  /**
   * Apply a runtime merge-patch. Null arguments revert the field to the
   * deploy-time default (RFC 7396 semantics).
   */
  public synchronized TimeseriesQualityScoringConfigIO patch(Boolean enabled, Integer batchSize) {
    TimeseriesQualityScoringConfig cfg = current();
    cfg.setEnabled(enabled);
    cfg.setBatchSize(batchSize);
    TimeseriesQualityScoringConfig saved = dao.createOrUpdate(cfg);
    Log.infof(
      "FTOGGLE-QS-1: :TimeseriesQualityScoringConfig patched (enabled=%s, batchSize=%s)",
      saved.getEnabled(), saved.getBatchSize());
    return TimeseriesQualityScoringConfigIO.from(saved, defaultEnabled, defaultBatchSize);
  }

  public boolean getDefaultEnabled() {
    return defaultEnabled;
  }

  public int getDefaultBatchSize() {
    return defaultBatchSize;
  }
}
