package de.dlr.shepard.plugins.files3;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.plugin.HealthcheckSpec;
import de.dlr.shepard.plugin.PortSpec;
import de.dlr.shepard.plugin.SidecarSpec;
import de.dlr.shepard.plugin.VolumeSpec;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * PM1f — assertions on the Garage {@link SidecarSpec} declared by
 * {@link FileS3PluginManifest}.
 *
 * <p>The values pinned here are operator-visible — they appear
 * verbatim in the compose snippet
 * {@link de.dlr.shepard.plugin.SidecarsAssembler} renders, which an
 * operator pastes into their deploy. Drift between this test and
 * the manifest is operator-affecting, so the test is intentionally
 * thorough.
 */
class FileS3PluginManifestSidecarsTest {

  private final FileS3PluginManifest manifest = new FileS3PluginManifest();

  @Test
  void sidecarsReturnsExactlyOneSpec() {
    assertThat(manifest.sidecars()).hasSize(1);
  }

  @Test
  void garageSpec_idAndImage() {
    SidecarSpec s = manifest.sidecars().get(0);
    assertThat(s.id()).isEqualTo("garage");
    assertThat(s.image()).isEqualTo("dxflrs/garage:v1.0.1");
  }

  @Test
  void garageSpec_exposesS3ApiAndWebAdminPorts() {
    SidecarSpec s = manifest.sidecars().get(0);
    List<PortSpec> ports = s.ports();
    assertThat(ports)
      .extracting(PortSpec::port)
      .containsExactlyInAnyOrder(3900, 3902);
    assertThat(ports)
      .extracting(PortSpec::role)
      .containsExactlyInAnyOrder("s3-api", "web-admin");
  }

  @Test
  void garageSpec_declaresDataVolume() {
    SidecarSpec s = manifest.sidecars().get(0);
    assertThat(s.volumes()).hasSize(1);
    VolumeSpec v = s.volumes().get(0);
    assertThat(v.name()).isEqualTo("garage_data");
    assertThat(v.mountpoint()).isEqualTo("/var/lib/garage");
  }

  @Test
  void garageSpec_rpcSecretIsTemplated() {
    SidecarSpec s = manifest.sidecars().get(0);
    assertThat(s.env().get("GARAGE_RPC_SECRET"))
      .isEqualTo("{{generate:hex:64}}");
  }

  @Test
  void garageSpec_apiBindAddrIsFixed() {
    SidecarSpec s = manifest.sidecars().get(0);
    assertThat(s.env().get("GARAGE_S3_API_BIND_ADDR")).isEqualTo("0.0.0.0:3900");
    assertThat(s.env().get("GARAGE_WEB_BIND_ADDR")).isEqualTo("0.0.0.0:3902");
  }

  @Test
  void garageSpec_healthcheckUsesCurlOnPort3900() {
    SidecarSpec s = manifest.sidecars().get(0);
    HealthcheckSpec hc = s.healthcheck();
    assertThat(hc.cmd()).contains("curl").contains("http://localhost:3900/health");
    assertThat(hc.interval()).isEqualTo(Duration.ofSeconds(30));
    assertThat(hc.timeout()).isEqualTo(Duration.ofSeconds(10));
    assertThat(hc.retries()).isEqualTo(3);
  }

  @Test
  void garageSpec_postInitHasFiveCommandsInOrder() {
    SidecarSpec s = manifest.sidecars().get(0);
    List<String> postInit = s.postInit();
    assertThat(postInit).hasSize(5);
    assertThat(postInit.get(0)).contains("layout assign");
    assertThat(postInit.get(1)).contains("layout apply");
    assertThat(postInit.get(2)).contains("bucket create shepard-files");
    assertThat(postInit.get(3)).contains("key new --name shepard-backend");
    assertThat(postInit.get(4)).contains("bucket allow");
  }

  @Test
  void garageSpec_backendEnvBindingSetsShepardFilesS3Keys() {
    SidecarSpec s = manifest.sidecars().get(0);
    assertThat(s.backendEnvBinding())
      .containsEntry("SHEPARD_FILES_S3_BUCKET", "shepard-files")
      .containsEntry("SHEPARD_FILES_S3_PATH_STYLE", "true")
      .containsEntry("SHEPARD_FILES_S3_REGION", "garage-region");
    assertThat(s.backendEnvBinding().get("SHEPARD_FILES_S3_ENDPOINT"))
      .contains("{{sidecar.host}}")
      .contains(":3900");
    assertThat(s.backendEnvBinding().get("SHEPARD_FILES_S3_ACCESS_KEY_ID"))
      .contains("{{from:postInit.");
    assertThat(s.backendEnvBinding().get("SHEPARD_FILES_S3_SECRET_ACCESS_KEY"))
      .contains("{{from:postInit.");
  }
}
