package de.dlr.shepard.plugins.v1compat;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.plugin.AbstractPluginManifestTest;
import org.junit.jupiter.api.Test;

/**
 * V1COMPAT.0 — structural and metadata smoke tests for
 * {@link V1CompatPluginManifest}.
 *
 * <p>Structural contract (id format, version non-blank,
 * shepardCompatibility non-blank, sidecars non-null) is provided for
 * free by {@link AbstractPluginManifestTest}.
 */
class V1CompatPluginManifestTest extends AbstractPluginManifestTest<V1CompatPluginManifest> {

  @Override
  protected V1CompatPluginManifest manifest() {
    return new V1CompatPluginManifest();
  }

  @Test
  void id_isV1Compat() {
    assertThat(manifest().id()).isEqualTo("v1-compat");
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
}
