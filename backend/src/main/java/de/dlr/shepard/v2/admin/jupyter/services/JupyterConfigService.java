package de.dlr.shepard.v2.admin.jupyter.services;

import de.dlr.shepard.v2.admin.jupyter.daos.JupyterConfigDAO;
import de.dlr.shepard.v2.admin.jupyter.entities.JupyterConfig;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.RequestContextController;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * J1e — service layer for the {@code :JupyterConfig} singleton.
 *
 * <p>Responsibilities (same shape as
 * {@code SqlTimeseriesConfigService} / {@code InstanceRorConfigService}):
 *
 * <ol>
 *   <li><b>Seed on first start.</b> {@link #onStart(StartupEvent)}
 *       observes the Quarkus {@code StartupEvent}; if no
 *       {@code :JupyterConfig} node exists yet, one is seeded from the
 *       deploy-time defaults ({@code shepard.jupyter.enabled} and
 *       {@code shepard.jupyter.hub-url}). Pre-J1e installs upgrading
 *       get the singleton minted on their next restart with the
 *       feature OFF and no hub URL configured.</li>
 *   <li><b>Get-or-seed.</b> {@link #current()} returns the singleton
 *       (seeding if absent — defence-in-depth against a fresh DB that
 *       somehow missed the startup hook).</li>
 *   <li><b>Effective values.</b> {@link #effectiveEnabled()} and
 *       {@link #effectiveHubUrl()} return the runtime singleton's
 *       value when set, otherwise the deploy-time default.</li>
 *   <li><b>Merge-patch.</b> {@link #patch(Boolean, String)} applies
 *       the runtime-mutable subset of the config per RFC 7396
 *       semantics (null = clear to default for the {@code hubUrl}
 *       field; for {@code enabled} the REST layer translates an
 *       explicit null into "leave alone" since the field is non-null
 *       in the entity).</li>
 * </ol>
 *
 * <p><b>Fail-soft posture.</b> A seed failure on startup is logged at
 * WARN and does not block boot — the launch-button visibility gate is
 * a no-op (returns {@code false}) when no singleton is reachable, so
 * the affordance simply stays hidden until the operator triggers a
 * retry by hitting the admin endpoint.
 */
@ApplicationScoped
public class JupyterConfigService {

  @Inject
  JupyterConfigDAO dao;

  @Inject
  RequestContextController requestContextController;

  @ConfigProperty(name = "shepard.jupyter.enabled", defaultValue = "false")
  boolean defaultEnabled;

  /**
   * Deploy-time default for the JupyterHub base URL. Sourced from
   * {@code shepard.jupyter.hub-url}; if unset, the {@link Optional}
   * resolves to empty (no URL).
   */
  @ConfigProperty(name = "shepard.jupyter.hub-url")
  Optional<String> defaultHubUrl;

  /**
   * Seed the singleton on first startup. Idempotent — re-running sees
   * the existing row and returns. Logged at INFO so an operator can
   * grep startup logs to confirm J1e came up correctly.
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
      // effectiveEnabled() / effectiveHubUrl() fall back to deploy-time
      // defaults even without a seeded singleton (same fail-soft posture
      // as ROR1 / P10c).
      Log.warnf(e, "J1e: could not seed :JupyterConfig on startup; will retry on first admin read");
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
   * @return the freshly-seeded or pre-existing {@link JupyterConfig}.
   */
  public synchronized JupyterConfig seedIfNeeded() {
    JupyterConfig existing = dao.findSingleton();
    if (existing != null) {
      Log.debugf("J1e: :JupyterConfig already present (appId=%s)", existing.getAppId());
      return existing;
    }
    JupyterConfig seed = new JupyterConfig();
    seed.setEnabled(defaultEnabled);
    seed.setHubUrl(defaultHubUrl.filter(s -> !s.isBlank()).orElse(null));
    JupyterConfig saved = dao.createOrUpdate(seed);
    Log.infof("J1e: seeded :JupyterConfig singleton (appId=%s, enabled=%s, hubUrl=%s)",
        saved.getAppId(), saved.isEnabled(), saved.getHubUrl());
    return saved;
  }

  /**
   * Return the current singleton, seeding from install defaults if absent.
   */
  public JupyterConfig current() {
    JupyterConfig existing = dao.findSingleton();
    if (existing != null) {
      return existing;
    }
    return seedIfNeeded();
  }

  /**
   * Return the effective enabled flag — the singleton's value when
   * present, otherwise the deploy-time default. Fail-soft on storage
   * unavailability: returns {@code false} when neither path resolves.
   */
  public boolean effectiveEnabled() {
    try {
      JupyterConfig cfg = dao.findSingleton();
      if (cfg != null) {
        return cfg.isEnabled();
      }
      return defaultEnabled;
    } catch (RuntimeException e) {
      Log.warnf(e, "J1e: effectiveEnabled() falling back to false on storage error");
      return false;
    }
  }

  /**
   * Return the effective JupyterHub base URL — the singleton's value
   * when non-null/non-blank, otherwise the deploy-time default
   * ({@code shepard.jupyter.hub-url}). Returns {@code null} when neither
   * source provides a URL.
   */
  public String effectiveHubUrl() {
    try {
      JupyterConfig cfg = dao.findSingleton();
      if (cfg != null && cfg.getHubUrl() != null && !cfg.getHubUrl().isBlank()) {
        return cfg.getHubUrl();
      }
    } catch (RuntimeException e) {
      Log.warnf(e, "J1e: effectiveHubUrl() falling back to deploy-time default on storage error");
    }
    return defaultHubUrl.filter(s -> !s.isBlank()).orElse(null);
  }

  /** Return the deploy-time default for {@code enabled} (for IO layer use). */
  public boolean getDefaultEnabled() {
    return defaultEnabled;
  }

  /** Return the deploy-time default for {@code hubUrl} (for IO layer use). */
  public String getDefaultHubUrl() {
    return defaultHubUrl.filter(s -> !s.isBlank()).orElse(null);
  }

  /**
   * Apply a runtime merge-patch to the singleton. Null arguments are
   * interpreted as "clear the field" for {@code hubUrl} (RFC 7396
   * semantics — reverts to deploy-time default); {@code enabled} is a
   * primitive boolean and is replaced unconditionally with the value
   * the REST layer resolved. The REST layer is responsible for
   * distinguishing "absent" from "explicit null" before calling here.
   *
   * @param enabled new enabled flag (caller-resolved per RFC 7396).
   * @param hubUrl  new hub URL, or {@code null} to revert to default.
   * @return the post-patch {@link JupyterConfig}.
   */
  public synchronized JupyterConfig patch(boolean enabled, String hubUrl) {
    JupyterConfig cfg = current();
    cfg.setEnabled(enabled);
    cfg.setHubUrl(hubUrl);
    JupyterConfig saved = dao.createOrUpdate(cfg);
    Log.infof("J1e: :JupyterConfig patched (enabled=%s, hubUrl=%s)",
        saved.isEnabled(), saved.getHubUrl());
    return saved;
  }
}
