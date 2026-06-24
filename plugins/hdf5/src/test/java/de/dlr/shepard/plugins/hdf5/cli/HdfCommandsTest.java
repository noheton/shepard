package de.dlr.shepard.plugins.hdf5.cli;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.cli.support.CliRunner;
import de.dlr.shepard.cli.support.CliRunner.Captured;
import de.dlr.shepard.cli.support.StubBackend;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * FTOGGLE-CLI-PARITY-1 — unit tests for {@code shepard-admin hdf <verb>}.
 *
 * <p>Uses the CLI module's {@link StubBackend} / {@link CliRunner}
 * harness so we exercise the real HttpClient + Jackson + Picocli
 * wiring without booting Quarkus.
 */
final class HdfCommandsTest {

  private static final String CONFIG_ENABLED  = "{\"enabled\":true}";
  private static final String CONFIG_DISABLED = "{\"enabled\":false}";

  private StubBackend backend;

  @BeforeEach
  void setUp() throws IOException {
    backend = StubBackend.start();
  }

  @AfterEach
  void tearDown() {
    backend.close();
  }

  // ─── status ──────────────────────────────────────────────────────────────

  @Test
  void status_humanOutput_rendersTable() {
    backend.route(HdfStatusCommand.CONFIG_PATH, 200, CONFIG_ENABLED);

    Captured cap = CliRunner.run(new HdfStatusCommand(), backend.baseUrl(), "test-key");

    assertThat(cap.exit()).isEqualTo(0);
    assertThat(cap.stdout()).contains("enabled").contains("true");
    assertThat(cap.stderr()).isEmpty();
  }

  @Test
  void status_jsonOutput_emitsRawBody() {
    backend.route(HdfStatusCommand.CONFIG_PATH, 200, CONFIG_ENABLED);

    Captured cap = CliRunner.run(
      new HdfStatusCommand(), backend.baseUrl(), "test-key", "--output=json");

    assertThat(cap.exit()).isEqualTo(0);
    assertThat(cap.stdout()).contains("\"enabled\"").contains("true");
  }

  // ─── enable ──────────────────────────────────────────────────────────────

  @Test
  void enable_sendsEnabledTrue() {
    backend.route(HdfEnableCommand.CONFIG_PATH, 200, rr -> {
      assertThat(rr.body()).contains("\"enabled\":true");
      return CONFIG_ENABLED;
    });

    Captured cap = CliRunner.run(new HdfEnableCommand(), backend.baseUrl(), "test-key");

    assertThat(cap.exit()).isEqualTo(0);
    assertThat(cap.stdout()).contains("HDF").contains("enabled");
  }

  @Test
  void enable_jsonOutput_emitsParseable() {
    backend.route(HdfEnableCommand.CONFIG_PATH, 200, CONFIG_ENABLED);

    Captured cap = CliRunner.run(
      new HdfEnableCommand(), backend.baseUrl(), "test-key", "--output=json");

    assertThat(cap.exit()).isEqualTo(0);
    assertThat(cap.stdout()).contains("\"enabled\"").contains("true");
  }

  // ─── disable ─────────────────────────────────────────────────────────────

  @Test
  void disable_sendsEnabledFalse() {
    backend.route(HdfDisableCommand.CONFIG_PATH, 200, rr -> {
      assertThat(rr.body()).contains("\"enabled\":false");
      return CONFIG_DISABLED;
    });

    Captured cap = CliRunner.run(new HdfDisableCommand(), backend.baseUrl(), "test-key");

    assertThat(cap.exit()).isEqualTo(0);
    assertThat(cap.stdout()).contains("HDF").contains("disabled");
  }

  // ─── API-key forwarding ──────────────────────────────────────────────────

  @Test
  void status_forwardsApiKey() {
    backend.route(HdfStatusCommand.CONFIG_PATH, 200, rr -> {
      assertThat(rr.apiKeyHeader()).isEqualTo("my-secret");
      return CONFIG_ENABLED;
    });

    CliRunner.run(new HdfStatusCommand(), backend.baseUrl(), "my-secret");
  }

  // ─── SPI provider ────────────────────────────────────────────────────────

  @Test
  void providerReturnsHdfCommandClass() {
    assertThat(new HdfAdminCliCommandProvider().commandClass()).isEqualTo(HdfCommand.class);
  }
}
