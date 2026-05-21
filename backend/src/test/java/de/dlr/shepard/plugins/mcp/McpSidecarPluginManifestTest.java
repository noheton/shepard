package de.dlr.shepard.plugins.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * MCP-1 — verifies the sidecar stub manifest returns the expected contract
 * so the PluginRegistry can discover + track it correctly.
 */
class McpSidecarPluginManifestTest {

  private final McpSidecarPluginManifest manifest = new McpSidecarPluginManifest();

  @Test
  void id_isMcp() {
    assertThat(manifest.id()).isEqualTo("mcp");
  }

  @Test
  void version_isPresent() {
    assertThat(manifest.version()).isNotBlank();
  }

  @Test
  void shepardCompatibility_coversCurrentRelease() {
    // The declared range must be a valid semver range string and must include
    // 5.2.0 (the upstream base this fork tracks).
    String range = manifest.shepardCompatibility();
    assertThat(range)
      .isNotBlank()
      .contains(">=5.2.0");
  }

  @Test
  void title_isPresent() {
    assertThat(manifest.title()).isNotBlank();
  }

  @Test
  void description_isPresent() {
    assertThat(manifest.description()).isNotBlank();
  }

  @Test
  void licence_isApache() {
    assertThat(manifest.licence()).isEqualTo("Apache-2.0");
  }
}
