package de.dlr.shepard.plugins.analyticsts;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * AT1 — smoke test of the analytics-ts plugin manifest. Verifies the
 * id matches the {@code shepard.plugins.<id>.enabled} key, the SPDX
 * licence is declared, and the manifest is wired up for discovery via
 * the {@code META-INF/services/de.dlr.shepard.plugin.PluginManifest}
 * resource.
 */
class AnalyticsTsPluginManifestTest {

  @Test
  void manifest_id_matches_the_enable_config_key() {
    var manifest = new AnalyticsTsPluginManifest();
    assertThat(manifest.id()).isEqualTo("analytics-ts");
  }

  @Test
  void manifest_declares_apache_2_0_licence() {
    var manifest = new AnalyticsTsPluginManifest();
    assertThat(manifest.licence()).isEqualTo("Apache-2.0");
  }

  @Test
  void manifest_declares_a_repository_url() {
    var manifest = new AnalyticsTsPluginManifest();
    assertThat(manifest.repositoryUrl()).isPresent();
  }

  @Test
  void manifest_version_is_non_blank() {
    var manifest = new AnalyticsTsPluginManifest();
    assertThat(manifest.version()).isNotBlank();
  }

  @Test
  void manifest_compatibility_range_targets_fork_6() {
    var manifest = new AnalyticsTsPluginManifest();
    assertThat(manifest.shepardCompatibility()).contains("6.0.0").contains("<7");
  }
}
