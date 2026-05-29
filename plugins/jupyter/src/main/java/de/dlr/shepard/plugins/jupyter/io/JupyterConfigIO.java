package de.dlr.shepard.plugins.jupyter.io;

import de.dlr.shepard.plugins.jupyter.entities.JupyterConfig;

/**
 * J1e — JSON shape returned by {@code GET/PATCH /v2/admin/jupyter/config}.
 *
 * <p>The {@code enabled} field is always present (non-null primitive
 * boolean). The {@code hubUrl} field may be {@code null} when neither
 * the runtime singleton nor the deploy-time default supplies a value;
 * a null on the wire is what the frontend uses to suppress the
 * "Open in JupyterHub" affordance.
 *
 * <p>Wire names:
 * <ul>
 *   <li>{@link #enabled} — serialises as {@code "enabled"}</li>
 *   <li>{@link #hubUrl} — serialises as {@code "hubUrl"}</li>
 * </ul>
 */
public record JupyterConfigIO(
  boolean enabled,
  String hubUrl
) {

  /**
   * Project a {@link JupyterConfig} entity onto the IO, resolving a
   * {@code null} hub URL against the deploy-time default.
   *
   * @param cfg            the singleton entity (never null)
   * @param defaultHubUrl  deploy-time default hub URL (may be null)
   * @return a fully-resolved IO record
   */
  public static JupyterConfigIO from(JupyterConfig cfg, String defaultHubUrl) {
    String url = cfg.getHubUrl() != null && !cfg.getHubUrl().isBlank()
      ? cfg.getHubUrl()
      : (defaultHubUrl != null && !defaultHubUrl.isBlank() ? defaultHubUrl : null);
    return new JupyterConfigIO(cfg.isEnabled(), url);
  }
}
