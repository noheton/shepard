package de.dlr.shepard.data.hdf.services;

import de.dlr.shepard.data.hdf.daos.HdfConfigDAO;
import de.dlr.shepard.data.hdf.entities.HdfConfig;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.RequestContextController;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * FTOGGLE-HDF-ENABLE-1 — service layer for the {@link HdfConfig} singleton.
 *
 * <p>Mirrors {@code SqlTimeseriesConfigService} exactly:
 * <ol>
 *   <li>Seeds the singleton on first startup from the deploy-time default.</li>
 *   <li>{@link #current()} returns the singleton (seeding if absent).</li>
 *   <li>{@link #effectiveEnabled()} returns the runtime value when non-null,
 *       falling back to the deploy-time default.</li>
 *   <li>{@link #patch(Boolean)} applies RFC 7396 merge-patch semantics
 *       (null = clear to default).</li>
 * </ol>
 */
@ApplicationScoped
public class HdfConfigService {

  @Inject
  HdfConfigDAO dao;

  @Inject
  RequestContextController requestContextController;

  @ConfigProperty(name = "shepard.hdf.enabled", defaultValue = "true")
  boolean defaultEnabled;

  void onStart(@Observes StartupEvent event) {
    boolean activated = requestContextController.activate();
    try {
      seedIfNeeded();
    } catch (RuntimeException e) {
      Log.warnf(e, "FTOGGLE-HDF-ENABLE-1: could not seed :HdfConfig on startup; will retry on first admin read");
    } finally {
      if (activated) {
        requestContextController.deactivate();
      }
    }
  }

  public synchronized HdfConfig seedIfNeeded() {
    HdfConfig existing = dao.findSingleton();
    if (existing != null) {
      Log.debugf("FTOGGLE-HDF-ENABLE-1: :HdfConfig already present (appId=%s)", existing.getAppId());
      return existing;
    }
    HdfConfig seed = new HdfConfig();
    seed.setEnabled(defaultEnabled);
    HdfConfig saved = dao.createOrUpdate(seed);
    Log.infof("FTOGGLE-HDF-ENABLE-1: seeded :HdfConfig singleton (appId=%s, enabled=%b)",
        saved.getAppId(), saved.getEnabled());
    return saved;
  }

  public HdfConfig current() {
    HdfConfig existing = dao.findSingleton();
    if (existing != null) return existing;
    return seedIfNeeded();
  }

  public boolean effectiveEnabled() {
    HdfConfig cfg = dao.findSingleton();
    if (cfg != null && cfg.getEnabled() != null) {
      return cfg.getEnabled();
    }
    return defaultEnabled;
  }

  public boolean getDefaultEnabled() {
    return defaultEnabled;
  }

  public synchronized HdfConfig patch(Boolean enabled) {
    HdfConfig cfg = current();
    cfg.setEnabled(enabled);
    HdfConfig saved = dao.createOrUpdate(cfg);
    Log.infof("FTOGGLE-HDF-ENABLE-1: :HdfConfig patched (enabled=%s)", saved.getEnabled());
    return saved;
  }
}
