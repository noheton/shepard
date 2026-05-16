package de.dlr.shepard.plugins.git;

import de.dlr.shepard.plugin.PluginContext;
import de.dlr.shepard.plugin.PluginManifest;
import io.quarkus.logging.Log;
import java.net.URI;
import java.util.Optional;

/**
 * PL1d — Git reference payload-kind plugin manifest, discovered by
 * {@code de.dlr.shepard.plugin.PluginRegistry} at startup via the
 * {@code META-INF/services/de.dlr.shepard.plugin.PluginManifest}
 * file shipped alongside this class.
 *
 * <p>The plugin's CDI beans — {@code GitReferenceService},
 * {@code GitReferenceRest}, {@code MeCredentialsRest},
 * {@code GitAdapterRegistry}, {@code GitArtifactCache} — are
 * discovered by Quarkus's build-time CDI scanner via the backend's
 * classpath. This manifest exists so the {@code PluginRegistry}
 * tracks PL1d in {@code GET /v2/admin/plugins} and so the
 * {@code shepard.plugins.git.enabled} runtime toggle is surfaced.
 *
 * <p>Neo4j-OGM entity-package registration ({@code GitReference}) is
 * handled separately by {@link GitPayloadKind} via the
 * {@code PayloadKind} ServiceLoader SPI — that path fires inside
 * {@code NeoConnector.connect()}, before CDI is up. This fixes the
 * latent OGM-gap where {@code GitReference}'s package was previously
 * unregistered in {@code NeoConnector}.
 *
 * <p>{@code GitCredential} entity, DAO, and service remain in the
 * backend (auth perimeter) — they are not part of this plugin.
 * Cypher migrations V19, V20, and V26 also stay in backend.
 */
public final class GitPluginManifest implements PluginManifest {

  private static final String ID = "git";

  private static final String VERSION = "1.0.0-SNAPSHOT";

  private static final String SHEPARD_COMPATIBILITY = ">=6.0.0-SNAPSHOT,<7";

  private static final String TITLE = "Git References";

  private static final String DESCRIPTION =
    "Git reference payload kind. Provides GitReference Neo4j context nodes, " +
    "REST endpoints (/v2/data-objects/{id}/git-references/*, /v2/me/git-credentials/*), " +
    "GitLab/GitHub/Gitea adapters, and the per-user PAT-authenticated file-artifact fetch cache. " +
    "GitCredential entity and service remain in backend (auth perimeter). " +
    "Fixes the latent OGM-gap where GitReference's package was previously unregistered in NeoConnector.";

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

  @Override
  public void onRegister(PluginContext ctx) {
    Log.infof(
      "PL1d: git plugin v%s active via PluginManifest SPI (id=%s, compat=%s)",
      VERSION,
      ID,
      SHEPARD_COMPATIBILITY
    );
  }

  @Override
  public void onUnregister(PluginContext ctx) {
    Log.debugf("PL1d: git plugin onUnregister invoked");
  }
}
