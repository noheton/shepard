package de.dlr.shepard.plugins.video;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.plugin.AbstractPluginManifestTest;
import org.junit.jupiter.api.Test;

/**
 * VID1b — structural and metadata smoke tests for {@link VideoPluginManifest}.
 *
 * <p>Structural contract (id format, version non-blank,
 * shepardCompatibility non-blank, sidecars non-null) is provided for
 * free by {@link AbstractPluginManifestTest}.
 */
class VideoPluginManifestTest extends AbstractPluginManifestTest<VideoPluginManifest> {

  @Override
  protected VideoPluginManifest manifest() {
    return new VideoPluginManifest();
  }

  @Test
  void id_isVideo() {
    assertThat(manifest().id()).isEqualTo("video");
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
  void onRegisterAndUnregister_doNotThrow() {
    // VID1b phase-1 — both hooks log and return without exception
    manifest().onRegister(null);
    manifest().onUnregister(null);
  }
}
