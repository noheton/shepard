package de.dlr.shepard.plugins.minter.datacite;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.plugin.PluginManifest;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

/**
 * KIP1d — PluginManifest SPI discovery + manifest-shape smoke tests.
 */
class DataciteMinterPluginManifestTest {

  @Test
  void manifest_isDiscoverableViaServiceLoader() {
    ServiceLoader<PluginManifest> loader = ServiceLoader.load(PluginManifest.class);

    boolean found = false;
    for (PluginManifest m : loader) {
      if (m instanceof DataciteMinterPluginManifest) {
        found = true;
        break;
      }
    }
    assertThat(found).as("DataciteMinterPluginManifest must be ServiceLoader-discoverable").isTrue();
  }

  @Test
  void manifest_carriesExpectedId() {
    DataciteMinterPluginManifest m = new DataciteMinterPluginManifest();
    assertThat(m.id()).isEqualTo("minter-datacite");
  }

  @Test
  void manifest_carriesNonBlankShape() {
    DataciteMinterPluginManifest m = new DataciteMinterPluginManifest();

    assertThat(m.version()).isNotBlank();
    assertThat(m.shepardCompatibility()).isNotBlank();
    assertThat(m.title()).isNotBlank();
    assertThat(m.description()).isNotBlank();
    assertThat(m.licence()).isEqualTo("Apache-2.0");
    assertThat(m.dependencies()).isEmpty();
    assertThat(m.homepageUrl()).isPresent();
    assertThat(m.repositoryUrl()).isPresent();
  }

  @Test
  void lifecycleHooks_areSafeToInvoke() {
    DataciteMinterPluginManifest m = new DataciteMinterPluginManifest();
    // Both hooks log; no observable side-effect — just verify they don't
    // throw when called with a null ctx (the no-op shape).
    m.onRegister(null);
    m.onUnregister(null);
  }
}
