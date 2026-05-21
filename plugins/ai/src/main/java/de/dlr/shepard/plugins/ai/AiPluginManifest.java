package de.dlr.shepard.plugins.ai;

import de.dlr.shepard.plugin.PluginContext;
import de.dlr.shepard.plugin.PluginManifest;
import io.quarkus.logging.Log;
import java.net.URI;
import java.util.Optional;

/**
 * AI1 — LLM provider plugin manifest, discovered by
 * {@code de.dlr.shepard.plugin.PluginRegistry} at startup via the
 * {@code META-INF/services/de.dlr.shepard.plugin.PluginManifest}
 * file shipped alongside this class.
 *
 * <p>v0 scope: TEXT capability only, OPENAI_COMPAT transport.
 * Provides {@code LlmProvider} SPI implementation for OpenAI-compatible
 * endpoints and the {@code /v2/admin/ai/capabilities} admin surface.
 *
 * <p>The plugin's CDI beans — {@code AiCapabilityConfigService},
 * {@code LlmProviderImpl}, {@code AiAdminRest}, {@code AiActivityDAO},
 * {@code AiCapabilityConfigDAO} — are discovered by Quarkus's build-time
 * CDI scanner via the backend's classpath when this plugin JAR is present.
 * This manifest exists so the {@code PluginRegistry} tracks the plugin
 * in {@code GET /v2/admin/plugins}.
 *
 * <p>Neo4j-OGM entity-package registration ({@code :AiCapabilityConfig},
 * {@code :AiActivity}) is handled separately by {@link AiPayloadKind}
 * via the {@code PayloadKind} ServiceLoader SPI.
 *
 * @see de.dlr.shepard.spi.ai.LlmProvider
 * @see de.dlr.shepard.plugins.ai.provider.LlmProviderImpl
 */
public final class AiPluginManifest implements PluginManifest {

  private static final String ID = "ai";
  private static final String VERSION = "1.0.0-SNAPSHOT";
  private static final String SHEPARD_COMPATIBILITY = ">=6.0.0-SNAPSHOT,<7";
  private static final String TITLE = "Shepard AI";
  private static final String DESCRIPTION =
    "LLM provider plugin — connects Shepard to OpenAI-compatible endpoints. " +
    "Implements the LlmProvider SPI for the TEXT capability (v0). " +
    "Exposes /v2/admin/ai/capabilities for per-capability runtime config. " +
    "Writes :AiActivity provenance nodes on every call. " +
    "See aidocs/platform/86-ai-plugin-design.md (AI1).";
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
      "AI1: ai plugin v%s active via PluginManifest SPI (id=%s, compat=%s)",
      VERSION,
      ID,
      SHEPARD_COMPATIBILITY
    );
  }

  @Override
  public void onUnregister(PluginContext ctx) {
    Log.debugf("AI1: ai plugin onUnregister invoked");
  }
}
