package de.dlr.shepard.plugins.krl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KrlInterpreterPluginManifestTest {

  private final KrlInterpreterPluginManifest manifest = new KrlInterpreterPluginManifest();

  @Test
  void idIsKrlInterpreter() {
    assertThat(manifest.id()).isEqualTo("krl-interpreter");
  }

  @Test
  void versionIsPresent() {
    assertThat(manifest.version()).isNotBlank();
  }

  @Test
  void compatibilityRangeIsPresent() {
    assertThat(manifest.shepardCompatibility()).isNotBlank();
  }

  @Test
  void titleIsPresent() {
    assertThat(manifest.title()).isNotBlank();
  }

  @Test
  void descriptionIsPresent() {
    assertThat(manifest.description()).isNotBlank();
  }

  @Test
  void repositoryUrlIsPresent() {
    assertThat(manifest.repositoryUrl()).isPresent();
  }

  @Test
  void licenceIsApache() {
    assertThat(manifest.licence()).isEqualTo("Apache-2.0");
  }
}
