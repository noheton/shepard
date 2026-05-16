package de.dlr.shepard.plugins.files3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.dlr.shepard.storage.StorageProviderUnavailableException;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link S3FileStorage} — structural invariants
 * exercised without a live S3 endpoint.
 * Integration tests against a real Garage container are in the
 * backend integration-test tree (FS1b acceptance suite, deferred).
 */
class S3FileStorageTest {

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

  @Test
  void presignedExportUrlThrowsWhenDisabled() {
    S3FileStorage storage = new S3FileStorage();
    assertThatThrownBy(
      () -> storage.presignedExportUrl("key", new byte[]{1, 2, 3}, "export.zip", Duration.ofMinutes(30))
    ).isInstanceOf(StorageProviderUnavailableException.class);
  }
}
