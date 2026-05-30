package de.dlr.shepard.v2.krl.services;

import de.dlr.shepard.v2.krl.config.KrlInterpreterConfig;
import de.dlr.shepard.v2.krl.daos.KrlInterpreterConfigDAO;
import de.dlr.shepard.v2.krl.entities.KrlInterpreterConfigEntity;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.RequestContextController;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * KRL-CONFIG-1 — service layer for the {@code :KrlInterpreterConfigEntity}
 * singleton.
 *
 * <p>Responsibilities (same shape as {@code JupyterConfigService}):
 *
 * <ol>
 *   <li><b>Seed on first start.</b> {@link #onStart(StartupEvent)}
 *       observes the Quarkus {@code StartupEvent}; if no
 *       {@code :KrlInterpreterConfigEntity} node exists yet, one is
 *       seeded from the deploy-time defaults in
 *       {@link KrlInterpreterConfig}.</li>
 *   <li><b>Get-or-seed.</b> {@link #current()} returns the singleton
 *       (seeding if absent — defence-in-depth).</li>
 *   <li><b>Effective values.</b> {@link #effectiveSidecarUrl()},
 *       {@link #effectiveTimeoutSeconds()}, and
 *       {@link #effectiveMaxBodySizeMb()} return the runtime
 *       singleton's value when non-null, otherwise the deploy-time
 *       default from the injected {@link KrlInterpreterConfig} CDI
 *       bean.</li>
 *   <li><b>Merge-patch.</b>
 *       {@link #patch(String, Integer, Integer)} applies the
 *       runtime-mutable subset of the config per RFC 7396 semantics
 *       (null = clear to default — service writes null into the entity,
 *       effective-value methods coalesce it back to the deploy-time
 *       default at read time).</li>
 * </ol>
 *
 * <p><b>Fail-soft posture.</b> A seed failure on startup is logged at
 * WARN and does not block boot — the effective-value methods fall back
 * to the deploy-time {@link KrlInterpreterConfig} bean, so the sidecar
 * continues using its baked-in defaults even when the singleton is
 * temporarily unavailable.
 */
@ApplicationScoped
public class KrlInterpreterConfigService {

  @Inject
  KrlInterpreterConfigDAO dao;

  @Inject
  RequestContextController requestContextController;

  /** Deploy-time bean — source of tier-1 defaults. */
  @Inject
  KrlInterpreterConfig deployTimeConfig;

  /**
   * Seed the singleton on first startup. Idempotent — re-running sees
   * the existing row and returns. Logged at INFO so an operator can
   * grep startup logs to confirm KRL-CONFIG-1 came up correctly.
   *
   * <p>The seed runs lazily-scoped (Arc's
   * {@link RequestContextController}) so the DAO's request-scoped
   * machinery has a context to bind to even though the
   * {@code StartupEvent} fires outside a JAX-RS request.
   */
  void onStart(@Observes StartupEvent event) {
    boolean activated = requestContextController.activate();
    try {
      seedIfNeeded();
    } catch (RuntimeException e) {
      // Seeding failure must not block startup — operators get a WARN.
      // effective*() methods fall back to deploy-time defaults even
      // without a seeded singleton (same fail-soft posture as J1e).
      Log.warnf(e,
        "KRL-CONFIG-1: could not seed :KrlInterpreterConfigEntity on startup; "
        + "will retry on first admin read");
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
   * on first start. All three fields are seeded as {@code null}
   * (meaning "use deploy-time default") — the effective-value methods
   * coalesce to the CDI bean values at read time.
   *
   * @return the freshly-seeded or pre-existing
   *         {@link KrlInterpreterConfigEntity}.
   */
  public synchronized KrlInterpreterConfigEntity seedIfNeeded() {
    KrlInterpreterConfigEntity existing = dao.findSingleton();
    if (existing != null) {
      Log.debugf("KRL-CONFIG-1: :KrlInterpreterConfigEntity already present (appId=%s)",
        existing.getAppId());
      return existing;
    }
    KrlInterpreterConfigEntity seed = new KrlInterpreterConfigEntity();
    // Seed with null runtime values → effective methods return deploy-time defaults.
    seed.setSidecarUrl(null);
    seed.setTimeoutSeconds(null);
    seed.setMaxBodySizeMb(null);
    KrlInterpreterConfigEntity saved = dao.createOrUpdate(seed);
    Log.infof("KRL-CONFIG-1: seeded :KrlInterpreterConfigEntity singleton (appId=%s)",
      saved.getAppId());
    return saved;
  }

  /**
   * Return the current singleton, seeding from install defaults if absent.
   */
  public KrlInterpreterConfigEntity current() {
    KrlInterpreterConfigEntity existing = dao.findSingleton();
    if (existing != null) {
      return existing;
    }
    return seedIfNeeded();
  }

  /**
   * Return the effective sidecar URL — the singleton's value when
   * non-null/non-blank, otherwise the deploy-time default.
   * Fail-soft on storage unavailability: returns the deploy-time default.
   */
  public String effectiveSidecarUrl() {
    try {
      KrlInterpreterConfigEntity cfg = dao.findSingleton();
      if (cfg != null && cfg.getSidecarUrl() != null && !cfg.getSidecarUrl().isBlank()) {
        return cfg.getSidecarUrl();
      }
    } catch (RuntimeException e) {
      Log.warnf(e,
        "KRL-CONFIG-1: effectiveSidecarUrl() falling back to deploy-time default on storage error");
    }
    return deployTimeConfig.getSidecarUrl();
  }

  /**
   * Return the effective timeout in seconds — the singleton's value when
   * non-null, otherwise the deploy-time default.
   * Fail-soft on storage unavailability: returns the deploy-time default.
   */
  public int effectiveTimeoutSeconds() {
    try {
      KrlInterpreterConfigEntity cfg = dao.findSingleton();
      if (cfg != null && cfg.getTimeoutSeconds() != null) {
        return cfg.getTimeoutSeconds();
      }
    } catch (RuntimeException e) {
      Log.warnf(e,
        "KRL-CONFIG-1: effectiveTimeoutSeconds() falling back to deploy-time default on storage error");
    }
    return deployTimeConfig.getTimeoutSeconds();
  }

  /**
   * Return the effective max body size in MB — the singleton's value when
   * non-null, otherwise the deploy-time default.
   * Fail-soft on storage unavailability: returns the deploy-time default.
   */
  public int effectiveMaxBodySizeMb() {
    try {
      KrlInterpreterConfigEntity cfg = dao.findSingleton();
      if (cfg != null && cfg.getMaxBodySizeMb() != null) {
        return cfg.getMaxBodySizeMb();
      }
    } catch (RuntimeException e) {
      Log.warnf(e,
        "KRL-CONFIG-1: effectiveMaxBodySizeMb() falling back to deploy-time default on storage error");
    }
    return deployTimeConfig.getMaxBodySizeMb();
  }

  // ── deploy-time default accessors (for IO layer use) ─────────────────────

  /** Return the deploy-time default for {@code sidecarUrl}. */
  public String getDefaultSidecarUrl() {
    return deployTimeConfig.getSidecarUrl();
  }

  /** Return the deploy-time default for {@code timeoutSeconds}. */
  public int getDefaultTimeoutSeconds() {
    return deployTimeConfig.getTimeoutSeconds();
  }

  /** Return the deploy-time default for {@code maxBodySizeMb}. */
  public int getDefaultMaxBodySizeMb() {
    return deployTimeConfig.getMaxBodySizeMb();
  }

  /**
   * Apply a runtime merge-patch to the singleton. Null arguments are
   * interpreted as "clear the field" (RFC 7396 semantics — reverts to
   * deploy-time default). The REST layer is responsible for
   * distinguishing "absent" from "explicit null" before calling here.
   *
   * @param sidecarUrl     new sidecar URL, or {@code null} to revert
   * @param timeoutSeconds new timeout, or {@code null} to revert
   * @param maxBodySizeMb  new max body size, or {@code null} to revert
   * @return the post-patch {@link KrlInterpreterConfigEntity}.
   */
  public synchronized KrlInterpreterConfigEntity patch(
    String sidecarUrl,
    Integer timeoutSeconds,
    Integer maxBodySizeMb
  ) {
    KrlInterpreterConfigEntity cfg = current();
    cfg.setSidecarUrl(sidecarUrl);
    cfg.setTimeoutSeconds(timeoutSeconds);
    cfg.setMaxBodySizeMb(maxBodySizeMb);
    KrlInterpreterConfigEntity saved = dao.createOrUpdate(cfg);
    Log.infof(
      "KRL-CONFIG-1: :KrlInterpreterConfigEntity patched "
      + "(sidecarUrl=%s, timeoutSeconds=%s, maxBodySizeMb=%s)",
      saved.getSidecarUrl(), saved.getTimeoutSeconds(), saved.getMaxBodySizeMb());
    return saved;
  }
}
