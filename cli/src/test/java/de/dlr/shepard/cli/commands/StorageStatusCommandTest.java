package de.dlr.shepard.cli.commands;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.cli.support.CliRunner;
import de.dlr.shepard.cli.support.CliRunner.Captured;
import de.dlr.shepard.cli.support.StubBackend;
import org.junit.jupiter.api.Test;

/**
 * FS1e1 — exercises {@code shepard-admin storage status}. Reads
 * {@code GET /v2/admin/storage} and surfaces the list of discovered
 * adapters with their enabled/active state.
 */
final class StorageStatusCommandTest {

  private static final String GRIDFS_ACTIVE =
    """
    {
      "activeProviderId": "gridfs",
      "adapters": [
        {"id": "gridfs", "enabled": true, "active": true},
        {"id": "s3", "enabled": false, "active": false}
      ]
    }
    """;

  private static final String S3_ACTIVE =
    """
    {
      "activeProviderId": "s3",
      "adapters": [
        {"id": "gridfs", "enabled": true, "active": false},
        {"id": "s3", "enabled": true, "active": true}
      ]
    }
    """;

  private static final String NO_ACTIVE =
    """
    {
      "activeProviderId": null,
      "adapters": [
        {"id": "gridfs", "enabled": true, "active": false}
      ]
    }
    """;

  @Test
  void activeProviderReturnsExitZero() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route(StorageStatusCommand.STORAGE_PATH, 200, GRIDFS_ACTIVE);

      Captured result = CliRunner.run(new StorageStatusCommand(), backend.baseUrl(), "test-key");

      assertThat(result.exit()).isEqualTo(0);
      assertThat(result.stdout())
        .contains("STORAGE")
        .contains("active provider")
        .contains("gridfs")
        .contains("adapter: s3");
    }
  }

  @Test
  void s3ActiveReturnsExitZero() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route(StorageStatusCommand.STORAGE_PATH, 200, S3_ACTIVE);

      Captured result = CliRunner.run(new StorageStatusCommand(), backend.baseUrl(), "test-key");

      assertThat(result.exit()).isEqualTo(0);
      assertThat(result.stdout())
        .contains("s3")
        .contains("enabled, active");
    }
  }

  @Test
  void noActiveProviderReturnsExitOne() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route(StorageStatusCommand.STORAGE_PATH, 200, NO_ACTIVE);

      Captured result = CliRunner.run(new StorageStatusCommand(), backend.baseUrl(), "test-key");

      assertThat(result.exit()).isEqualTo(1);
      assertThat(result.stdout()).contains("none configured");
    }
  }

  @Test
  void jsonOutputIncludesActiveProviderId() throws Exception {
    try (StubBackend backend = StubBackend.start()) {
      backend.route(StorageStatusCommand.STORAGE_PATH, 200, GRIDFS_ACTIVE);

      Captured result = CliRunner.run(
        new StorageStatusCommand(),
        backend.baseUrl(),
        "test-key",
        "--output", "json"
      );

      assertThat(result.exit()).isEqualTo(0);
      assertThat(result.stdout())
        .contains("\"activeProviderId\"")
        .contains("\"gridfs\"")
        .contains("\"adapters\"");
    }
  }
}
