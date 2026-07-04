package de.dlr.shepard.v2.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.spi.ai.AiCapability;
import de.dlr.shepard.spi.ai.AiRegistry;
import de.dlr.shepard.spi.ai.LlmException;
import de.dlr.shepard.spi.ai.LlmProvider;
import de.dlr.shepard.spi.ai.LlmRequest;
import de.dlr.shepard.spi.ai.LlmResponse;
import de.dlr.shepard.spi.ai.Transport;
import io.quarkiverse.mcp.server.McpException;
import jakarta.enterprise.inject.Instance;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * MCP-COV-12 — unit tests for {@link AiMcpTools}.
 */
class AiMcpToolsTest {

  @Mock AiRegistry aiRegistry;
  @Mock McpContextBridge contextBridge;
  @Mock LlmProvider llmProvider;
  @Mock Transport transport;

  AiMcpTools tools;
  McpToolSupport support;

  /** Test-only Instance impl that's either resolvable+returns a bean, or unresolvable. */
  static class StubInstance<T> implements Instance<T> {
    private final T value;
    StubInstance(T value) { this.value = value; }
    @Override public Instance<T> select(java.lang.annotation.Annotation... qualifiers) { return null; }
    @Override public <U extends T> Instance<U> select(Class<U> subtype, java.lang.annotation.Annotation... qualifiers) { return null; }
    @Override public <U extends T> Instance<U> select(jakarta.enterprise.util.TypeLiteral<U> subtype, java.lang.annotation.Annotation... qualifiers) { return null; }
    @Override public boolean isUnsatisfied() { return value == null; }
    @Override public boolean isAmbiguous() { return false; }
    @Override public boolean isResolvable() { return value != null; }
    @Override public void destroy(T instance) {}
    @Override public T get() { return value; }
    @Override public Iterator<T> iterator() {
      return value == null ? List.<T>of().iterator() : List.of(value).iterator();
    }
    @Override public jakarta.enterprise.inject.Instance.Handle<T> getHandle() { return null; }
    @Override public Iterable<? extends jakarta.enterprise.inject.Instance.Handle<T>> handles() { return List.of(); }
  }

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    support = new McpToolSupport();
    support.objectMapper = new ObjectMapper();

    tools = new AiMcpTools();
    tools.aiRegistry = aiRegistry;
    tools.contextBridge = contextBridge;
    tools.support = support;
    // Default: no provider — local-noop posture.
    tools.llmProvider = new StubInstance<>(null);
  }

  // ── ai_capabilities ──────────────────────────────────────────────────────

  @Test
  void capabilitiesReturnsAllEnumSlotsWithEmptyTransports() throws Exception {
    when(aiRegistry.bindings()).thenReturn(Map.of());
    when(aiRegistry.firstEnabledFor(any())).thenReturn(Optional.empty());

    String json = tools.aiCapabilities();
    JsonNode arr = new ObjectMapper().readTree(json);
    assertTrue(arr.isArray());
    assertEquals(AiCapability.values().length, arr.size());
    JsonNode firstRow = arr.get(0);
    assertNotNull(firstRow.get("capability"));
    assertEquals(0, firstRow.get("transports").size());
    assertEquals(false, firstRow.get("hasEnabled").asBoolean());
    assertEquals(false, firstRow.get("providerResolvable").asBoolean());
  }

  @Test
  void capabilitiesReflectsConfiguredTransports() throws Exception {
    when(aiRegistry.bindings()).thenReturn(
      Map.of(AiCapability.TEXT, java.util.Set.of("openai-compat", "anthropic"))
    );
    when(aiRegistry.firstEnabledFor(AiCapability.TEXT)).thenReturn(Optional.of(transport));
    when(aiRegistry.firstEnabledFor(AiCapability.EMBEDDING)).thenReturn(Optional.empty());
    tools.llmProvider = new StubInstance<>(llmProvider);

    String json = tools.aiCapabilities();
    JsonNode arr = new ObjectMapper().readTree(json);
    // Find the TEXT row.
    JsonNode textRow = null;
    for (JsonNode r : arr) {
      if ("TEXT".equals(r.get("capability").asText())) { textRow = r; break; }
    }
    assertNotNull(textRow);
    assertEquals(2, textRow.get("transports").size());
    assertEquals(true, textRow.get("hasEnabled").asBoolean());
    assertEquals(true, textRow.get("providerResolvable").asBoolean());
  }

  // ── ai_invoke — local-noop default ───────────────────────────────────────

  @Test
  void invokeReturnsLocalNoopWhenNoProviderRegistered() throws Exception {
    when(aiRegistry.firstEnabledFor(AiCapability.TEXT)).thenReturn(Optional.empty());

    Map<String, Object> inputs = new LinkedHashMap<>();
    inputs.put("userInstruction", "hi");
    String json = tools.aiInvoke("TEXT", inputs);

    JsonNode root = new ObjectMapper().readTree(json);
    assertEquals("", root.get("result").asText());
    assertEquals("local-noop", root.get("modelId").asText());
    assertEquals(0.0, root.get("confidence").asDouble());
    assertEquals("no transport configured", root.get("note").asText());
  }

  @Test
  void invokeReturnsLocalNoopWhenProviderResolvableButNoTransport() throws Exception {
    tools.llmProvider = new StubInstance<>(llmProvider);
    when(aiRegistry.firstEnabledFor(AiCapability.TEXT)).thenReturn(Optional.empty());

    Map<String, Object> inputs = Map.of("userInstruction", "hi");
    String json = tools.aiInvoke("TEXT", inputs);
    JsonNode root = new ObjectMapper().readTree(json);
    assertEquals("local-noop", root.get("modelId").asText());
  }

  // ── ai_invoke — happy path ────────────────────────────────────────────────

  @Test
  void invokeDispatchesToProviderWhenAvailable() throws Exception {
    tools.llmProvider = new StubInstance<>(llmProvider);
    when(aiRegistry.firstEnabledFor(AiCapability.TEXT)).thenReturn(Optional.of(transport));
    when(transport.id()).thenReturn("openai-compat");
    when(llmProvider.isAvailable(AiCapability.TEXT)).thenReturn(true);
    LlmResponse response = LlmResponse.builder()
      .text("hello, world")
      .activityAppId("act-1")
      .inputTokens(3)
      .outputTokens(2)
      .build();
    when(llmProvider.complete(any(LlmRequest.class))).thenReturn(response);

    Map<String, Object> inputs = new LinkedHashMap<>();
    inputs.put("userInstruction", "say hi");
    inputs.put("temperature", 0.2);
    inputs.put("maxTokens", 100);
    inputs.put("untrustedDocuments", List.of("doc 1", "doc 2"));
    String json = tools.aiInvoke("TEXT", inputs);

    JsonNode root = new ObjectMapper().readTree(json);
    assertEquals("hello, world", root.get("result").asText());
    assertEquals("openai-compat", root.get("modelId").asText());
    assertEquals(1.0, root.get("confidence").asDouble());
    assertEquals("act-1", root.get("activityAppId").asText());
    assertEquals(3, root.get("inputTokens").asInt());
    assertEquals(2, root.get("outputTokens").asInt());
  }

  @Test
  void invokeDegradesToLocalNoopOnProviderException() throws Exception {
    tools.llmProvider = new StubInstance<>(llmProvider);
    when(aiRegistry.firstEnabledFor(AiCapability.TEXT)).thenReturn(Optional.of(transport));
    when(llmProvider.isAvailable(AiCapability.TEXT)).thenReturn(true);
    when(llmProvider.complete(any(LlmRequest.class))).thenThrow(new LlmException("upstream 503"));

    Map<String, Object> inputs = Map.of("userInstruction", "hi");
    String json = tools.aiInvoke("TEXT", inputs);
    JsonNode root = new ObjectMapper().readTree(json);
    assertEquals("local-noop", root.get("modelId").asText());
    assertTrue(root.get("note").asText().contains("upstream 503"));
  }

  // ── ai_invoke — validation ────────────────────────────────────────────────

  @Test
  void invokeRejectsUnknownCapability() {
    McpException ex = assertThrows(McpException.class,
      () -> tools.aiInvoke("WIZARDRY", Map.of("userInstruction", "hi")));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
    assertTrue(ex.getMessage().contains("WIZARDRY"));
  }

  @Test
  void invokeRejectsBlankCapability() {
    McpException ex = assertThrows(McpException.class,
      () -> tools.aiInvoke("", Map.of("userInstruction", "hi")));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  @Test
  void invokeRejectsNullInputs() {
    McpException ex = assertThrows(McpException.class,
      () -> tools.aiInvoke("TEXT", null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  @Test
  void invokeRejectsMissingUserInstruction() {
    McpException ex = assertThrows(McpException.class,
      () -> tools.aiInvoke("TEXT", Map.of("trustedContext", "ctx")));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  @Test
  void invokeRejectsNonNumericMaxTokens() {
    Map<String, Object> inputs = new LinkedHashMap<>();
    inputs.put("userInstruction", "hi");
    inputs.put("maxTokens", "not-a-number");
    // No provider configured — but parsing happens before dispatch only when needed.
    // Configure provider+transport so we get past the local-noop short-circuit.
    tools.llmProvider = new StubInstance<>(llmProvider);
    when(aiRegistry.firstEnabledFor(AiCapability.TEXT)).thenReturn(Optional.of(transport));
    when(llmProvider.isAvailable(AiCapability.TEXT)).thenReturn(true);

    McpException ex = assertThrows(McpException.class, () -> tools.aiInvoke("TEXT", inputs));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  // ── parseCapability helper ────────────────────────────────────────────────

  @Test
  void parseCapabilityAcceptsLowercase() {
    assertEquals(AiCapability.TEXT, AiMcpTools.parseCapability("text"));
    assertEquals(AiCapability.FAST_TEXT, AiMcpTools.parseCapability("fast_text"));
  }

  @Test
  void parseCapabilityRejectsBlank() {
    McpException ex = assertThrows(McpException.class, () -> AiMcpTools.parseCapability(""));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }
}
