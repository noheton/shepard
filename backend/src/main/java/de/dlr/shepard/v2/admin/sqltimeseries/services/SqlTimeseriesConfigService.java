package de.dlr.shepard.v2.admin.sqltimeseries.services;

import de.dlr.shepard.v2.admin.sqltimeseries.daos.SqlTimeseriesConfigDAO;
import de.dlr.shepard.v2.admin.sqltimeseries.entities.SqlTimeseriesConfig;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.RequestContextController;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * P10c — service layer for the {@code :SqlTimeseriesConfig} singleton.
 *
 * <p>Responsibilities:
 *
 * <ol>
 *   <li><b>Seed on first start.</b> {@link #onStart(StartupEvent)}
 *       observes the Quarkus {@code StartupEvent}; if no
 *       {@code :SqlTimeseriesConfig} node exists yet, one is seeded from
 *       the deploy-time defaults ({@code shepard.timeseries.sql.max-rows}
 *       and {@code shepard.timeseries.sql.max-duration}). Pre-P10c installs
 *       upgrading get the singleton minted on their next restart with values
 *       matching their existing deploy-time config.</li>
 *   <li><b>Get-or-seed.</b> {@link #current()} returns the singleton
 *       (seeding if absent — defence-in-depth against a fresh DB that
 *       somehow missed the startup hook).</li>
 *   <li><b>Effective values.</b> {@link #effectiveMaxRows()} and
 *       {@link #effectiveMaxDurationIso()} return the runtime singleton's
 *       value when non-null, falling back to the deploy-time default.
 *       This is the source of truth consumed by {@code SqlTimeseriesRest}.</li>
 *   <li><b>Merge-patch.</b> {@link #patch(Long, String)} applies the
 *       runtime-mutable subset of the config per RFC 7396 semantics
 *       (null = clear to default, absent = leave alone — handled by the
 *       REST layer before calling this method).</li>
 * </ol>
 *
 * <p>Mirrors the {@code InstanceRorConfigService} startup pattern exactly.
 */
@ApplicationScoped
public class SqlTimeseriesConfigService {

  @Inject
  SqlTimeseriesConfigDAO dao;

  @Inject
  RequestContextController requestContextController;

  @ConfigProperty(name = "shepard.timeseries.sql.max-rows", defaultValue = "1000000")
  long defaultMaxRows;

  @ConfigProperty(name = "shepard.timeseries.sql.max-duration", defaultValue = "PT60S")
  String defaultMaxDuration;

  /**
   * Seed the singleton on first startup. Idempotent — re-running sees the
   * existing row and returns. Logged at INFO so an operator can grep startup
   * logs to confirm P10c came up correctly.
   *
   * <p>The seed runs lazily-scoped (Arc's {@link RequestContextController})
   * so the DAO's request-scoped machinery has a context to bind to even
   * though the {@code StartupEvent} fires outside a JAX-RS request.
   */
  void onStart(@Observes StartupEvent event) {
    boolean activated = requestContextController.activate();
    try {
      seedIfNeeded();
    } catch (RuntimeException e) {
      // Seeding failure must not block startup — operators get a WARN.
      // effectiveMaxRows() / effectiveMaxDurationIso() fall back to deploy-time
      // defaults even without a seeded singleton (same fail-soft posture as ROR1).
      Log.warnf(e, "P10c: could not seed :SqlTimeseriesConfig on startup; will retry on first admin read");
    } finally {
      if (activated) {
        requestContextController.deactivate();
      }
    }
  }

  /**
   * Seed the singleton if it doesn't exist yet. Public for tests +
   * defence-in-depth callers (the service's other entry points all call
   * {@link #current()} which delegates here).
   *
   * <p>Seeds with the deploy-time defaults ({@code max-rows} and
   * {@code max-duration}) so an install's existing {@code application.properties}
   * values propagate into the singleton on first start.
   *
   * @return the freshly-seeded or pre-existing {@link SqlTimeseriesConfig}.
   */
  public synchronized SqlTimeseriesConfig seedIfNeeded() {
    SqlTimeseriesConfig existing = dao.findSingleton();
    if (existing != null) {
      Log.debugf("P10c: :SqlTimeseriesConfig already present (appId=%s)", existing.getAppId());
      return existing;
    }
    SqlTimeseriesConfig seed = new SqlTimeseriesConfig();
    // Seed with deploy-time defaults so the singleton's values match the
    // operator's existing application.properties on first migration.
    seed.setMaxRows(defaultMaxRows);
    seed.setMaxDurationIso(defaultMaxDuration);
    SqlTimeseriesConfig saved = dao.createOrUpdate(seed);
    Log.infof("P10c: seeded :SqlTimeseriesConfig singleton (appId=%s, maxRows=%d, maxDuration=%s)",
        saved.getAppId(), saved.getMaxRows(), saved.getMaxDurationIso());
    return saved;
  }

  /**
   * Return the current singleton, seeding from install defaults if absent.
   */
  public SqlTimeseriesConfig current() {
    SqlTimeseriesConfig existing = dao.findSingleton();
    if (existing != null) {
      return existing;
    }
    return seedIfNeeded();
  }

  /**
   * Return the effective row cap — the singleton's {@code maxRows} when
   * non-null, otherwise the deploy-time default.
   *
   * @return effective max rows (always {@literal >} 0).
   */
  public long effectiveMaxRows() {
    SqlTimeseriesConfig cfg = dao.findSingleton();
    if (cfg != null && cfg.getMaxRows() != null) {
      return cfg.getMaxRows();
    }
    return defaultMaxRows;
  }

  /**
   * Return the effective duration cap as an ISO-8601 string — the singleton's
   * {@code maxDurationIso} when non-null, otherwise the deploy-time default.
   *
   * @return effective max duration ISO-8601 string (never null).
   */
  public String effectiveMaxDurationIso() {
    SqlTimeseriesConfig cfg = dao.findSingleton();
    if (cfg != null && cfg.getMaxDurationIso() != null) {
      return cfg.getMaxDurationIso();
    }
    return defaultMaxDuration;
  }

  /**
   * Return the deploy-time default for max rows (for IO layer use).
   */
  public long getDefaultMaxRows() {
    return defaultMaxRows;
  }

  /**
   * Return the deploy-time default for max duration (for IO layer use).
   */
  public String getDefaultMaxDuration() {
    return defaultMaxDuration;
  }

  /**
   * Apply a runtime merge-patch to the singleton. Null arguments are
   * interpreted as "clear the field" (RFC 7396 semantics — reverts to
   * deploy-time default); the REST layer is responsible for distinguishing
   * "absent" from "null" before calling here.
   *
   * @param maxRows        new row cap, or {@code null} to revert to default
   * @param maxDurationIso new duration cap ISO-8601, or {@code null} to revert to default
   * @return the post-patch {@link SqlTimeseriesConfig}.
   */
  public synchronized SqlTimeseriesConfig patch(Long maxRows, String maxDurationIso) {
    SqlTimeseriesConfig cfg = current();
    cfg.setMaxRows(maxRows);
    cfg.setMaxDurationIso(maxDurationIso);
    SqlTimeseriesConfig saved = dao.createOrUpdate(cfg);
    Log.infof("P10c: :SqlTimeseriesConfig patched (maxRows=%s, maxDuration=%s)",
        saved.getMaxRows(), saved.getMaxDurationIso());
    return saved;
  }
}
