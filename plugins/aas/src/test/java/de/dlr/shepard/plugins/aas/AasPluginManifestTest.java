package de.dlr.shepard.plugins.aas;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.plugin.AbstractPluginManifestTest;
import org.junit.jupiter.api.Test;

/**
 * AAS1 — structural and metadata smoke tests for {@link AasPluginManifest}.
 *
 * <p>Structural contract (id format, version non-blank,
 * shepardCompatibility non-blank, sidecars non-null) is provided for
 * free by {@link AbstractPluginManifestTest}.
 */
class AasPluginManifestTest extends AbstractPluginManifestTest<AasPluginManifest> {

  @Override
  protected AasPluginManifest manifest() {
    return new AasPluginManifest();
  }

  @Test
  void id_isAas() {
    assertThat(manifest().id()).isEqualTo("aas");
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
