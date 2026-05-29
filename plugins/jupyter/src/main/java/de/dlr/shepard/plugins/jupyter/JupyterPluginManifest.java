package de.dlr.shepard.plugins.jupyter;

import de.dlr.shepard.plugin.PluginContext;
import de.dlr.shepard.plugin.PluginManifest;
import io.quarkus.logging.Log;
import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * J1e-PLUGIN-REFACTOR — JupyterHub link-out plugin manifest,
 * discovered by {@code de.dlr.shepard.plugin.PluginRegistry} at
 * startup via the
 * {@code META-INF/services/de.dlr.shepard.plugin.PluginManifest}
 * file shipped alongside this class.
 *
 * <p>Relocates the J1e admin gate (shipped in-tree on 2026-05-29 under
 * {@code de.dlr.shepard.v2.admin.jupyter.*}) into a drop-in plugin
 * per the plugin-first principle (CLAUDE.md §"plugin-first").
 * The integration is a textbook external-service link-out — runtime
 * config + public read endpoint + per-row UI affordance — and has no
 * reason to ship in the core image when the operator doesn't run a
 * JupyterHub.
 *
 * <p>The JupyterHub docker-compose sidecar declaration (J1e-PR-05)
 * lands in a later PR.
 */
public final class JupyterPluginManifest implements PluginManifest {

  private static final String ID = "jupyter";

  private static final String VERSION = "1.0.0-SNAPSHOT";

  private static final String SHEPARD_COMPATIBILITY = ">=6.0.0-SNAPSHOT,<7";

  private static final String TITLE = "JupyterHub link-out";

  private static final String DESCRIPTION =
    "Admin-configurable per-notebook 'Open in JupyterHub' action. " +
    "Provides the :JupyterConfig singleton + admin REST (instance-admin) + " +
    "public read endpoint that the unified Data References table consults " +
    "to gate the row action. CLI parity via `shepard-admin jupyter ...`. " +
    "See aidocs/16 J1e-PLUGIN-REFACTOR.";

  private static final URI REPOSITORY = URI.create("https://github.com/noheton/shepard");

  private static final String LICENCE = "Apache-2.0";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public String version() {
    return VERSION;
  }

  @Override
  public String shepardCompatibility() {
    return SHEPARD_COMPATIBILITY;
  }

  @Override
  public String title() {
    return TITLE;
  }

  @Override
  public String description() {
    return DESCRIPTION;
  }

  @Override
  public Optional<URI> repositoryUrl() {
    return Optional.of(REPOSITORY);
  }

  @Override
  public String licence() {
    return LICENCE;
  }

  /**
   * The public read endpoint at {@code /v2/plugins/jupyter/config}
   * (and the legacy {@code /v2/jupyter/config} compat-shim path) is
   * intentionally callable without authentication — the unified
   * Data References table reads it from every browser session to
   * decide whether to render the per-row "Open in JupyterHub"
   * action.
   *
   * <p>The publicPaths declaration here is informational for the
   * PluginRegistry / admin survey of public surfaces; the real
   * permit-all wiring is on the JAX-RS resource itself.
   */
  @Override
  public List<String> publicPaths() {
    return List.of("/v2/plugins/jupyter/config", "/v2/jupyter/config");
  }

  @Override
  public void onRegister(PluginContext ctx) {
    Log.infof(
      "J1e-PLUGIN-REFACTOR: jupyter plugin v%s active via PluginManifest SPI (id=%s, compat=%s)",
      VERSION,
      ID,
      SHEPARD_COMPATIBILITY
    );
  }

  @Override
  public void onUnregister(PluginContext ctx) {
    Log.debugf("J1e-PLUGIN-REFACTOR: jupyter plugin onUnregister invoked");
  }
}
