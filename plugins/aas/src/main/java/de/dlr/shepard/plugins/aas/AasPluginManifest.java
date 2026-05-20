package de.dlr.shepard.plugins.aas;

import de.dlr.shepard.plugin.PluginContext;
import de.dlr.shepard.plugin.PluginManifest;
import io.quarkus.logging.Log;
import java.net.URI;
import java.util.Optional;

/**
 * AAS1-plugin — Asset Administration Shell plugin manifest.
 *
 * <p>Discovered by {@code de.dlr.shepard.plugin.PluginRegistry} at startup via
 * {@code META-INF/services/de.dlr.shepard.plugin.PluginManifest}.
 *
 * <p>The plugin's CDI beans — {@code AasShellMappingService},
 * {@code AasServerSelfDescriptionService}, {@code AasIdtaTemplateImportService},
 * {@code AasRegistryOutboxService}, {@code AasRegistryClient},
 * {@code AasShellsRest}, {@code AasWellKnownRest}, {@code AasAdminRest},
 * {@code AasRegistrationAdminRest} — are discovered by Quarkus's build-time
 * CDI scanner when the plugin JAR is on the backend's compile-time classpath
 * (via the {@code with-plugins} Maven profile).
 *
 * <p>V46 Neo4j migration stays in {@code backend/src/main/resources/neo4j/migrations/}
 * because the Docker runner reads from the fixed path {@code /deployments/neo4j/migrations/}
 * which is only populated from backend resources.
 */
public final class AasPluginManifest implements PluginManifest {

  private static final String ID = "aas";
  private static final String VERSION = "6.0.0-SNAPSHOT";
  private static final String SHEPARD_COMPATIBILITY = ">=6.0.0-SNAPSHOT,<7";
  private static final String TITLE = "Asset Administration Shell (IDTA)";
  private static final String DESCRIPTION =
    "IDTA AAS v3 integration. Exposes shepard Collections as AAS Shells and DataObjects as " +
    "Submodel references via /v2/aas/shells. Provides a server self-description endpoint " +
    "(/v2/aas/.well-known/aas-server), IDTA Submodel Template import " +
    "(/v2/admin/aas/import-idta-templates), and an outbox-based AAS registry sync " +
    "(/v2/admin/aas/registrations). See docs/reference/aas-plugin.md.";
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
      "AAS1-plugin: AAS plugin v%s active (id=%s, compat=%s)",
      VERSION,
      ID,
      SHEPARD_COMPATIBILITY
    );
  }

  @Override
  public void onUnregister(PluginContext ctx) {
    Log.debugf("AAS1-plugin: AAS plugin onUnregister invoked");
  }
}
