package de.dlr.shepard.v2.admin.storage.services;

import de.dlr.shepard.v2.admin.storage.daos.AutosweepConfigDAO;
import de.dlr.shepard.v2.admin.storage.entities.AutosweepConfig;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.RequestContextController;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * FTOGGLE-AUTOSWEEP-1 — service layer for the {@code :AutosweepConfig} singleton.
 *
 * <p>Seeds the singleton from deploy-time defaults on startup. Exposes
 * effective-value methods consumed by {@code FileMigrationService.autoSweep()}.
 * Supports runtime merge-patch via {@link #patch(Boolean, String, String)}.
 *
 * <p>Mirrors the {@code ProvenanceConfigService} startup pattern exactly.
 */
@ApplicationScoped
public class AutosweepConfigService {

  @Inject
  AutosweepConfigDAO dao;

  @Inject
  RequestContextController requestContextController;

  @ConfigProperty(name = "shepard.migration.auto-sweep.enabled", defaultValue = "false")
  boolean defaultEnabled;

  @ConfigProperty(name = "shepard.migration.auto-sweep.source", defaultValue = "")
  String defaultSource;

  @ConfigProperty(name = "shepard.migration.auto-sweep.target", defaultValue = "")
  String defaultTarget;

  void onStart(@Observes StartupEvent event) {
    boolean activated = requestContextController.activate();
    try {
      seedIfNeeded();
    } catch (RuntimeException e) {
      Log.warnf(e, "FTOGGLE-AUTOSWEEP-1: could not seed :AutosweepConfig on startup; will retry on first admin read");
    } finally {
      if (activated) {
        requestContextController.deactivate();
      }
    }
  }

  public synchronized AutosweepConfig seedIfNeeded() {
    AutosweepConfig existing = dao.findSingleton();
    if (existing != null) {
      Log.debugf("FTOGGLE-AUTOSWEEP-1: :AutosweepConfig already present (appId=%s)", existing.getAppId());
      return existing;
    }
    AutosweepConfig seed = new AutosweepConfig();
    seed.setEnabled(defaultEnabled);
    seed.setSource(defaultSource.isBlank() ? null : defaultSource);
    seed.setTarget(defaultTarget.isBlank() ? null : defaultTarget);
    AutosweepConfig saved = dao.createOrUpdate(seed);
    Log.infof(
      "FTOGGLE-AUTOSWEEP-1: seeded :AutosweepConfig singleton (appId=%s, enabled=%b, source=%s, target=%s)",
      saved.getAppId(), saved.getEnabled(), saved.getSource(), saved.getTarget());
    return saved;
  }

  public AutosweepConfig current() {
    AutosweepConfig existing = dao.findSingleton();
    if (existing != null) {
      return existing;
    }
    return seedIfNeeded();
  }

  /** Effective enabled flag — singleton value when non-null, else deploy-time default. */
  public boolean effectiveEnabled() {
    AutosweepConfig cfg = dao.findSingleton();
    if (cfg != null && cfg.getEnabled() != null) {
      return cfg.getEnabled();
    }
    return defaultEnabled;
  }

  /** Effective source adapter id — singleton value when non-null, else deploy-time default. */
  public String effectiveSource() {
    AutosweepConfig cfg = dao.findSingleton();
    if (cfg != null && cfg.getSource() != null) {
      return cfg.getSource();
    }
    return defaultSource;
  }

  /** Effective target adapter id — singleton value when non-null, else deploy-time default. */
  public String effectiveTarget() {
    AutosweepConfig cfg = dao.findSingleton();
    if (cfg != null && cfg.getTarget() != null) {
      return cfg.getTarget();
    }
    return defaultTarget;
  }

  public boolean getDefaultEnabled() {
    return defaultEnabled;
  }

  public String getDefaultSource() {
    return defaultSource;
  }

  public String getDefaultTarget() {
    return defaultTarget;
  }

  public synchronized AutosweepConfig patch(Boolean enabled, String source, String target) {
    AutosweepConfig cfg = current();
    cfg.setEnabled(enabled);
    cfg.setSource(source);
    cfg.setTarget(target);
    AutosweepConfig saved = dao.createOrUpdate(cfg);
    Log.infof(
      "FTOGGLE-AUTOSWEEP-1: :AutosweepConfig patched (enabled=%s, source=%s, target=%s)",
      saved.getEnabled(), saved.getSource(), saved.getTarget());
    return saved;
  }
}
