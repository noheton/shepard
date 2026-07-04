package de.dlr.shepard.v2.admin.thermography.services;

import de.dlr.shepard.v2.admin.thermography.daos.ThermographyConfigDAO;
import de.dlr.shepard.v2.admin.thermography.entities.ThermographyConfig;
import de.dlr.shepard.v2.admin.thermography.io.ThermographyConfigIO;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.RequestContextController;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * MFFD-NDT-ADMIN-CONFIG-1 — service layer for the {@code :ThermographyConfig} singleton.
 *
 * <p>Responsibilities (same shape as {@code JupyterConfigService}):
 *
 * <ol>
 *   <li><b>Seed on first start.</b> {@link #onStart(StartupEvent)}
 *       observes the Quarkus {@code StartupEvent}; if no
 *       {@code :ThermographyConfig} node exists yet, one is seeded from
 *       the deploy-time defaults ({@code shepard.v2.thermography.threshold-c},
 *       {@code shepard.v2.thermography.grid-width},
 *       {@code shepard.v2.thermography.grid-height}). Pre-MFFD-NDT-ADMIN-CONFIG-1
 *       installs upgrading get the singleton minted on their next restart
 *       with the deploy-time defaults propagated in.</li>
 *   <li><b>Get-or-seed.</b> {@link #current()} returns the singleton
 *       (seeding if absent — defence-in-depth).</li>
 *   <li><b>Effective config IO.</b> {@link #getConfig()} projects the
 *       singleton onto a fully-resolved {@link ThermographyConfigIO}
 *       with all null fields replaced by deploy-time defaults.</li>
 *   <li><b>Merge-patch.</b> {@link #patchConfig(Double, Integer, Integer)}
 *       applies the runtime-mutable subset per RFC 7396 semantics
 *       (null = revert field to deploy-time default).</li>
 * </ol>
 *
 * <p><b>Fail-soft posture.</b> A seed failure on startup is logged at
 * WARN and does not block boot — the analysis service falls back to the
 * {@code @ConfigProperty} deploy-time defaults when the singleton is
 * unreachable, so thermography analysis still works without the singleton.
 */
@ApplicationScoped
public class ThermographyConfigService {

  @Inject
  ThermographyConfigDAO dao;

  @Inject
  RequestContextController requestContextController;

  @ConfigProperty(name = "shepard.v2.thermography.threshold-c", defaultValue = "80.0")
  double defaultThresholdC;

  @ConfigProperty(name = "shepard.v2.thermography.grid-width", defaultValue = "64")
  int defaultGridWidth;

  @ConfigProperty(name = "shepard.v2.thermography.grid-height", defaultValue = "64")
  int defaultGridHeight;

  /**
   * Seed the singleton on first startup. Idempotent — re-running sees
   * the existing row and returns. Logged at INFO so an operator can
   * grep startup logs to confirm MFFD-NDT-ADMIN-CONFIG-1 came up correctly.
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
      // ThermographyAnalysisService falls back to @ConfigProperty deploy-time
      // defaults when the singleton is unavailable (same fail-soft posture
      // as J1e / ROR1 / P10c).
      Log.warnf(e,
        "MFFD-NDT-ADMIN-CONFIG-1: could not seed :ThermographyConfig on startup; " +
        "analysis will use deploy-time defaults until next admin read");
    } finally {
      if (activated) {
        requestContextController.deactivate();
      }
    }
  }

  /**
   * Seed the singleton if it doesn't exist yet. Public for tests +
   * defence-in-depth callers.
   *
   * <p>Seeds with the deploy-time defaults so an install's existing
   * {@code application.properties} values propagate into the singleton
   * on first start.
   *
   * @return the freshly-seeded or pre-existing {@link ThermographyConfig}.
   */
  public synchronized ThermographyConfig seedIfNeeded() {
    return dao.findSingleton().orElseGet(() -> {
      ThermographyConfig seed = new ThermographyConfig();
      // Seed with null values — the IO layer resolves against deploy-time
      // defaults, so null in the singleton means "runtime not yet overridden".
      seed.setThresholdC(null);
      seed.setGridWidth(null);
      seed.setGridHeight(null);
      ThermographyConfig saved = dao.createOrUpdate(seed);
      Log.infof(
        "MFFD-NDT-ADMIN-CONFIG-1: seeded :ThermographyConfig singleton (appId=%s, " +
        "thresholdC=<default %.1f>, gridWidth=<default %d>, gridHeight=<default %d>)",
        saved.getAppId(), defaultThresholdC, defaultGridWidth, defaultGridHeight);
      return saved;
    });
  }

  /**
   * Return the current singleton, seeding from install defaults if absent.
   */
  public ThermographyConfig current() {
    return dao.findSingleton().orElseGet(this::seedIfNeeded);
  }

  /**
   * Return the fully-resolved config IO — null fields in the singleton are
   * replaced by deploy-time defaults so callers always get an effective value.
   */
  public ThermographyConfigIO getConfig() {
    ThermographyConfig cfg = current();
    return ThermographyConfigIO.from(cfg, defaultThresholdC, defaultGridWidth, defaultGridHeight);
  }

  /**
   * Apply a runtime merge-patch to the singleton. Null arguments mean
   * "revert that field to deploy-time default" (RFC 7396 semantics).
   *
   * @param thresholdC new threshold in °C, or {@code null} to revert to default
   * @param gridWidth  new grid column count, or {@code null} to revert to default
   * @param gridHeight new grid row count, or {@code null} to revert to default
   * @return the post-patch fully-resolved {@link ThermographyConfigIO}
   */
  public synchronized ThermographyConfigIO patchConfig(
    Double thresholdC,
    Integer gridWidth,
    Integer gridHeight
  ) {
    ThermographyConfig cfg = current();
    cfg.setThresholdC(thresholdC);
    cfg.setGridWidth(gridWidth);
    cfg.setGridHeight(gridHeight);
    ThermographyConfig saved = dao.createOrUpdate(cfg);
    Log.infof(
      "MFFD-NDT-ADMIN-CONFIG-1: :ThermographyConfig patched " +
      "(thresholdC=%s, gridWidth=%s, gridHeight=%s)",
      saved.getThresholdC(), saved.getGridWidth(), saved.getGridHeight());
    return ThermographyConfigIO.from(saved, defaultThresholdC, defaultGridWidth, defaultGridHeight);
  }

  /** Return the deploy-time default threshold (for IO layer use). */
  public double getDefaultThresholdC() {
    return defaultThresholdC;
  }

  /** Return the deploy-time default grid width (for IO layer use). */
  public int getDefaultGridWidth() {
    return defaultGridWidth;
  }

  /** Return the deploy-time default grid height (for IO layer use). */
  public int getDefaultGridHeight() {
    return defaultGridHeight;
  }
}
