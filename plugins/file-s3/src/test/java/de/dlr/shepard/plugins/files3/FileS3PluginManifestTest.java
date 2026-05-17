package de.dlr.shepard.plugins.files3;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link FileS3PluginManifest} — pins the stable
 * metadata values that the {@code PluginRegistry} exposes through
 * {@code GET /v2/admin/plugins}.
 */
class FileS3PluginManifestTest {

  private final FileS3PluginManifest manifest = new FileS3PluginManifest();

  @Test
  void idIsFileDashS3() {
    assertThat(manifest.id()).isEqualTo("file-s3");
  }

  @Test
  void versionIsNonBlank() {
    assertThat(manifest.version()).isNotBlank();
  }

  @Test
  void shepardCompatibilityCoversCurrentVersion() {
    // Must cover 6.x; not be empty
    String compat = manifest.shepardCompatibility();
    assertThat(compat)
      .isNotBlank()
      .contains("6");
  }

  @Test
  void titleIsHumanReadable() {
    assertThat(manifest.title()).isNotBlank().doesNotStartWith("file-s3");
  }

  @Test
  void descriptionMentionsS3() {
    assertThat(manifest.description().toLowerCase()).contains("s3");
  }

  @Test
  void repositoryUrlIsPresent() {
    assertThat(manifest.repositoryUrl()).isPresent();
    assertThat(manifest.repositoryUrl().get().toString()).startsWith("https://");
  }

  @Test
  void licenceIsApache() {
    assertThat(manifest.licence()).isEqualToIgnoringCase("Apache-2.0");
  }

  @Test
  void dependenciesIsEmpty() {
    assertThat(manifest.dependencies()).isEmpty();
  }

  @Test
  void onRegisterAndUnregisterAreNoOps() {
    // Neither method should throw; both take a PluginContext (null is fine
    // for the no-op default implementations that only call Log.infof).
    manifest.onRegister(null);
    manifest.onUnregister(null);
  }
}
