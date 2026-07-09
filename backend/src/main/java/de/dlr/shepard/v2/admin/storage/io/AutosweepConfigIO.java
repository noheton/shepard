package de.dlr.shepard.v2.admin.storage.io;

import de.dlr.shepard.v2.admin.storage.entities.AutosweepConfig;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * FTOGGLE-AUTOSWEEP-1 — JSON shape for {@code GET|PATCH /v2/admin/config/autosweep}.
 *
 * <p>All fields are always non-null in the response — null singleton fields
 * are resolved to deploy-time defaults by the service before being projected
 * here. Consumers see a fully-resolved config snapshot.
 */
@Schema(description = "Runtime configuration for the autosweep file-migration background job.")
public record AutosweepConfigIO(
  boolean enabled,
  String source,
  String target
) {

  public static AutosweepConfigIO from(
      AutosweepConfig cfg,
      boolean defaultEnabled,
      String defaultSource,
      String defaultTarget) {
    boolean en = cfg.getEnabled() != null ? cfg.getEnabled() : defaultEnabled;
    String src = cfg.getSource() != null ? cfg.getSource() : defaultSource;
    String tgt = cfg.getTarget() != null ? cfg.getTarget() : defaultTarget;
    return new AutosweepConfigIO(en, src, tgt);
  }
}
