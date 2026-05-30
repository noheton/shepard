package de.dlr.shepard.v2.krl.io;

import de.dlr.shepard.v2.krl.entities.KrlInterpreterConfigEntity;

/**
 * KRL-CONFIG-1 — JSON shape returned by
 * {@code GET/PATCH /v2/admin/plugins/krl/config}.
 *
 * <p>All three fields carry the <em>effective</em> value — the runtime
 * singleton's value when non-null, otherwise the deploy-time default
 * from {@code application.properties}. Callers always see a fully
 * resolved config; they never need to perform their own null-coalesce.
 *
 * <p>Wire names:
 * <ul>
 *   <li>{@link #sidecarUrl} — serialises as {@code "sidecarUrl"}</li>
 *   <li>{@link #timeoutSeconds} — serialises as
 *       {@code "timeoutSeconds"}</li>
 *   <li>{@link #maxBodySizeMb} — serialises as
 *       {@code "maxBodySizeMb"}</li>
 * </ul>
 */
public record KrlInterpreterConfigIO(
  String sidecarUrl,
  int timeoutSeconds,
  int maxBodySizeMb
) {

  /**
   * Project a {@link KrlInterpreterConfigEntity} onto the IO, resolving
   * {@code null} runtime fields against the deploy-time defaults.
   *
   * @param entity              the singleton entity (never null)
   * @param defaultSidecarUrl   deploy-time default sidecar URL
   * @param defaultTimeout      deploy-time default timeout in seconds
   * @param defaultMaxBodySize  deploy-time default max body size in MB
   * @return a fully-resolved IO record
   */
  public static KrlInterpreterConfigIO from(
    KrlInterpreterConfigEntity entity,
    String defaultSidecarUrl,
    int defaultTimeout,
    int defaultMaxBodySize
  ) {
    String url = (entity.getSidecarUrl() != null && !entity.getSidecarUrl().isBlank())
      ? entity.getSidecarUrl()
      : defaultSidecarUrl;
    int timeout = entity.getTimeoutSeconds() != null
      ? entity.getTimeoutSeconds()
      : defaultTimeout;
    int maxBody = entity.getMaxBodySizeMb() != null
      ? entity.getMaxBodySizeMb()
      : defaultMaxBodySize;
    return new KrlInterpreterConfigIO(url, timeout, maxBody);
  }
}
