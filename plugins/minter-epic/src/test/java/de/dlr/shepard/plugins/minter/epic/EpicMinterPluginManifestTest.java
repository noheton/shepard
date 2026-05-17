package de.dlr.shepard.plugins.minter.epic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import de.dlr.shepard.plugin.PluginContext;
import org.junit.jupiter.api.Test;

/**
 * KIP1c — sanity checks for the plugin manifest SPI.
 */
class EpicMinterPluginManifestTest {

  private final EpicMinterPluginManifest manifest = new EpicMinterPluginManifest();

  @Test
  void id_isStable() {
    assertThat(manifest.id()).isEqualTo("minter-epic");
  }

  @Test
  void title_isHumanReadable() {
    assertThat(manifest.title()).contains("ePIC");
  }

  @Test
  void description_isNonEmpty() {
    assertThat(manifest.description()).isNotBlank();
  }

  @Test
  void licence_isApache2() {
    assertThat(manifest.licence()).isEqualTo("Apache-2.0");
  }

  @Test
  void homepageUrl_isPresent() {
    assertThat(manifest.homepageUrl()).isPresent();
    assertThat(manifest.homepageUrl().get().toString()).contains("pidconsortium");
  }

  @Test
  void repositoryUrl_isPresent() {
    assertThat(manifest.repositoryUrl()).isPresent();
  }

  @Test
  void dependencies_isEmpty() {
    assertThat(manifest.dependencies()).isEmpty();
  }

  @Test
  void shepardCompatibility_isNonEmpty() {
    assertThat(manifest.shepardCompatibility()).isNotBlank();
    assertThat(manifest.shepardCompatibility()).contains("6.0.0-SNAPSHOT");
  }

  @Test
  void onRegister_doesNotThrow() {
    PluginContext ctx = mock(PluginContext.class);
    // Should log and return without exception
    manifest.onRegister(ctx);
  }

  @Test
  void onUnregister_doesNotThrow() {
    PluginContext ctx = mock(PluginContext.class);
    manifest.onUnregister(ctx);
  }
}
