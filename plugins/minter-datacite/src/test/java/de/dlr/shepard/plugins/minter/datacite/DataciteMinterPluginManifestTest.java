package de.dlr.shepard.plugins.minter.datacite;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.plugin.AbstractPluginManifestTest;
import de.dlr.shepard.plugin.PluginManifest;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

/**
 * KIP1d — PluginManifest SPI discovery + manifest-shape smoke tests.
 *
 * <p>Structural contract (id format, version non-blank,
 * shepardCompatibility non-blank, sidecars non-null) is provided for
 * free by {@link AbstractPluginManifestTest}.
 */
class DataciteMinterPluginManifestTest
  extends AbstractPluginManifestTest<DataciteMinterPluginManifest> {

  @Override
  protected DataciteMinterPluginManifest manifest() {
    return new DataciteMinterPluginManifest();
  }

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
    assertThat(manifest().id()).isEqualTo("minter-datacite");
  }

  @Test
  void manifest_carriesNonBlankShape() {
    assertThat(manifest().title()).isNotBlank();
    assertThat(manifest().description()).isNotBlank();
    assertThat(manifest().licence()).isEqualTo("Apache-2.0");
    assertThat(manifest().dependencies()).isEmpty();
    assertThat(manifest().homepageUrl()).isPresent();
    assertThat(manifest().repositoryUrl()).isPresent();
  }

  @Test
  void lifecycleHooks_areSafeToInvoke() {
    // Both hooks log; no observable side-effect — just verify they don't
    // throw when called with a null ctx (the no-op shape).
    manifest().onRegister(null);
    manifest().onUnregister(null);
  }
}
