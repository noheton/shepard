package de.dlr.shepard.cli.commands;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.cli.support.CliRunner;
import de.dlr.shepard.cli.support.CliRunner.Captured;
import de.dlr.shepard.cli.support.StubBackend;
import de.dlr.shepard.cli.support.StubBackend.RecordedRequest;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * FTOGGLE-CLI-PARITY-1 — unit tests for
 * {@code shepard-admin sql-timeseries <verb>}.
 *
 * <p>Each test wires the Picocli command against an in-process
 * {@link StubBackend} so we exercise the real HttpClient + Jackson +
 * Picocli wiring without booting Quarkus.
 */
final class SqlTimeseriesCommandsTest {

  private static final String CONFIG_BODY =
    "{\"maxRows\":50000,\"maxDuration\":\"PT60S\"}";

  private static final String CONFIG_BODY_ENABLED =
    "{\"enabled\":true,\"maxRows\":50000,\"maxDuration\":\"PT60S\"}";

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
    backend.route(SqlTimeseriesStatusCommand.CONFIG_PATH, 200, CONFIG_BODY);

    Captured cap = CliRunner.run(new SqlTimeseriesStatusCommand(), backend.baseUrl(), "test-key");

    assertThat(cap.exit()).isEqualTo(0);
    assertThat(cap.stdout())
      .contains("maxRows")
      .contains("50000")
      .contains("maxDuration")
      .contains("PT60S");
    assertThat(cap.stderr()).isEmpty();
  }

  @Test
  void status_jsonOutput_emitsRawBody() {
    backend.route(SqlTimeseriesStatusCommand.CONFIG_PATH, 200, CONFIG_BODY);

    Captured cap = CliRunner.run(
      new SqlTimeseriesStatusCommand(), backend.baseUrl(), "test-key", "--output=json");

    assertThat(cap.exit()).isEqualTo(0);
    assertThat(cap.stdout()).contains("maxRows").contains("50000");
  }

  @Test
  void status_withEnabledField_showsEnabled() {
    backend.route(SqlTimeseriesStatusCommand.CONFIG_PATH, 200, CONFIG_BODY_ENABLED);

    Captured cap = CliRunner.run(new SqlTimeseriesStatusCommand(), backend.baseUrl(), "test-key");

    assertThat(cap.exit()).isEqualTo(0);
    assertThat(cap.stdout()).contains("enabled").contains("true");
  }

  // ─── enable ──────────────────────────────────────────────────────────────

  @Test
  void enable_sendsEnabledTrue_andShowsTable() {
    backend.route(SqlTimeseriesEnableCommand.CONFIG_PATH, 200, rr -> {
      assertThat(rr.body()).contains("\"enabled\":true");
      return CONFIG_BODY_ENABLED;
    });

    Captured cap = CliRunner.run(new SqlTimeseriesEnableCommand(), backend.baseUrl(), "test-key");

    assertThat(cap.exit()).isEqualTo(0);
    assertThat(cap.stdout()).contains("enabled").contains("true");
  }

  @Test
  void enable_jsonOutput_emitsParseable() {
    backend.route(SqlTimeseriesEnableCommand.CONFIG_PATH, 200, CONFIG_BODY_ENABLED);

    Captured cap = CliRunner.run(
      new SqlTimeseriesEnableCommand(), backend.baseUrl(), "test-key", "--output=json");

    assertThat(cap.exit()).isEqualTo(0);
    assertThat(cap.stdout()).contains("\"enabled\"").contains("true");
  }

  // ─── disable ─────────────────────────────────────────────────────────────

  @Test
  void disable_sendsEnabledFalse_andShowsTable() {
    final String disabledBody = "{\"enabled\":false,\"maxRows\":50000,\"maxDuration\":\"PT60S\"}";
    backend.route(SqlTimeseriesDisableCommand.CONFIG_PATH, 200, rr -> {
      assertThat(rr.body()).contains("\"enabled\":false");
      return disabledBody;
    });

    Captured cap = CliRunner.run(new SqlTimeseriesDisableCommand(), backend.baseUrl(), "test-key");

    assertThat(cap.exit()).isEqualTo(0);
    assertThat(cap.stdout()).contains("enabled").contains("false");
  }

  // ─── set-max-rows ────────────────────────────────────────────────────────

  @Test
  void setMaxRows_value_sendsCorrectPatch() {
    final String updated = "{\"maxRows\":100000,\"maxDuration\":\"PT60S\"}";
    backend.route(SqlTimeseriesSetMaxRowsCommand.CONFIG_PATH, 200, rr -> {
      assertThat(rr.body()).contains("\"maxRows\":100000");
      return updated;
    });

    Captured cap = CliRunner.run(
      new SqlTimeseriesSetMaxRowsCommand(), backend.baseUrl(), "test-key", "--value=100000");

    assertThat(cap.exit()).isEqualTo(0);
    assertThat(cap.stdout()).contains("maxRows").contains("100000");
  }

  @Test
  void setMaxRows_reset_sendsNullPatch() {
    backend.route(SqlTimeseriesSetMaxRowsCommand.CONFIG_PATH, 200, rr -> {
      assertThat(rr.body()).contains("\"maxRows\":null");
      return CONFIG_BODY;
    });

    Captured cap = CliRunner.run(
      new SqlTimeseriesSetMaxRowsCommand(), backend.baseUrl(), "test-key", "--reset");

    assertThat(cap.exit()).isEqualTo(0);
  }

  @Test
  void setMaxRows_noArgs_exitsOne() {
    Captured cap = CliRunner.run(
      new SqlTimeseriesSetMaxRowsCommand(), backend.baseUrl(), "test-key");

    assertThat(cap.exit()).isEqualTo(1);
    assertThat(cap.stderr()).contains("error:");
  }

  @Test
  void setMaxRows_valueAndReset_exitsOne() {
    Captured cap = CliRunner.run(
      new SqlTimeseriesSetMaxRowsCommand(), backend.baseUrl(), "test-key",
      "--value=100", "--reset");

    assertThat(cap.exit()).isEqualTo(1);
    assertThat(cap.stderr()).contains("mutually exclusive");
  }

  // ─── set-max-duration ────────────────────────────────────────────────────

  @Test
  void setMaxDuration_value_sendsCorrectPatch() {
    final String updated = "{\"maxRows\":50000,\"maxDuration\":\"PT2M\"}";
    backend.route(SqlTimeseriesSetMaxDurationCommand.CONFIG_PATH, 200, rr -> {
      assertThat(rr.body()).contains("\"maxDuration\":\"PT2M\"");
      return updated;
    });

    Captured cap = CliRunner.run(
      new SqlTimeseriesSetMaxDurationCommand(), backend.baseUrl(), "test-key", "--value=PT2M");

    assertThat(cap.exit()).isEqualTo(0);
    assertThat(cap.stdout()).contains("maxDuration").contains("PT2M");
  }

  @Test
  void setMaxDuration_reset_sendsNullPatch() {
    backend.route(SqlTimeseriesSetMaxDurationCommand.CONFIG_PATH, 200, rr -> {
      assertThat(rr.body()).contains("\"maxDuration\":null");
      return CONFIG_BODY;
    });

    Captured cap = CliRunner.run(
      new SqlTimeseriesSetMaxDurationCommand(), backend.baseUrl(), "test-key", "--reset");

    assertThat(cap.exit()).isEqualTo(0);
  }

  @Test
  void setMaxDuration_noArgs_exitsOne() {
    Captured cap = CliRunner.run(
      new SqlTimeseriesSetMaxDurationCommand(), backend.baseUrl(), "test-key");

    assertThat(cap.exit()).isEqualTo(1);
    assertThat(cap.stderr()).contains("error:");
  }

  // ─── API-key forwarding ──────────────────────────────────────────────────

  @Test
  void status_forwardsApiKey() {
    backend.route(SqlTimeseriesStatusCommand.CONFIG_PATH, 200, rr -> {
      assertThat(rr.apiKeyHeader()).isEqualTo("my-secret-key");
      return CONFIG_BODY;
    });

    CliRunner.run(new SqlTimeseriesStatusCommand(), backend.baseUrl(), "my-secret-key");
  }
}
