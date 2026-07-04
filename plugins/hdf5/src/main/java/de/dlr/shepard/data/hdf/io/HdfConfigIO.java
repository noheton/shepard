package de.dlr.shepard.data.hdf.io;

import de.dlr.shepard.data.hdf.entities.HdfConfig;

/**
 * FTOGGLE-HDF-ENABLE-1 — wire shape for {@code GET/PATCH /v2/admin/config/hdf}.
 *
 * @param enabled whether the HDF/HSDS feature is active. Resolved to the
 *                deploy-time default when the runtime singleton field is null.
 */
public record HdfConfigIO(boolean enabled) {

  public static HdfConfigIO from(HdfConfig cfg, boolean defaultEnabled) {
    boolean eff = cfg.getEnabled() != null ? cfg.getEnabled() : defaultEnabled;
    return new HdfConfigIO(eff);
  }
}
