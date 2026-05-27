package de.dlr.shepard.data.file.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.dlr.shepard.common.exceptions.InvalidRequestException;
import io.quarkus.test.component.QuarkusComponentTest;
import io.quarkus.test.component.TestConfigProperty;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * MONGO-AUDIT-2026-05-24-012 — isolated tests for the configurable upload size
 * cap in {@link FileService#enforceFileSizeCap(long)}.
 *
 * <p>A separate test class is required because {@code @TestConfigProperty} is
 * class-level and overrides the config for all tests in the class; the main
 * {@link FileServiceTest} uses the default config value throughout.
 */
@QuarkusComponentTest
@TestConfigProperty(key = "shepard.mongo.file.max-bytes", value = "100")
public class FileServiceSizeCapTest {

  @Inject
  FileService fileService;

  /**
   * MONGO-AUDIT-2026-05-24-012 — a file larger than the configured cap
   * must be rejected with {@link InvalidRequestException}.
   */
  @Test
  public void enforceFileSizeCap_exceedsLimit_throwsInvalidRequestException() {
    assertThrows(
      InvalidRequestException.class,
      () -> fileService.enforceFileSizeCap(101L)
    );
  }

  @Test
  public void enforceFileSizeCap_exactlyAtLimit_doesNotThrow() {
    // Exactly at the cap (100 bytes) should be accepted.
    assertDoesNotThrow(() -> fileService.enforceFileSizeCap(100L));
  }

  @Test
  public void enforceFileSizeCap_unknownSize_doesNotThrow() {
    // Declared size <= 0 means "unknown" — skip the check.
    assertDoesNotThrow(() -> fileService.enforceFileSizeCap(0L));
    assertDoesNotThrow(() -> fileService.enforceFileSizeCap(-1L));
  }
}
