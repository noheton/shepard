package de.dlr.shepard.v2.admin.krl.io;

import de.dlr.shepard.v2.admin.krl.entities.KrlInterpreterConfigSingleton;

/**
 * KRL-CONFIG-1 — JSON shape returned by
 * {@code GET/PATCH /v2/admin/krl/config}.
 *
 * <p>Wire names:
 * <ul>
 *   <li>{@link #enabled} — serialises as {@code "enabled"}</li>
 *   <li>{@link #sidecarUrl} — serialises as {@code "sidecarUrl"};
 *       null when no runtime override and the deploy-time default is
 *       also null</li>
 *   <li>{@link #timeoutSeconds} — serialises as {@code "timeoutSeconds"};
 *       reflects the <em>effective</em> value (runtime if set, else
 *       deploy-time default)</li>
 *   <li>{@link #maxBodySizeMb} — serialises as {@code "maxBodySizeMb"};
 *       reflects the <em>effective</em> value</li>
 * </ul>
 *
 * <p>All four fields are always present in the response — clients may
 * rely on their presence to construct the human-readable status view.
 */
public record KrlInterpreterConfigIO(
    boolean enabled, String sidecarUrl, int timeoutSeconds, int maxBodySizeMb) {

  /**
   * Project a {@link KrlInterpreterConfigSingleton} entity onto the IO,
   * resolving zero / null runtime values against deploy-time defaults.
   *
   * @param cfg                  the singleton entity (never null)
   * @param defaultSidecarUrl    deploy-time default sidecar URL (may be null)
   * @param defaultTimeoutSeconds deploy-time default timeout in seconds
   * @param defaultMaxBodySizeMb  deploy-time default max body size in MiB
   * @return a fully-resolved IO record with effective values
   */
  public static KrlInterpreterConfigIO from(
      KrlInterpreterConfigSingleton cfg,
      String defaultSidecarUrl,
      int defaultTimeoutSeconds,
      int defaultMaxBodySizeMb) {
    String effectiveUrl =
        (cfg.getSidecarUrl() != null && !cfg.getSidecarUrl().isBlank())
            ? cfg.getSidecarUrl()
            : (defaultSidecarUrl != null && !defaultSidecarUrl.isBlank()
                ? defaultSidecarUrl
                : null);
    int effectiveTimeout = cfg.getTimeoutSeconds() > 0 ? cfg.getTimeoutSeconds() : defaultTimeoutSeconds;
    int effectiveMaxBody = cfg.getMaxBodySizeMb() > 0 ? cfg.getMaxBodySizeMb() : defaultMaxBodySizeMb;
    return new KrlInterpreterConfigIO(cfg.isEnabled(), effectiveUrl, effectiveTimeout, effectiveMaxBody);
  }
}
