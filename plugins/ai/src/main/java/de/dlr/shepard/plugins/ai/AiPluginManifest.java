package de.dlr.shepard.plugins.ai;

import de.dlr.shepard.plugin.HealthcheckSpec;
import de.dlr.shepard.plugin.PluginContext;
import de.dlr.shepard.plugin.PluginManifest;
import de.dlr.shepard.plugin.PortSpec;
import de.dlr.shepard.plugin.SidecarSpec;
import de.dlr.shepard.plugin.VolumeSpec;
import io.quarkus.logging.Log;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
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
 * <p>PM1f — the TEI (Text Embeddings Inference) sidecar required by
 * {@code LocalTeiEmbeddingProvider} (AI-V6-003, deferred) is declared via
 * {@link #sidecars()}. The CPU-only image is chosen for portability; GPU
 * variant ({@code pu-1.5}) is available for operators with CUDA hardware.
 * Model {@code BAAI/bge-m3} provides multilingual dense embeddings suitable
 * for research-data semantic search across mixed-language corpora.
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
    "See aidocs/archive/platform/86-ai-plugin-design.md (AI1).";
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
   * PM1f — declares the TEI (Text Embeddings Inference) sidecar needed by
   * {@code LocalTeiEmbeddingProvider} for local-offline embedding generation.
   *
   * <p>This is additive-only for now — the {@link de.dlr.shepard.plugin.SidecarsAssembler}
   * that reads this declaration to render compose snippets is not yet
   * deployed. The declaration exists so the tooling has the information
   * ready (PM1f partial; compose removal deferred to the SidecarsAssembler
   * milestone per PM1f-MIGRATION-AI-2026-05-24).
   *
   * <p>The CPU-only image tag {@code cpu-1.5} is chosen for maximum
   * portability — every operator can run it without CUDA hardware. Operators
   * with a GPU host should override to {@code pu-1.5}.
   *
   * <p>Model {@code BAAI/bge-m3} is a 567 M-parameter multilingual dense
   * retrieval model that handles 100+ languages including English and German,
   * making it suitable for DLR's mixed-language research corpora. The model
   * weights are cached in the {@code tei_models} named volume so TEI does
   * not re-download on container restart.
   *
   * <p>The {@code backendEnvBinding} keys map to
   * {@code shepard.ai.tei.*} properties consumed by
   * {@code LocalTeiEmbeddingProvider}: {@code SHEPARD_AI_TEI_ENDPOINT}
   * points at the TEI {@code /embed} REST path and
   * {@code SHEPARD_AI_TEI_ENABLED} activates the local provider path.
   */
  @Override
  public List<SidecarSpec> sidecars() {
    return List.of(
      new SidecarSpec(
        "tei",
        "ghcr.io/huggingface/text-embeddings-inference:cpu-1.5",
        List.of(new PortSpec(80, "http-embeddings")),
        List.of(new VolumeSpec("tei_models", "/data")),
        Map.of(
          "MODEL_ID",
          "BAAI/bge-m3",
          "MAX_CLIENT_BATCH_SIZE",
          "64",
          "MAX_BATCH_TOKENS",
          "16384"
        ),
        new HealthcheckSpec(
          "curl --fail --silent http://localhost/health",
          Duration.ofSeconds(30),
          Duration.ofSeconds(10),
          3
        ),
        List.of(),
        Map.of(
          "SHEPARD_AI_TEI_ENDPOINT",
          "http://{{sidecar.host}}/embed",
          "SHEPARD_AI_TEI_ENABLED",
          "true"
        ),
        "4g"
      )
    );
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
