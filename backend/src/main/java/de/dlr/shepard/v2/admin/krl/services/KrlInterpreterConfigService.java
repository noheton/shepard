package de.dlr.shepard.v2.admin.krl.services;

import de.dlr.shepard.v2.admin.krl.daos.KrlInterpreterConfigDAO;
import de.dlr.shepard.v2.admin.krl.entities.KrlInterpreterConfigSingleton;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.RequestContextController;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * KRL-CONFIG-1 — service layer for the {@code :KrlInterpreterConfigSingleton}.
 *
 * <p>Responsibilities (same shape as {@code JupyterConfigService} /
 * {@code SqlTimeseriesConfigService} / {@code InstanceRorConfigService}):
 *
 * <ol>
 *   <li><b>Seed on first start.</b> {@link #onStart(StartupEvent)}
 *       observes the Quarkus {@code StartupEvent}; if no
 *       {@code :KrlInterpreterConfigSingleton} node exists yet, one is
 *       seeded from the deploy-time defaults
 *       ({@code shepard.krl.sidecar.url},
 *       {@code shepard.krl.sidecar.timeout-seconds},
 *       {@code shepard.krl.sidecar.max-body-size-mb}).</li>
 *   <li><b>Get-or-seed.</b> {@link #current()} returns the singleton
 *       (seeding if absent — defence-in-depth against a fresh DB that
 *       somehow missed the startup hook).</li>
 *   <li><b>Effective values.</b> {@link #effectiveSidecarUrl()},
 *       {@link #effectiveTimeoutSeconds()}, and
 *       {@link #effectiveMaxBodySizeMb()} return the runtime singleton's
 *       value when set, otherwise the deploy-time default.</li>
 *   <li><b>Merge-patch.</b> {@link #patch(Boolean, String, Integer, Integer)}
 *       applies the runtime-mutable subset of the config per RFC 7396
 *       semantics (null = clear to default for String/Integer fields;
 *       for {@code enabled} the REST layer translates an explicit null
 *       into "leave alone" since the field is non-null in the entity).</li>
 * </ol>
 *
 * <p><b>Fail-soft posture.</b> A seed failure on startup is logged at
 * WARN and does not block boot — effective-value methods fall back to
 * deploy-time defaults when no singleton is reachable, so the sidecar
 * client keeps working with the deploy-time configuration.
 */
@ApplicationScoped
public class KrlInterpreterConfigService {

  @Inject KrlInterpreterConfigDAO dao;

  @Inject RequestContextController requestContextController;

  @ConfigProperty(name = "shepard.krl.sidecar.url", defaultValue = "http://krl-interpreter-sidecar:8000")
  String defaultSidecarUrl;

  @ConfigProperty(name = "shepard.krl.sidecar.timeout-seconds", defaultValue = "120")
  int defaultTimeoutSeconds;

  @ConfigProperty(name = "shepard.krl.sidecar.max-body-size-mb", defaultValue = "16")
  int defaultMaxBodySizeMb;

  /**
   * Deploy-time default for {@code enabled}. True so that once the
   * operator brings the sidecar up, the feature is active without
   * requiring a manual admin enable.
   */
  @ConfigProperty(name = "shepard.krl.enabled", defaultValue = "true")
  boolean defaultEnabled;

  /**
   * Seed the singleton on first startup. Idempotent — re-running sees
   * the existing row and returns. Logged at INFO so an operator can
   * grep startup logs to confirm KRL-CONFIG-1 came up correctly.
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
      // effective*() methods fall back to deploy-time defaults even
      // without a seeded singleton (same fail-soft posture as J1e / P10c).
      Log.warnf(
          e,
          "KRL-CONFIG-1: could not seed :KrlInterpreterConfigSingleton on startup;"
              + " will retry on first admin read");
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
   * <p>Seeds with the deploy-time defaults so an install's existing
   * {@code application.properties} values propagate into the singleton
   * on first start.
   *
   * @return the freshly-seeded or pre-existing {@link KrlInterpreterConfigSingleton}.
   */
  public synchronized KrlInterpreterConfigSingleton seedIfNeeded() {
    KrlInterpreterConfigSingleton existing = dao.findSingleton();
    if (existing != null) {
      Log.debugf(
          "KRL-CONFIG-1: :KrlInterpreterConfigSingleton already present (appId=%s)",
          existing.getAppId());
      return existing;
    }
    KrlInterpreterConfigSingleton seed = new KrlInterpreterConfigSingleton();
    seed.setEnabled(defaultEnabled);
    seed.setSidecarUrl(defaultSidecarUrl.isBlank() ? null : defaultSidecarUrl);
    seed.setTimeoutSeconds(defaultTimeoutSeconds);
    seed.setMaxBodySizeMb(defaultMaxBodySizeMb);
    KrlInterpreterConfigSingleton saved = dao.createOrUpdate(seed);
    Log.infof(
        "KRL-CONFIG-1: seeded :KrlInterpreterConfigSingleton (appId=%s, enabled=%s,"
            + " sidecarUrl=%s, timeoutSeconds=%d, maxBodySizeMb=%d)",
        saved.getAppId(),
        saved.isEnabled(),
        saved.getSidecarUrl(),
        saved.getTimeoutSeconds(),
        saved.getMaxBodySizeMb());
    return saved;
  }

  /**
   * Return the current singleton, seeding from install defaults if absent.
   */
  public KrlInterpreterConfigSingleton current() {
    KrlInterpreterConfigSingleton existing = dao.findSingleton();
    if (existing != null) {
      return existing;
    }
    return seedIfNeeded();
  }

  /**
   * Return the effective enabled flag — the singleton's value when
   * present, otherwise the deploy-time default. Fail-soft on storage
   * unavailability: returns {@code true} when neither path resolves
   * (sidecar is active by default).
   */
  public boolean effectiveEnabled() {
    try {
      KrlInterpreterConfigSingleton cfg = dao.findSingleton();
      if (cfg != null) {
        return cfg.isEnabled();
      }
      return defaultEnabled;
    } catch (RuntimeException e) {
      Log.warnf(e, "KRL-CONFIG-1: effectiveEnabled() falling back to default on storage error");
      return defaultEnabled;
    }
  }

  /**
   * Return the effective sidecar URL — the singleton's value when
   * non-null/non-blank, otherwise the deploy-time default
   * ({@code shepard.krl.sidecar.url}). Returns the deploy-time default
   * when neither source provides a URL.
   */
  public String effectiveSidecarUrl() {
    try {
      KrlInterpreterConfigSingleton cfg = dao.findSingleton();
      if (cfg != null && cfg.getSidecarUrl() != null && !cfg.getSidecarUrl().isBlank()) {
        return cfg.getSidecarUrl();
      }
    } catch (RuntimeException e) {
      Log.warnf(
          e, "KRL-CONFIG-1: effectiveSidecarUrl() falling back to deploy-time default on"
              + " storage error");
    }
    return defaultSidecarUrl;
  }

  /**
   * Return the effective timeout in seconds — the singleton's value when
   * positive, otherwise the deploy-time default
   * ({@code shepard.krl.sidecar.timeout-seconds}).
   */
  public int effectiveTimeoutSeconds() {
    try {
      KrlInterpreterConfigSingleton cfg = dao.findSingleton();
      if (cfg != null && cfg.getTimeoutSeconds() > 0) {
        return cfg.getTimeoutSeconds();
      }
    } catch (RuntimeException e) {
      Log.warnf(
          e,
          "KRL-CONFIG-1: effectiveTimeoutSeconds() falling back to deploy-time default on"
              + " storage error");
    }
    return defaultTimeoutSeconds;
  }

  /**
   * Return the effective max body size in megabytes — the singleton's
   * value when positive, otherwise the deploy-time default
   * ({@code shepard.krl.sidecar.max-body-size-mb}).
   */
  public int effectiveMaxBodySizeMb() {
    try {
      KrlInterpreterConfigSingleton cfg = dao.findSingleton();
      if (cfg != null && cfg.getMaxBodySizeMb() > 0) {
        return cfg.getMaxBodySizeMb();
      }
    } catch (RuntimeException e) {
      Log.warnf(
          e,
          "KRL-CONFIG-1: effectiveMaxBodySizeMb() falling back to deploy-time default on"
              + " storage error");
    }
    return defaultMaxBodySizeMb;
  }

  /** Return the deploy-time default for {@code enabled} (for IO layer use). */
  public boolean getDefaultEnabled() {
    return defaultEnabled;
  }

  /** Return the deploy-time default for {@code sidecarUrl} (for IO layer use). */
  public String getDefaultSidecarUrl() {
    return defaultSidecarUrl.isBlank() ? null : defaultSidecarUrl;
  }

  /** Return the deploy-time default for {@code timeoutSeconds} (for IO layer use). */
  public int getDefaultTimeoutSeconds() {
    return defaultTimeoutSeconds;
  }

  /** Return the deploy-time default for {@code maxBodySizeMb} (for IO layer use). */
  public int getDefaultMaxBodySizeMb() {
    return defaultMaxBodySizeMb;
  }

  /**
   * Apply a runtime merge-patch to the singleton. The REST layer is
   * responsible for distinguishing "absent" from "explicit null" before
   * calling here, per RFC 7396 semantics.
   *
   * @param enabled         new enabled flag (caller-resolved per RFC 7396), or {@code null}
   *                        to leave unchanged.
   * @param sidecarUrl      new sidecar URL, or {@code null} to revert to deploy-time default.
   * @param timeoutSeconds  new timeout in seconds, or {@code null} / zero to revert to default.
   * @param maxBodySizeMb   new max body size in MiB, or {@code null} / zero to revert to default.
   * @return the post-patch {@link KrlInterpreterConfigSingleton}.
   */
  public synchronized KrlInterpreterConfigSingleton patch(
      Boolean enabled, String sidecarUrl, Integer timeoutSeconds, Integer maxBodySizeMb) {
    KrlInterpreterConfigSingleton cfg = current();
    if (enabled != null) {
      cfg.setEnabled(enabled);
    }
    // null sidecarUrl = clear (revert to deploy-time default at resolution time)
    cfg.setSidecarUrl(
        (sidecarUrl == null || sidecarUrl.isBlank()) ? null : sidecarUrl);
    // null or zero timeout = revert to default
    cfg.setTimeoutSeconds(
        (timeoutSeconds == null || timeoutSeconds <= 0) ? 0 : timeoutSeconds);
    // null or zero maxBodySizeMb = revert to default
    cfg.setMaxBodySizeMb(
        (maxBodySizeMb == null || maxBodySizeMb <= 0) ? 0 : maxBodySizeMb);
    KrlInterpreterConfigSingleton saved = dao.createOrUpdate(cfg);
    Log.infof(
        "KRL-CONFIG-1: :KrlInterpreterConfigSingleton patched (enabled=%s, sidecarUrl=%s,"
            + " timeoutSeconds=%d, maxBodySizeMb=%d)",
        saved.isEnabled(),
        saved.getSidecarUrl(),
        saved.getTimeoutSeconds(),
        saved.getMaxBodySizeMb());
    return saved;
  }

  /**
   * Return an {@link Optional} containing the effective sidecar URL, or
   * empty when neither the singleton nor the deploy-time default
   * provides one. Convenience method for callers that need to check
   * "is a URL configured at all?" before making a call.
   */
  public Optional<String> effectiveSidecarUrlOpt() {
    String url = effectiveSidecarUrl();
    return (url == null || url.isBlank()) ? Optional.empty() : Optional.of(url);
  }
}
