package de.dlr.shepard.plugins.files3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.dlr.shepard.storage.StorageException;
import de.dlr.shepard.storage.StorageLocator;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link S3FileStorage} — locator parsing and
 * structural invariants exercised without a live S3 endpoint.
 * Integration tests against a real Garage container are in the
 * backend integration-test tree (FS1b acceptance suite, deferred).
 */
class S3FileStorageTest {

  @Test
  void splitLocator_parsesSimpleKey() throws StorageException {
    StorageLocator loc = new StorageLocator("s3", "my-bucket/container123/file-uuid-goes-here");
    S3FileStorage.LocatorParts parts = S3FileStorage.splitLocator(loc);

    assertThat(parts.bucket()).isEqualTo("my-bucket");
    assertThat(parts.key()).isEqualTo("container123/file-uuid-goes-here");
  }

  @Test
  void splitLocator_handlesMultiSegmentKey() throws StorageException {
    StorageLocator loc = new StorageLocator("s3", "bucket/a/b/c/d");
    S3FileStorage.LocatorParts parts = S3FileStorage.splitLocator(loc);

    assertThat(parts.bucket()).isEqualTo("bucket");
    assertThat(parts.key()).isEqualTo("a/b/c/d");
  }

  @Test
  void splitLocator_rejectsNoSlash() {
    StorageLocator loc = new StorageLocator("s3", "nobucket");
    assertThatThrownBy(() -> S3FileStorage.splitLocator(loc))
      .isInstanceOf(StorageException.class)
      .hasMessageContaining("Malformed S3 locator");
  }

  @Test
  void splitLocator_rejectsTrailingSlash() {
    StorageLocator loc = new StorageLocator("s3", "bucket/");
    assertThatThrownBy(() -> S3FileStorage.splitLocator(loc))
      .isInstanceOf(StorageException.class)
      .hasMessageContaining("Malformed S3 locator");
  }

  @Test
  void splitLocator_rejectsLeadingSlash() {
    StorageLocator loc = new StorageLocator("s3", "/key");
    assertThatThrownBy(() -> S3FileStorage.splitLocator(loc))
      .isInstanceOf(StorageException.class)
      .hasMessageContaining("Malformed S3 locator");
  }

  @Test
  void idIsS3() {
    S3FileStorage storage = new S3FileStorage();
    assertThat(storage.id()).isEqualTo("s3");
  }

  @Test
  void isDisabledByDefault() {
    S3FileStorage storage = new S3FileStorage();
    assertThat(storage.isEnabled()).isFalse();
  }
}
