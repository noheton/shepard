package de.dlr.shepard.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * PM1f — record-shape unit tests for {@link SidecarSpec} and its
 * companion records ({@link PortSpec}, {@link VolumeSpec},
 * {@link HealthcheckSpec}).
 *
 * <p>Pins the SPI's validation contract: blank ids, out-of-range
 * ports, non-absolute mountpoints, non-positive durations are all
 * rejected at construction time so a misshapen manifest fails the
 * unit test rather than rendering a broken compose snippet at
 * operator-deploy time.
 *
 * <p>Templating placeholders ({@code {{generate:hex:64}}} etc.) pass
 * through verbatim — the record is a passive carrier; resolution is
 * the renderer's job.
 */
class SidecarSpecTest {

  @Test
  void portSpec_rejectsZeroPort() {
    assertThatThrownBy(() -> new PortSpec(0, "s3-api"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("port must be in [1, 65535]");
  }

  @Test
  void portSpec_rejectsPortAbove65535() {
    assertThatThrownBy(() -> new PortSpec(70000, "s3-api"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("port must be in [1, 65535]");
  }

  @Test
  void portSpec_rejectsBlankRole() {
    assertThatThrownBy(() -> new PortSpec(3900, ""))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void portSpec_acceptsValidPortAndRole() {
    PortSpec p = new PortSpec(3900, "s3-api");
    assertThat(p.port()).isEqualTo(3900);
    assertThat(p.role()).isEqualTo("s3-api");
  }

  @Test
  void volumeSpec_rejectsRelativeMountpoint() {
    assertThatThrownBy(() -> new VolumeSpec("garage_data", "var/lib/garage"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("must be absolute");
  }

  @Test
  void volumeSpec_rejectsBlankName() {
    assertThatThrownBy(() -> new VolumeSpec("", "/var/lib/garage"))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void volumeSpec_acceptsValidShape() {
    VolumeSpec v = new VolumeSpec("garage_data", "/var/lib/garage");
    assertThat(v.name()).isEqualTo("garage_data");
    assertThat(v.mountpoint()).isEqualTo("/var/lib/garage");
  }

  @Test
  void healthcheckSpec_rejectsBlankCmd() {
    assertThatThrownBy(() ->
      new HealthcheckSpec("", Duration.ofSeconds(30), Duration.ofSeconds(10), 3)
    )
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void healthcheckSpec_rejectsNonPositiveInterval() {
    assertThatThrownBy(() ->
      new HealthcheckSpec("curl", Duration.ZERO, Duration.ofSeconds(10), 3)
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("interval must be positive");
  }

  @Test
  void healthcheckSpec_rejectsNonPositiveTimeout() {
    assertThatThrownBy(() ->
      new HealthcheckSpec("curl", Duration.ofSeconds(30), Duration.ZERO, 3)
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("timeout must be positive");
  }

  @Test
  void healthcheckSpec_rejectsZeroRetries() {
    assertThatThrownBy(() ->
      new HealthcheckSpec("curl", Duration.ofSeconds(30), Duration.ofSeconds(10), 0)
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("retries must be >= 1");
  }

  @Test
  void healthcheckSpec_acceptsValidShape() {
    HealthcheckSpec h = new HealthcheckSpec(
      "curl -fsS http://localhost:3900/health",
      Duration.ofSeconds(30),
      Duration.ofSeconds(10),
      3
    );
    assertThat(h.cmd()).isEqualTo("curl -fsS http://localhost:3900/health");
    assertThat(h.interval()).isEqualTo(Duration.ofSeconds(30));
    assertThat(h.timeout()).isEqualTo(Duration.ofSeconds(10));
    assertThat(h.retries()).isEqualTo(3);
  }

  @Test
  void sidecarSpec_rejectsBlankId() {
    assertThatThrownBy(() ->
      new SidecarSpec(
        "",
        "dxflrs/garage:v1.0.1",
        List.of(),
        List.of(),
        Map.of(),
        validHealthcheck(),
        List.of(),
        Map.of()
      )
    )
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void sidecarSpec_rejectsBlankImage() {
    assertThatThrownBy(() ->
      new SidecarSpec(
        "garage",
        "",
        List.of(),
        List.of(),
        Map.of(),
        validHealthcheck(),
        List.of(),
        Map.of()
      )
    )
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void sidecarSpec_nullCollectionsCoerceToEmpty() {
    SidecarSpec s = new SidecarSpec(
      "garage",
      "dxflrs/garage:v1.0.1",
      null,
      null,
      null,
      validHealthcheck(),
      null,
      null
    );
    assertThat(s.ports()).isEmpty();
    assertThat(s.volumes()).isEmpty();
    assertThat(s.env()).isEmpty();
    assertThat(s.postInit()).isEmpty();
    assertThat(s.backendEnvBinding()).isEmpty();
  }

  @Test
  void sidecarSpec_acceptsFullShapeAndIsImmutable() {
    SidecarSpec s = new SidecarSpec(
      "garage",
      "dxflrs/garage:v1.0.1",
      List.of(new PortSpec(3900, "s3-api")),
      List.of(new VolumeSpec("garage_data", "/var/lib/garage")),
      Map.of("GARAGE_RPC_SECRET", "{{generate:hex:64}}"),
      validHealthcheck(),
      List.of("/garage bucket create shepard-files"),
      Map.of("SHEPARD_FILES_S3_BUCKET", "shepard-files")
    );
    assertThat(s.id()).isEqualTo("garage");
    assertThat(s.image()).isEqualTo("dxflrs/garage:v1.0.1");
    assertThat(s.ports()).hasSize(1);
    assertThat(s.env())
      .containsEntry("GARAGE_RPC_SECRET", "{{generate:hex:64}}");
    // Defensive-copy contract — record's accessor returns immutable
    // views; attempts to mutate via the accessor should throw.
    assertThatThrownBy(() -> s.env().put("X", "Y"))
      .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> s.ports().add(new PortSpec(3902, "web-admin")))
      .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void sidecarSpec_templatingPlaceholdersPassThroughVerbatim() {
    // The record is a passive carrier; placeholders aren't resolved
    // here — the operator-side bootstrap holds that responsibility.
    SidecarSpec s = new SidecarSpec(
      "garage",
      "dxflrs/garage:v1.0.1",
      List.of(),
      List.of(),
      Map.of("GARAGE_RPC_SECRET", "{{generate:hex:64}}"),
      validHealthcheck(),
      List.of(),
      Map.of(
        "SHEPARD_FILES_S3_ENDPOINT",
        "http://{{sidecar.host}}:3900",
        "SHEPARD_FILES_S3_ACCESS_KEY_ID",
        "{{from:postInit.3.access_key_id}}"
      )
    );
    assertThat(s.env().get("GARAGE_RPC_SECRET")).isEqualTo("{{generate:hex:64}}");
    assertThat(s.backendEnvBinding().get("SHEPARD_FILES_S3_ENDPOINT"))
      .contains("{{sidecar.host}}");
    assertThat(s.backendEnvBinding().get("SHEPARD_FILES_S3_ACCESS_KEY_ID"))
      .contains("{{from:postInit.");
  }

  private static HealthcheckSpec validHealthcheck() {
    return new HealthcheckSpec(
      "curl -fsS http://localhost:3900/health",
      Duration.ofSeconds(30),
      Duration.ofSeconds(10),
      3
    );
  }
}
