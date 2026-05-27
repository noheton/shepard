package de.dlr.shepard.plugins.spatiotemporal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * SPATIAL-V6-001 — unit tests for {@link SpatiotemporalPluginManifest}.
 */
class SpatiotemporalPluginManifestTest {

  private final SpatiotemporalPluginManifest manifest = new SpatiotemporalPluginManifest();

  @Test
  void id_is_spatiotemporal() {
    assertThat(manifest.id()).isEqualTo("spatiotemporal");
  }

  @Test
  void version_is_non_blank() {
    assertThat(manifest.version()).isNotBlank();
  }

  @Test
  void shepardCompatibility_is_non_blank() {
    assertThat(manifest.shepardCompatibility()).isNotBlank();
  }

  @Test
  void title_mentions_spatiotemporal() {
    assertThat(manifest.title()).containsIgnoringCase("Spatiotemporal");
  }

  @Test
  void description_is_non_blank() {
    assertThat(manifest.description()).isNotBlank();
  }

  @Test
  void repositoryUrl_is_present() {
    assertThat(manifest.repositoryUrl()).isPresent();
  }

  @Test
  void licence_is_apache() {
    assertThat(manifest.licence()).isEqualTo("Apache-2.0");
  }
}
