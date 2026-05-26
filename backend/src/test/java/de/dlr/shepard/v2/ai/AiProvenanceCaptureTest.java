package de.dlr.shepard.v2.ai;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.provenance.services.ProvenanceService;
import de.dlr.shepard.spi.ai.Fair2rPredicates;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link AiProvenanceCapture} (TPL9).
 *
 * <p>Verifies:
 * <ul>
 *   <li>Records correctly when all fields are present</li>
 *   <li>Records correctly with only the required type field</li>
 *   <li>Handles null toolName gracefully (no NPE)</li>
 *   <li>Never throws when ProvenanceService returns null (best-effort)</li>
 *   <li>Summary string contains expected f(ai)²r predicate tokens</li>
 *   <li>actionKind is always "AI_ACTION"</li>
 * </ul>
 */
class AiProvenanceCaptureTest {

  @Mock
  ProvenanceService provenanceService;

  AiProvenanceCapture capture;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    capture = new AiProvenanceCapture();
    capture.provenanceService = provenanceService;
    // ProvenanceService returns null on best-effort capture (common in tests)
    when(provenanceService.record(anyString(), any(), any(), any(), anyString(),
      anyString(), anyString(), anyInt(), anyLong(), anyLong()))
      .thenReturn(null);
  }

  @Test
  void record_withAllFields_delegatesToProvenanceServiceWithCorrectActionKind() {
    capture.record(
      AiActivityType.ANNOTATION_SUGGESTION,
      "claude-opus-4-7",
      "sha256-abc123",
      "019e30b0-99a2-79e7-b7d8-c15396095b42",
      Map.of(Fair2rPredicates.RESULTED_IN_WRITE, "true"),
      "list_collections",
      "claude"
    );

    verify(provenanceService).record(
      eq(AiProvenanceCapture.ACTION_KIND),
      isNull(),                // targetKind — not known at MCP transport layer
      eq("019e30b0-99a2-79e7-b7d8-c15396095b42"),
      isNull(),                // agentUsername — read from AuthenticationContext
      argThat(s -> s.contains("ANNOTATION_SUGGESTION")),
      eq("MCP"),
      eq("/v2/mcp/list_collections"),
      eq(200),
      anyLong(),
      anyLong()
    );
  }

  @Test
  void record_summaryContainsFair2rPredicates() {
    final String[] capturedSummary = new String[1];
    when(provenanceService.record(anyString(), any(), any(), any(), anyString(),
      anyString(), anyString(), anyInt(), anyLong(), anyLong()))
      .thenAnswer(inv -> {
        capturedSummary[0] = inv.getArgument(4);
        return null;
      });

    capture.record(
      AiActivityType.ANOMALY_DETECTION,
      "gpt-4o",
      "sha256-deadbeef",
      null,
      null,
      "detect_anomaly",
      "openai-assistant"
    );

    assertNotNull(capturedSummary[0]);
    String s = capturedSummary[0];
    // Must contain the aiActionType predicate key and value
    assertEquals(true, s.contains(Fair2rPredicates.AI_ACTION_TYPE + "=ANOMALY_DETECTION"),
      "summary must contain aiActionType=ANOMALY_DETECTION, was: " + s);
    // Must contain model
    assertEquals(true, s.contains(Fair2rPredicates.USED_MODEL + "=gpt-4o"),
      "summary must contain usedModel=gpt-4o, was: " + s);
    // Must contain agent
    assertEquals(true, s.contains(Fair2rPredicates.INVOKED_BY + "=openai-assistant"),
      "summary must contain invokedBy=openai-assistant, was: " + s);
    // Must contain prompt hash
    assertEquals(true, s.contains(Fair2rPredicates.PROMPT_HASH + "=sha256-deadbeef"),
      "summary must contain promptHash=sha256-deadbeef, was: " + s);
  }

  @Test
  void record_nullToolName_doesNotThrow() {
    assertDoesNotThrow(() ->
      capture.record(AiActivityType.CHAT_RESPONSE, null, null, null, null, null, "claude")
    );
    // Path must default gracefully
    verify(provenanceService).record(
      eq(AiProvenanceCapture.ACTION_KIND),
      isNull(), isNull(), isNull(),
      anyString(),
      eq("MCP"),
      eq("/v2/mcp"),   // fallback path when toolName is null
      eq(200),
      anyLong(), anyLong()
    );
  }

  @Test
  void record_provenanceServiceReturnsNull_noException() {
    // Best-effort: null return from ProvenanceService must not throw
    when(provenanceService.record(anyString(), any(), any(), any(), anyString(),
      anyString(), anyString(), anyInt(), anyLong(), anyLong()))
      .thenReturn(null);

    assertDoesNotThrow(() ->
      capture.record(AiActivityType.SPARQL_GENERATION, "llama3", null, null, null)
    );
  }

  @Test
  void record_shorthandOverload_defaultsToolNameAndAgentId() {
    capture.record(
      AiActivityType.SEMANTIC_ENRICHMENT,
      "mistral-large",
      "sha256-xyz",
      "some-app-id",
      Map.of("custom", "value")
    );

    verify(provenanceService).record(
      eq(AiProvenanceCapture.ACTION_KIND),
      isNull(),
      eq("some-app-id"),
      isNull(),
      argThat(s -> s.contains("SEMANTIC_ENRICHMENT")),
      eq("MCP"),
      eq("/v2/mcp"),   // no toolName in this overload
      eq(200),
      anyLong(), anyLong()
    );
  }

  @Test
  void record_provenanceServiceThrows_doesNotPropagate() {
    when(provenanceService.record(anyString(), any(), any(), any(), anyString(),
      anyString(), anyString(), anyInt(), anyLong(), anyLong()))
      .thenThrow(new RuntimeException("Neo4j is down"));

    // Must NOT throw — best-effort capture
    assertDoesNotThrow(() ->
      capture.record(AiActivityType.IMPORT_MANIFEST_GENERATION, null, null, null, null,
        "create_data_object", "agent-x")
    );
  }

  @Test
  void record_nullType_fallsBackToChatResponse() {
    final String[] capturedSummary = new String[1];
    when(provenanceService.record(anyString(), any(), any(), any(), anyString(),
      anyString(), anyString(), anyInt(), anyLong(), anyLong()))
      .thenAnswer(inv -> { capturedSummary[0] = inv.getArgument(4); return null; });

    capture.record(null, null, null, null, null, "some_tool", "agent-y");

    assertNotNull(capturedSummary[0]);
    assertEquals(true,
      capturedSummary[0].contains(Fair2rPredicates.AI_ACTION_TYPE + "=CHAT_RESPONSE"),
      "null type must default to CHAT_RESPONSE, was: " + capturedSummary[0]);
  }

  @Test
  void actionKindConstant_isAiAction() {
    assertEquals("AI_ACTION", AiProvenanceCapture.ACTION_KIND);
  }
}
