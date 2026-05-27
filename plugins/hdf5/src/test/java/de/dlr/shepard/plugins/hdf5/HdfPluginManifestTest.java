package de.dlr.shepard.plugins.hdf5;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.plugin.AbstractPluginManifestTest;
import org.junit.jupiter.api.Test;

/**
 * PL1c — structural and metadata smoke tests for {@link HdfPluginManifest}.
 *
 * <p>Structural contract (id format, version non-blank,
 * shepardCompatibility non-blank, sidecars non-null) is provided for
 * free by {@link AbstractPluginManifestTest}.
 */
class HdfPluginManifestTest extends AbstractPluginManifestTest<HdfPluginManifest> {

  @Override
  protected HdfPluginManifest manifest() {
    return new HdfPluginManifest();
  }

  @Test
  void id_isHdf5() {
    assertThat(manifest().id()).isEqualTo("hdf5");
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
  void sidecars_includes_hsds() {
    assertThat(manifest().sidecars())
      .extracting(de.dlr.shepard.plugin.SidecarSpec::id)
      .contains("shepard-hsds");
  }

  @Test
  void sidecars_hsds_has_correct_image() {
    assertThat(manifest().sidecars())
      .filteredOn(s -> s.id().equals("shepard-hsds"))
      .singleElement()
      .extracting(de.dlr.shepard.plugin.SidecarSpec::image)
      .asString()
      .contains("hdfgroup/hsds");
  }
}
