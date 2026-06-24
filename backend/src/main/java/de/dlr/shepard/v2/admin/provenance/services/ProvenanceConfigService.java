package de.dlr.shepard.v2.admin.provenance.services;

import de.dlr.shepard.v2.admin.provenance.daos.ProvenanceConfigDAO;
import de.dlr.shepard.v2.admin.provenance.entities.ProvenanceConfig;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.RequestContextController;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * FTOGGLE-PROV-1 — service layer for the {@code :ProvenanceConfig} singleton.
 *
 * <p>Seeds the singleton from deploy-time defaults on startup. Exposes
 * effective-value methods consumed by {@code ProvenanceCaptureFilter} and
 * {@code ProvenanceRetentionJob}. Supports runtime merge-patch via
 * {@link #patch(Boolean, Boolean, Long)}.
 *
 * <p>Mirrors the {@code SqlTimeseriesConfigService} startup pattern exactly.
 */
@ApplicationScoped
public class ProvenanceConfigService {

  @Inject
  ProvenanceConfigDAO dao;

  @Inject
  RequestContextController requestContextController;

  @ConfigProperty(name = "shepard.provenance.enabled", defaultValue = "true")
  boolean defaultEnabled;

  @ConfigProperty(name = "shepard.provenance.capture-reads", defaultValue = "false")
  boolean defaultCaptureReads;

  @ConfigProperty(name = "shepard.provenance.retention-days", defaultValue = "730")
  long defaultRetentionDays;

  void onStart(@Observes StartupEvent event) {
    boolean activated = requestContextController.activate();
    try {
      seedIfNeeded();
    } catch (RuntimeException e) {
      Log.warnf(e, "FTOGGLE-PROV-1: could not seed :ProvenanceConfig on startup; will retry on first admin read");
    } finally {
      if (activated) {
        requestContextController.deactivate();
      }
    }
  }

  public synchronized ProvenanceConfig seedIfNeeded() {
    ProvenanceConfig existing = dao.findSingleton();
    if (existing != null) {
      Log.debugf("FTOGGLE-PROV-1: :ProvenanceConfig already present (appId=%s)", existing.getAppId());
      return existing;
    }
    ProvenanceConfig seed = new ProvenanceConfig();
    seed.setEnabled(defaultEnabled);
    seed.setCaptureReads(defaultCaptureReads);
    seed.setRetentionDays(defaultRetentionDays);
    ProvenanceConfig saved = dao.createOrUpdate(seed);
    Log.infof(
      "FTOGGLE-PROV-1: seeded :ProvenanceConfig singleton (appId=%s, enabled=%b, captureReads=%b, retentionDays=%d)",
      saved.getAppId(), saved.getEnabled(), saved.getCaptureReads(), saved.getRetentionDays());
    return saved;
  }

  public ProvenanceConfig current() {
    ProvenanceConfig existing = dao.findSingleton();
    if (existing != null) {
      return existing;
    }
    return seedIfNeeded();
  }

  /** Effective master-switch — singleton value when non-null, else deploy-time default. */
  public boolean effectiveEnabled() {
    ProvenanceConfig cfg = dao.findSingleton();
    if (cfg != null && cfg.getEnabled() != null) {
      return cfg.getEnabled();
    }
    return defaultEnabled;
  }

  /** Effective capture-reads flag — singleton value when non-null, else deploy-time default. */
  public boolean effectiveCaptureReads() {
    ProvenanceConfig cfg = dao.findSingleton();
    if (cfg != null && cfg.getCaptureReads() != null) {
      return cfg.getCaptureReads();
    }
    return defaultCaptureReads;
  }

  /** Effective retention window in days — singleton value when non-null, else deploy-time default. */
  public long effectiveRetentionDays() {
    ProvenanceConfig cfg = dao.findSingleton();
    if (cfg != null && cfg.getRetentionDays() != null) {
      return cfg.getRetentionDays();
    }
    return defaultRetentionDays;
  }

  public boolean getDefaultEnabled() {
    return defaultEnabled;
  }

  public boolean getDefaultCaptureReads() {
    return defaultCaptureReads;
  }

  public long getDefaultRetentionDays() {
    return defaultRetentionDays;
  }

  public synchronized ProvenanceConfig patch(Boolean enabled, Boolean captureReads, Long retentionDays) {
    ProvenanceConfig cfg = current();
    cfg.setEnabled(enabled);
    cfg.setCaptureReads(captureReads);
    cfg.setRetentionDays(retentionDays);
    ProvenanceConfig saved = dao.createOrUpdate(cfg);
    Log.infof(
      "FTOGGLE-PROV-1: :ProvenanceConfig patched (enabled=%s, captureReads=%s, retentionDays=%s)",
      saved.getEnabled(), saved.getCaptureReads(), saved.getRetentionDays());
    return saved;
  }
}
