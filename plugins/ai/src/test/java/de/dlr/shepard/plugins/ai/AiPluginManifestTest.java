package de.dlr.shepard.plugins.ai;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.plugin.AbstractPluginManifestTest;
import org.junit.jupiter.api.Test;

/**
 * AI1 — structural and metadata smoke tests for {@link AiPluginManifest}.
 *
 * <p>Structural contract (id format, version non-blank,
 * shepardCompatibility non-blank, sidecars non-null) is provided for
 * free by {@link AbstractPluginManifestTest}.
 *
 * <p>PM1f — TEI sidecar declaration tests pin the id, image, and
 * backendEnvBinding keys so a future refactor can't silently change
 * the env keys that {@code LocalTeiEmbeddingProvider} (AI-V6-003) reads.
 */
class AiPluginManifestTest extends AbstractPluginManifestTest<AiPluginManifest> {

  @Override
  protected AiPluginManifest manifest() {
    return new AiPluginManifest();
  }

  @Test
  void id_isAi() {
    assertThat(manifest().id()).isEqualTo("ai");
  }

  @Test
  void title_isNonBlank() {
    assertThat(manifest().title()).isNotBlank();
  }

  @Test
  void description_isNonBlank() {
    assertThat(manifest().description()).isNotBlank();
  }

  @Test
  void repositoryUrl_isPresent() {
    assertThat(manifest().repositoryUrl()).isPresent();
  }

  @Test
  void licence_isApache() {
    assertThat(manifest().licence()).isEqualTo("Apache-2.0");
  }

  @Test
  void onRegisterAndUnregister_areNoOps() {
    manifest().onRegister(null);
    manifest().onUnregister(null);
  }

  @Test
  void sidecars_includes_tei() {
    assertThat(manifest().sidecars())
      .extracting(de.dlr.shepard.plugin.SidecarSpec::id)
      .contains("tei");
  }

  @Test
  void sidecars_tei_has_correct_image() {
    assertThat(manifest().sidecars())
      .filteredOn(s -> s.id().equals("tei"))
      .singleElement()
      .extracting(de.dlr.shepard.plugin.SidecarSpec::image)
      .asString()
      .contains("text-embeddings-inference");
  }

  @Test
  void sidecars_tei_backendEnvBinding_contains_endpoint_and_enabled_keys() {
    var tei = manifest()
      .sidecars()
      .stream()
      .filter(s -> s.id().equals("tei"))
      .findFirst()
      .orElseThrow();
    assertThat(tei.backendEnvBinding())
      .containsKey("SHEPARD_AI_TEI_ENDPOINT")
      .containsKey("SHEPARD_AI_TEI_ENABLED");
  }
}
