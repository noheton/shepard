package de.dlr.shepard.plugins.importer;

import de.dlr.shepard.plugin.PluginContext;
import de.dlr.shepard.plugin.PluginManifest;
import io.quarkus.logging.Log;
import java.net.URI;
import java.util.Optional;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * IMP1a — shepard-plugin-importer manifest, discovered by
 * {@code de.dlr.shepard.plugin.PluginRegistry} at startup via the
 * {@code META-INF/services/de.dlr.shepard.plugin.PluginManifest}
 * shipped alongside this class.
 *
 * <p>PR-1 ships the manifest + Maven wiring only. PR-2 lands the
 * {@code importer_run} Postgres table + {@code JobService}-shaped
 * DAO/service (the first concrete instance of
 * {@code aidocs/platform/32}'s broader JobService design). PR-3
 * extracts the {@code DLRv5Source} adapter from
 * {@code examples/mffd-showcase/scripts/mffd-dropbox-import.py}
 * v12's {@code ShepardClient} (lines 239-1170). PR-4 adds the REST
 * surface ({@code /v2/imports/...}). PR-5 adds the
 * {@code shepard-admin importer ...} CLI parity. PR-6 adds the
 * frontend (2s polling per decision #7). PR-7 the end-to-end
 * integration test against a mock v5 source server.
 *
 * <p>The agentic data-management pipeline this plugin enables is
 * the north-star MFFD demo: git → seed → snapshot → AI reorg →
 * wiki-publish. See {@code aidocs/44} IMP1 row + the
 * {@code project_importer_plugin} memory note for the full design
 * arc.
 */
public final class ImporterPluginManifest implements PluginManifest {

  /** Plugin id — matches {@code shepard.plugins.importer.enabled}. */
  private static final String ID = "importer";

  /**
   * Plugin version. Hand-pinned to {@code ${revision}} from
   * {@code plugins/importer/pom.xml}. PM1b will read this from a
   * build-generated resource so the version doesn't drift.
   */
  private static final String VERSION = "1.0.0-SNAPSHOT";

  /**
   * Semver range of the shepard core this plugin is known
   * compatible with. IMP1a depends on PluginManifest SPI (PM1a),
   * Hibernate ORM Panache (already in backend), Flyway (already in
   * backend), and microprofile-fault-tolerance (already in
   * backend) — all shipped from 6.0.0-SNAPSHOT onwards.
   */
  private static final String SHEPARD_COMPATIBILITY = ">=6.0.0-SNAPSHOT,<7";

  /** PM1c — display name surfaced in admin REST + CLI. */
  private static final String TITLE = "Importer (agentic data-management pipeline)";

  /** PM1c — operator-facing summary. */
  private static final String DESCRIPTION =
    "Library of importers that pull data from remote sources " +
    "(DLR shepard v5 instances, git, S3, …) into the local instance. " +
    "Asynchronous; each run is a row in the importer_run Postgres table.";

  /** PM1c — homepage (the fork's repo for now; standalone home later). */
  private static final URI HOMEPAGE =
    URI.create("https://github.com/noheton/shepard");

  /** PM1c — fork source-code repository. */
  private static final URI REPOSITORY =
    URI.create("https://github.com/noheton/shepard");

  /** PM1c — SPDX licence id (inherits the fork's Apache-2.0). */
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
  public Optional<URI> homepageUrl() {
    return Optional.of(HOMEPAGE);
  }

  @Override
  public Optional<URI> repositoryUrl() {
    return Optional.of(REPOSITORY);
  }

  @Override
  public String licence() {
    return LICENCE;
  }

  // dependencies() — default empty list. IMP1a depends on in-tree
  // backend SPIs only (Hibernate ORM Panache, Flyway,
  // microprofile-fault-tolerance, JobService-shape conventions) —
  // not on any other plugin.

  @Override
  public void onRegister(PluginContext ctx) {
    // IMPL3 — fail-fast guard: refuse to activate with an insecure
    // SHEPARD_INSTANCE_SECRET in non-dev/test profiles. The guard
    // runs before any CDI wiring so the plugin lands in FAILED state
    // (surfaced in GET /v2/admin/plugins) rather than running silently
    // with a weak credential.
    enforceInstanceSecretGuard(
      ConfigProvider.getConfig()
        .getOptionalValue("quarkus.profile", String.class)
        .orElse("prod"),
      ConfigProvider.getConfig()
        .getOptionalValue("shepard.audit.instance-secret", String.class)
        .orElse("")
    );
    // PR-1 has no CDI beans yet; nothing to wire imperatively.
    // PR-2's @ApplicationScoped services and PR-4's @Path
    // resources will be discovered by Quarkus's build-time CDI
    // scanner once they land.
    Log.infof(
      "IMP1a: importer plugin v%s active via PluginManifest SPI (id=%s, compat=%s)",
      VERSION,
      ID,
      SHEPARD_COMPATIBILITY
    );
  }

  /**
   * IMPL3 — startup guard: throws {@link IllegalStateException} when the
   * runtime profile is production-like (anything other than {@code dev}
   * or {@code test}) and {@code SHEPARD_INSTANCE_SECRET}
   * ({@code shepard.audit.instance-secret}) is absent, blank, the
   * well-known sentinel {@code "changeme"}, or shorter than 16 characters.
   *
   * <p>Package-private + static so unit tests can exercise the logic
   * without booting Quarkus or touching {@link ConfigProvider}.
   * {@link #onRegister} resolves both values from config and passes
   * them through here.
   *
   * <p>The check is intentionally symmetric with the install-doc
   * warning at {@code plugins/importer/docs/install.md §Pitfalls}:
   * the doc said "do not run in production with the default
   * changeme-style value"; this method makes the consequence
   * automatic and immediate.
   *
   * @param profile the Quarkus {@code quarkus.profile} value
   *                (e.g. {@code "dev"}, {@code "test"}, {@code "prod"})
   * @param secret  the configured {@code shepard.audit.instance-secret}
   *                value, or empty string if absent
   * @throws IllegalStateException when the guard fires
   */
  static void enforceInstanceSecretGuard(String profile, String secret) {
    if ("dev".equals(profile) || "test".equals(profile)) {
      return;
    }
    String s = (secret == null) ? "" : secret.trim();
    if (s.isBlank() || "changeme".equalsIgnoreCase(s) || s.length() < 16) {
      throw new IllegalStateException(
        "shepard-plugin-importer: SHEPARD_INSTANCE_SECRET is insecure " +
        "(blank, default 'changeme', or shorter than 16 characters). " +
        "Set a strong value (≥16 chars) in SHEPARD_INSTANCE_SECRET before " +
        "running in production. See plugins/importer/docs/install.md for details."
      );
    }
  }

  @Override
  public void onUnregister(PluginContext ctx) {
    Log.debugf("IMP1a: importer plugin onUnregister invoked");
  }
}
