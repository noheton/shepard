package de.dlr.shepard.v2.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.v2.transform.krl.KrlTrajectoryTransformExecutor;
import de.dlr.shepard.v2.transform.krl.services.KrlTrajectoryParams;
import de.dlr.shepard.v2.transform.krl.services.KrlTrajectoryService;
import io.quarkiverse.mcp.server.McpException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * MCP-COV-09-KRL — unit tests for {@link KrlMcpTools}.
 *
 * <p>Full end-to-end interpret (sidecar + CDI) is exercised by the backend
 * integration tests (CI). Here we cover: tool registration metadata (shape IRI,
 * binding roles), input validation failure paths before service interaction, and
 * the happy-path delegation to {@link KrlTrajectoryService}.
 */
class KrlMcpToolsTest {

  private KrlTrajectoryService krlService;
  private McpContextBridge contextBridge;
  private McpToolSupport support;
  private KrlMcpTools tools;

  @BeforeEach
  void setUp() {
    krlService = mock(KrlTrajectoryService.class);
    contextBridge = mock(McpContextBridge.class);
    doNothing().when(contextBridge).bind();

    // Wire a real McpToolSupport so the run() wrapper logic is exercised.
    // McpToolSupport has injected deps; build a minimal double that delegates.
    support = new McpToolSupportDouble();

    tools = new KrlMcpTools();
    tools.krlTrajectoryService = krlService;
    tools.contextBridge = contextBridge;
    tools.support = support;
  }

  // ─── krl_capabilities ─────────────────────────────────────────────────────

  @Test
  void capabilitiesReturnsShapeIri() throws Exception {
    String json = tools.krlCapabilities();
    JsonNode root = new ObjectMapper().readTree(json);
    assertThat(root.path("shapeIri").asText())
      .isEqualTo(KrlTrajectoryTransformExecutor.KRL_TRAJECTORY_SHAPE_IRI);
  }

  @Test
  void capabilitiesBindingRolesContainBothRequiredRoles() throws Exception {
    String json = tools.krlCapabilities();
    JsonNode root = new ObjectMapper().readTree(json);
    JsonNode roles = root.path("bindingRoles");
    assertThat(roles.has(KrlTrajectoryTransformExecutor.ROLE_SRC_FILE)).isTrue();
    assertThat(roles.has(KrlTrajectoryTransformExecutor.ROLE_URDF_FILE)).isTrue();
  }

  @Test
  void capabilitiesIncludesRequiredTemplateBodyFields() throws Exception {
    String json = tools.krlCapabilities();
    JsonNode root = new ObjectMapper().readTree(json);
    JsonNode fields = root.path("templateBodyFields");
    assertThat(fields.has("targetDataObjectAppId")).isTrue();
    assertThat(fields.has("timeseriesContainerAppId")).isTrue();
  }

  // ─── krl_interpret — validation ───────────────────────────────────────────

  @Test
  void interpretRejectsNullSrcFileAppId() {
    assertThatThrownBy(() ->
      tools.krlInterpret(null, "urdf-1", "do-1", "ts-1", null, null))
      .isInstanceOf(McpException.class)
      .hasMessageContaining("srcFileRefAppId");
  }

  @Test
  void interpretRejectsBlankSrcFileAppId() {
    assertThatThrownBy(() ->
      tools.krlInterpret("  ", "urdf-1", "do-1", "ts-1", null, null))
      .isInstanceOf(McpException.class)
      .hasMessageContaining("srcFileRefAppId");
  }

  @Test
  void interpretRejectsNullUrdfAppId() {
    assertThatThrownBy(() ->
      tools.krlInterpret("src-1", null, "do-1", "ts-1", null, null))
      .isInstanceOf(McpException.class)
      .hasMessageContaining("urdfFileRefAppId");
  }

  @Test
  void interpretRejectsNullTargetDataObjectAppId() {
    assertThatThrownBy(() ->
      tools.krlInterpret("src-1", "urdf-1", null, "ts-1", null, null))
      .isInstanceOf(McpException.class)
      .hasMessageContaining("targetDataObjectAppId");
  }

  @Test
  void interpretRejectsNullTimeseriesContainerAppId() {
    assertThatThrownBy(() ->
      tools.krlInterpret("src-1", "urdf-1", "do-1", null, null, null))
      .isInstanceOf(McpException.class)
      .hasMessageContaining("timeseriesContainerAppId");
  }

  // ─── krl_interpret — happy path ───────────────────────────────────────────

  @Test
  void interpretDelegatesWithAllRequiredParams() throws Exception {
    when(krlService.interpret(any(KrlTrajectoryParams.class), isNull(), isNull()))
      .thenReturn("derived-ref-appid");

    String json = tools.krlInterpret("src-1", "urdf-1", "do-1", "ts-1", null, null);

    JsonNode root = new ObjectMapper().readTree(json);
    assertThat(root.path("derivedReferenceAppId").asText()).isEqualTo("derived-ref-appid");
    verify(contextBridge).bind();
  }

  @Test
  void interpretForwardsDatFileAppIds() throws Exception {
    when(krlService.interpret(any(KrlTrajectoryParams.class), isNull(), isNull()))
      .thenReturn("ref-42");

    tools.krlInterpret("src-1", "urdf-1", "do-1", "ts-1", List.of("dat-a", "dat-b"), "tmpl-1");

    verify(krlService).interpret(
      new KrlTrajectoryParams("tmpl-1", "src-1", "urdf-1", "do-1", "ts-1",
        List.of("dat-a", "dat-b")),
      null, null
    );
  }

  @Test
  void interpretDefaultsNullDatListToEmpty() throws Exception {
    when(krlService.interpret(any(KrlTrajectoryParams.class), isNull(), isNull()))
      .thenReturn("ref-43");

    tools.krlInterpret("src-1", "urdf-1", "do-1", "ts-1", null, null);

    verify(krlService).interpret(
      new KrlTrajectoryParams(null, "src-1", "urdf-1", "do-1", "ts-1", List.of()),
      null, null
    );
  }

  // ─── inner test double ────────────────────────────────────────────────────

  /**
   * Minimal McpToolSupport double that runs the callable directly so we get
   * real exception-mapping behaviour without needing a full CDI/Vert.x context.
   */
  static class McpToolSupportDouble extends McpToolSupport {
    @Override
    <T> T run(String toolName, java.util.concurrent.Callable<T> body) {
      try {
        return body.call();
      } catch (McpException e) {
        throw e;
      } catch (RuntimeException e) {
        throw e;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    String toJson(Object value) {
      try {
        return new ObjectMapper().writeValueAsString(value);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }
}
