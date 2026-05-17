package de.dlr.shepard.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for P23: {@link PresignTtlValidator}. */
class PresignTtlValidatorTest {

  private PresignTtlValidator validator;

  @BeforeEach
  void setUp() {
    validator = new PresignTtlValidator();
    // default cache TTL 5 minutes
    validator.permissionsCacheTtl = Duration.ofMinutes(5);
  }

  @Test
  void uploadTtlCappedWhenExceedsCache() {
    validator.configuredUploadTtl = Duration.ofMinutes(15);
    assertEquals(Duration.ofMinutes(5), validator.effectiveUploadTtl());
  }

  @Test
  void uploadTtlPassedThroughWhenBelowCache() {
    validator.configuredUploadTtl = Duration.ofMinutes(3);
    assertEquals(Duration.ofMinutes(3), validator.effectiveUploadTtl());
  }

  @Test
  void uploadTtlPassedThroughWhenEqualToCache() {
    validator.configuredUploadTtl = Duration.ofMinutes(5);
    assertEquals(Duration.ofMinutes(5), validator.effectiveUploadTtl());
  }

  @Test
  void downloadTtlCappedWhenExceedsCache() {
    validator.configuredDownloadTtl = Duration.ofMinutes(10);
    assertEquals(Duration.ofMinutes(5), validator.effectiveDownloadTtl());
  }

  @Test
  void exportTtlCappedAtCacheTtlByDefault() {
    validator.configuredExportTtl = Duration.ofMinutes(30);
    assertEquals(Duration.ofMinutes(5), validator.effectiveExportTtl());
  }

  @Test
  void exportTtlPassedThroughWhenCacheTtlIsLarger() {
    validator.permissionsCacheTtl = Duration.ofHours(1);
    validator.configuredExportTtl = Duration.ofMinutes(30);
    assertEquals(Duration.ofMinutes(30), validator.effectiveExportTtl());
  }

  @Test
  void capMethodReturnsMinDuration() {
    assertEquals(Duration.ofMinutes(5), validator.cap(Duration.ofMinutes(5)));
    assertEquals(Duration.ofMinutes(5), validator.cap(Duration.ofMinutes(10)));
    assertEquals(Duration.ofMinutes(3), validator.cap(Duration.ofMinutes(3)));
  }

  @Test
  void zeroCacheTtlCapsEverythingToZero() {
    validator.permissionsCacheTtl = Duration.ZERO;
    validator.configuredUploadTtl = Duration.ofMinutes(15);
    assertEquals(Duration.ZERO, validator.effectiveUploadTtl());
  }
}
