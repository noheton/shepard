package de.dlr.shepard.plugins.storage.s3;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.plugin.PluginManifest;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

/**
 * FS1b — verifies the {@link S3StoragePluginManifest} shape and
 * ServiceLoader discovery.
 */
class S3StoragePluginManifestTest {

  private final S3StoragePluginManifest manifest = new S3StoragePluginManifest();

  @Test
  void id_isStorageS3() {
    assertThat(manifest.id()).isEqualTo("storage-s3");
  }

  @Test
  void version_isSnapshot() {
    assertThat(manifest.version()).isNotBlank();
  }

  @Test
  void shepardCompatibility_startsWithGte52() {
    assertThat(manifest.shepardCompatibility()).startsWith(">=5.2.0");
  }

  @Test
  void title_isNotBlank() {
    assertThat(manifest.title()).isNotBlank();
    assertThat(manifest.title()).contains("S3");
  }

  @Test
  void description_isNotBlank() {
    assertThat(manifest.description()).isNotBlank();
    assertThat(manifest.description()).contains("S3");
  }

  @Test
  void repositoryUrl_pointsToNoheton() {
    assertThat(manifest.repositoryUrl()).isPresent();
    assertThat(manifest.repositoryUrl().get().toString()).contains("noheton");
  }

  @Test
  void licence_isApache2() {
    assertThat(manifest.licence()).isEqualTo("Apache-2.0");
  }

  @Test
  void dependencies_isEmpty() {
    assertThat(manifest.dependencies()).isEmpty();
  }

  @Test
  void serviceLoader_discoversManifest() {
    ServiceLoader<PluginManifest> loader = ServiceLoader.load(PluginManifest.class);
    boolean found = false;
    for (PluginManifest pm : loader) {
      if ("storage-s3".equals(pm.id())) {
        found = true;
        break;
      }
    }
    assertThat(found)
      .withFailMessage("ServiceLoader did not discover storage-s3 PluginManifest")
      .isTrue();
  }
}
