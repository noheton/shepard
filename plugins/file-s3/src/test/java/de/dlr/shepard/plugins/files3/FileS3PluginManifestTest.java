package de.dlr.shepard.plugins.files3;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.plugin.AbstractPluginManifestTest;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link FileS3PluginManifest} — pins the stable
 * metadata values that the {@code PluginRegistry} exposes through
 * {@code GET /v2/admin/plugins}.
 *
 * <p>Structural contract (id format, version non-blank,
 * shepardCompatibility non-blank, sidecars non-null) is provided for
 * free by {@link AbstractPluginManifestTest}. This class adds the
 * file-s3-specific pin assertions on top.
 */
class FileS3PluginManifestTest extends AbstractPluginManifestTest<FileS3PluginManifest> {

  @Override
  protected FileS3PluginManifest manifest() {
    return new FileS3PluginManifest();
  }

  @Test
  void idIsFileDashS3() {
    assertThat(manifest().id()).isEqualTo("file-s3");
  }

  @Test
  void shepardCompatibilityCoversCurrentVersion() {
    // Must cover 6.x; not be empty
    String compat = manifest().shepardCompatibility();
    assertThat(compat)
      .isNotBlank()
      .contains("6");
  }

  @Test
  void titleIsHumanReadable() {
    assertThat(manifest().title()).isNotBlank().doesNotStartWith("file-s3");
  }

  @Test
  void descriptionMentionsS3() {
    assertThat(manifest().description().toLowerCase()).contains("s3");
  }

  @Test
  void repositoryUrlIsPresent() {
    assertThat(manifest().repositoryUrl()).isPresent();
    assertThat(manifest().repositoryUrl().get().toString()).startsWith("https://");
  }

  @Test
  void licenceIsApache() {
    assertThat(manifest().licence()).isEqualToIgnoringCase("Apache-2.0");
  }

  @Test
  void dependenciesIsEmpty() {
    assertThat(manifest().dependencies()).isEmpty();
  }

  @Test
  void onRegisterAndUnregisterAreNoOps() {
    // Neither method should throw; both take a PluginContext (null is fine
    // for the no-op default implementations that only call Log.infof).
    manifest().onRegister(null);
    manifest().onUnregister(null);
  }
}
