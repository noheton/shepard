package de.dlr.shepard.plugins.references.dbpediadatabus;

import de.dlr.shepard.plugin.PluginContext;
import de.dlr.shepard.plugin.PluginManifest;
import io.quarkus.logging.Log;
import java.net.URI;
import java.util.Optional;

/**
 * REF1c — DBpedia Databus rich-reference plugin manifest, discovered
 * by {@code de.dlr.shepard.plugin.PluginRegistry} at startup via the
 * {@code META-INF/services/de.dlr.shepard.plugin.PluginManifest}
 * shipped alongside this class.
 *
 * <p>Same shape as UH1a / KIP1g / KIP1h: the plugin's CDI beans are
 * discovered by Quarkus's build-time CDI scanner via the backend's
 * own classpath. This manifest exists so PluginRegistry tracks REF1c
 * in {@code GET /v2/admin/plugins}.
 *
 * <p>Per CLAUDE.md plugin-first heuristic #2 ("New external
 * integrations → plugin shape"), the DBpedia Databus integration —
 * with its own release cadence, JSON-LD parsing dependency, and
 * optional OAuth client-credentials auth — belongs in a plugin
 * rather than the in-core `references/` tree.
 */
public final class DbpediaDatabusReferencePluginManifest implements PluginManifest {

  private static final String ID = "reference-dbpedia-databus";
  private static final String VERSION = "1.0.0-SNAPSHOT";
  private static final String SHEPARD_COMPATIBILITY = ">=5.2.0,<6";
  private static final String TITLE = "DBpedia Databus References";
  private static final String DESCRIPTION =
    "Typed reference (:DbpediaDatabusReference) pointing at DBpedia Databus artifact URIs, " +
    "with optional OAuth client-credentials auth and a preview path that parses each " +
    "artifact's JSON-LD into title / abstract / version / licence / distributions and " +
    "caches the result in-graph for the configured TTL.";
  private static final URI HOMEPAGE = URI.create("https://databus.dbpedia.org/");
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

  @Override
  public void onRegister(PluginContext ctx) {
    Log.infof(
      "REF1c: reference-dbpedia-databus plugin v%s active via PluginManifest SPI (id=%s, compat=%s)",
      VERSION,
      ID,
      SHEPARD_COMPATIBILITY
    );
  }

  @Override
  public void onUnregister(PluginContext ctx) {
    Log.debugf("REF1c: reference-dbpedia-databus plugin onUnregister invoked");
  }
}
