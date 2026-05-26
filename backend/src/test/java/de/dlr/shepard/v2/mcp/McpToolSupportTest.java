package de.dlr.shepard.v2.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.provenance.services.ProvenanceService;
import de.dlr.shepard.v2.ai.AiActivityType;
import de.dlr.shepard.v2.ai.AiProvenanceCapture;
import io.quarkiverse.mcp.server.McpException;
import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.inject.Instance;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link McpToolSupport}.
 *
 * <p>Covers:
 * <ul>
 *   <li>Exception mapping in {@link McpToolSupport#run}: NotFoundException → -32602,
 *       ForbiddenException → -32002, RuntimeException → -32603</li>
 *   <li>TPL9 AI provenance wiring: {@link McpToolSupport#run} calls
 *       {@link AiProvenanceCapture#record} only when {@code X-AI-Agent} header
 *       is present; absent header or absent routing context → no capture.</li>
 *   <li>Header forwarding: model, prompt hash, and activity type headers are
 *       correctly read and forwarded to {@link AiProvenanceCapture#record}.</li>
 * </ul>
 */
@SuppressWarnings("unchecked")
class McpToolSupportTest {

  @Mock EntityIdResolver entityIdResolver;
  @Mock AiProvenanceCapture aiProvenanceCapture;
  @Mock Instance<CurrentVertxRequest> currentVertxRequestInstance;
  @Mock CurrentVertxRequest currentVertxRequest;
  @Mock RoutingContext rc;
  @Mock HttpServerRequest httpRequest;
  @Mock ProvenanceService provenanceService;

  McpToolSupport support;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);

    support = new McpToolSupport();
    support.entityIdResolver = entityIdResolver;
    support.objectMapper = new ObjectMapper();
    support.aiProvenanceCapture = aiProvenanceCapture;
    support.currentVertxRequest = currentVertxRequestInstance;

    // Default: Instance returns a live CurrentVertxRequest which has no routing context.
    when(currentVertxRequestInstance.get()).thenReturn(currentVertxRequest);
    when(currentVertxRequest.getCurrent()).thenReturn(null);

    // Default: routing context returns the mock request.
    when(rc.request()).thenReturn(httpRequest);
  }

  // ── Exception mapping in run() ─────────────────────────────────────────────

  @Test
  void run_successfulCallable_returnsResult() {
    String result = support.run("test_tool", () -> "hello");
    assertEquals("hello", result);
  }

  @Test
  void run_notFoundException_mapsToInvalidParams() {
    McpException ex = assertThrows(McpException.class,
      () -> support.run("test_tool", () -> { throw new NotFoundException("not found"); }));
    assertEquals(McpToolSupport.INVALID_PARAMS, ex.getJsonRpcErrorCode());
  }

  @Test
  void run_illegalArgumentException_mapsToInvalidParams() {
    McpException ex = assertThrows(McpException.class,
      () -> support.run("test_tool", () -> { throw new IllegalArgumentException("bad arg"); }));
    assertEquals(McpToolSupport.INVALID_PARAMS, ex.getJsonRpcErrorCode());
  }

  @Test
  void run_forbiddenException_mapsToForbidden() {
    McpException ex = assertThrows(McpException.class,
      () -> support.run("test_tool", () -> { throw new ForbiddenException("no access"); }));
    assertEquals(McpToolSupport.FORBIDDEN, ex.getJsonRpcErrorCode());
  }

  @Test
  void run_runtimeException_mapsToInternalError() {
    McpException ex = assertThrows(McpException.class,
      () -> support.run("test_tool", () -> { throw new RuntimeException("boom"); }));
    assertEquals(McpToolSupport.INTERNAL_ERROR, ex.getJsonRpcErrorCode());
    // Message must include the tool name for debuggability.
    assertEquals(true, ex.getMessage().contains("test_tool"),
      "Internal error must name the tool, was: " + ex.getMessage());
  }

  @Test
  void run_mcpExceptionPassesThrough() {
    McpException orig = new McpException("already mapped", -32602);
    McpException ex = assertThrows(McpException.class,
      () -> support.run("test_tool", () -> { throw orig; }));
    assertEquals(orig, ex);
  }

  // ── TPL9: no AI header → no provenance capture ────────────────────────────

  @Test
  void run_noRoutingContext_noAiProvenanceCapture() {
    // currentVertxRequest.getCurrent() returns null (default setUp).
    support.run("list_collections", () -> "[]");
    verify(aiProvenanceCapture, never()).record(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void run_instanceGetThrows_noAiProvenanceCapture() {
    when(currentVertxRequestInstance.get()).thenThrow(new RuntimeException("ContextNotActive"));
    support.run("list_collections", () -> "[]");
    verify(aiProvenanceCapture, never()).record(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void run_routingContextPresent_noXAiAgentHeader_noCapture() {
    when(currentVertxRequest.getCurrent()).thenReturn(rc);
    when(httpRequest.getHeader(McpToolSupport.HEADER_AI_AGENT)).thenReturn(null);

    support.run("list_collections", () -> "[]");

    verify(aiProvenanceCapture, never()).record(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void run_xAiAgentHeaderBlank_noCapture() {
    when(currentVertxRequest.getCurrent()).thenReturn(rc);
    when(httpRequest.getHeader(McpToolSupport.HEADER_AI_AGENT)).thenReturn("   ");

    support.run("list_collections", () -> "[]");

    verify(aiProvenanceCapture, never()).record(any(), any(), any(), any(), any(), any(), any());
  }

  // ── TPL9: X-AI-Agent header present → provenance is captured ─────────────

  @Test
  void run_xAiAgentPresent_aiProvenanceCaptured() {
    when(currentVertxRequest.getCurrent()).thenReturn(rc);
    when(httpRequest.getHeader(McpToolSupport.HEADER_AI_AGENT)).thenReturn("claude");
    when(httpRequest.getHeader(McpToolSupport.HEADER_AI_MODEL)).thenReturn(null);
    when(httpRequest.getHeader(McpToolSupport.HEADER_AI_PROMPT_HASH)).thenReturn(null);
    when(httpRequest.getHeader(McpToolSupport.HEADER_AI_ACTIVITY_TYPE)).thenReturn(null);

    support.run("list_collections", () -> "[]");

    // TPL9: record must be called exactly once.
    verify(aiProvenanceCapture).record(
      eq(AiActivityType.CHAT_RESPONSE),  // null header → CHAT_RESPONSE default
      isNull(),                          // modelId
      isNull(),                          // promptHash
      isNull(),                          // subjectAppId
      isNull(),                          // metadata
      eq("list_collections"),            // toolName
      eq("claude")                       // agentId
    );
  }

  @Test
  void run_xAiAgentAndModelAndPromptHash_forwardedToCapture() {
    when(currentVertxRequest.getCurrent()).thenReturn(rc);
    when(httpRequest.getHeader(McpToolSupport.HEADER_AI_AGENT)).thenReturn("openai-assistant");
    when(httpRequest.getHeader(McpToolSupport.HEADER_AI_MODEL)).thenReturn("gpt-4o");
    when(httpRequest.getHeader(McpToolSupport.HEADER_AI_PROMPT_HASH)).thenReturn("sha256-abc");
    when(httpRequest.getHeader(McpToolSupport.HEADER_AI_ACTIVITY_TYPE)).thenReturn(null);

    support.run("create_data_object", () -> "{}");

    verify(aiProvenanceCapture).record(
      eq(AiActivityType.CHAT_RESPONSE),
      eq("gpt-4o"),
      eq("sha256-abc"),
      isNull(),
      isNull(),
      eq("create_data_object"),
      eq("openai-assistant")
    );
  }

  @Test
  void run_activityTypeHeader_parsedAndForwarded() {
    when(currentVertxRequest.getCurrent()).thenReturn(rc);
    when(httpRequest.getHeader(McpToolSupport.HEADER_AI_AGENT)).thenReturn("claude");
    when(httpRequest.getHeader(McpToolSupport.HEADER_AI_MODEL)).thenReturn(null);
    when(httpRequest.getHeader(McpToolSupport.HEADER_AI_PROMPT_HASH)).thenReturn(null);
    when(httpRequest.getHeader(McpToolSupport.HEADER_AI_ACTIVITY_TYPE))
      .thenReturn("ANNOTATION_SUGGESTION");

    support.run("annotate", () -> "ok");

    verify(aiProvenanceCapture).record(
      eq(AiActivityType.ANNOTATION_SUGGESTION),
      isNull(),
      isNull(),
      isNull(),
      isNull(),
      eq("annotate"),
      eq("claude")
    );
  }

  @Test
  void run_aiProvenanceCaptureThrows_toolResultStillReturned() {
    // Best-effort: capture failure must NEVER block the tool result.
    when(currentVertxRequest.getCurrent()).thenReturn(rc);
    when(httpRequest.getHeader(McpToolSupport.HEADER_AI_AGENT)).thenReturn("agent-x");
    when(httpRequest.getHeader(McpToolSupport.HEADER_AI_MODEL)).thenReturn(null);
    when(httpRequest.getHeader(McpToolSupport.HEADER_AI_PROMPT_HASH)).thenReturn(null);
    when(httpRequest.getHeader(McpToolSupport.HEADER_AI_ACTIVITY_TYPE)).thenReturn(null);

    // Capture bean throws (e.g. Neo4j down).
    org.mockito.Mockito.doThrow(new RuntimeException("Neo4j is down"))
      .when(aiProvenanceCapture)
      .record(any(), any(), any(), any(), any(), any(), any());

    // Tool result must still come through.
    String result = support.run("list_collections", () -> "[]");
    assertEquals("[]", result);
  }

  @Test
  void run_toolThrows_aiProvenanceNotCaptured() {
    // Provenance is only recorded on SUCCESSFUL tool execution.
    when(currentVertxRequest.getCurrent()).thenReturn(rc);
    when(httpRequest.getHeader(McpToolSupport.HEADER_AI_AGENT)).thenReturn("claude");

    assertThrows(McpException.class,
      () -> support.run("failing_tool", () -> { throw new NotFoundException("gone"); }));

    verify(aiProvenanceCapture, never()).record(any(), any(), any(), any(), any(), any(), any());
  }
}
