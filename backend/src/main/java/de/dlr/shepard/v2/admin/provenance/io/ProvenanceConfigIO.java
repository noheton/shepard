package de.dlr.shepard.v2.admin.provenance.io;

import de.dlr.shepard.v2.admin.provenance.entities.ProvenanceConfig;

/**
 * FTOGGLE-PROV-1 — JSON shape for {@code GET|PATCH /v2/admin/config/provenance}.
 *
 * <p>All fields are always non-null in the response — null singleton fields
 * are resolved to deploy-time defaults by the service before being projected
 * here. Consumers see a fully-resolved config snapshot.
 */
public record ProvenanceConfigIO(
  boolean enabled,
  boolean captureReads,
  long retentionDays
) {

  public static ProvenanceConfigIO from(
      ProvenanceConfig cfg,
      boolean defaultEnabled,
      boolean defaultCaptureReads,
      long defaultRetentionDays) {
    boolean en = cfg.getEnabled() != null ? cfg.getEnabled() : defaultEnabled;
    boolean cr = cfg.getCaptureReads() != null ? cfg.getCaptureReads() : defaultCaptureReads;
    long rd = cfg.getRetentionDays() != null ? cfg.getRetentionDays() : defaultRetentionDays;
    return new ProvenanceConfigIO(en, cr, rd);
  }
}
