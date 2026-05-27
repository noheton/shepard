package de.dlr.shepard.plugins.git;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.plugin.AbstractPluginManifestTest;
import org.junit.jupiter.api.Test;

/**
 * PL1d — structural and metadata smoke tests for {@link GitPluginManifest}.
 *
 * <p>Structural contract (id format, version non-blank,
 * shepardCompatibility non-blank, sidecars non-null) is provided for
 * free by {@link AbstractPluginManifestTest}.
 */
class GitPluginManifestTest extends AbstractPluginManifestTest<GitPluginManifest> {

  @Override
  protected GitPluginManifest manifest() {
    return new GitPluginManifest();
  }

  @Test
  void id_isGit() {
    assertThat(manifest().id()).isEqualTo("git");
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
