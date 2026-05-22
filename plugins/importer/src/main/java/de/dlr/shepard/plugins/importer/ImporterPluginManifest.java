package de.dlr.shepard.plugins.importer;

import de.dlr.shepard.plugin.PluginContext;
import de.dlr.shepard.plugin.PluginManifest;
import io.quarkus.logging.Log;
import java.net.URI;
import java.util.Optional;

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

  @Override
  public void onUnregister(PluginContext ctx) {
    Log.debugf("IMP1a: importer plugin onUnregister invoked");
  }
}
