package de.dlr.shepard.plugins.spatiotemporal;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.plugin.AbstractPluginManifestTest;
import org.junit.jupiter.api.Test;

/**
 * SPATIAL-V6-001 — unit tests for {@link SpatiotemporalPluginManifest}.
 *
 * <p>Structural contract (id format, version non-blank,
 * shepardCompatibility non-blank, sidecars non-null) is provided for
 * free by {@link AbstractPluginManifestTest}.
 */
class SpatiotemporalPluginManifestTest
  extends AbstractPluginManifestTest<SpatiotemporalPluginManifest> {

  @Override
  protected SpatiotemporalPluginManifest manifest() {
    return new SpatiotemporalPluginManifest();
  }

  @Test
  void id_is_spatiotemporal() {
    assertThat(manifest().id()).isEqualTo("spatiotemporal");
  }

  @Test
  void title_mentions_spatiotemporal() {
    assertThat(manifest().title()).containsIgnoringCase("Spatiotemporal");
  }

  @Test
  void description_is_non_blank() {
    assertThat(manifest().description()).isNotBlank();
  }

  @Test
  void repositoryUrl_is_present() {
    assertThat(manifest().repositoryUrl()).isPresent();
  }

  @Test
  void licence_is_apache() {
    assertThat(manifest().licence()).isEqualTo("Apache-2.0");
  }

  @Test
  void sidecars_includes_postgis() {
    assertThat(manifest().sidecars())
      .extracting(de.dlr.shepard.plugin.SidecarSpec::id)
      .contains("postgis");
  }

  @Test
  void sidecars_postgis_has_correct_image() {
    assertThat(manifest().sidecars())
      .filteredOn(s -> s.id().equals("postgis"))
      .singleElement()
      .extracting(de.dlr.shepard.plugin.SidecarSpec::image)
      .asString()
      .contains("timescaledb");
  }
}
