package de.dlr.shepard.v2.mcp;

import de.dlr.shepard.spi.ai.AiCapability;
import de.dlr.shepard.spi.ai.AiException;
import de.dlr.shepard.spi.ai.AiRegistry;
import de.dlr.shepard.spi.ai.LlmException;
import de.dlr.shepard.spi.ai.LlmProvider;
import de.dlr.shepard.spi.ai.LlmRequest;
import de.dlr.shepard.spi.ai.LlmResponse;
import de.dlr.shepard.spi.ai.Transport;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * MCP-COV-12 — AI capability invocation MCP tools.
 *
 * <p>Thin wrapper over the {@link LlmProvider} / {@link AiRegistry} SPI
 * (aidocs/platform/86, doc 47 §SPI-AI). Lets an MCP caller introspect the
 * available AI capability slots and invoke one for a synchronous text
 * completion without the per-plugin LLM glue.
 *
 * <p><b>Local default contract</b> (CLAUDE.md "ship a working local
 * default for every AI capability"): when the registry has no enabled
 * {@link Transport} for the requested capability — or no
 * {@link LlmProvider} bean is on the classpath at all — {@code ai_invoke}
 * returns a structured {@code local-noop} payload:
 * <pre>
 *   { "result": "", "modelId": "local-noop", "confidence": 0.0,
 *     "note": "no transport configured" }
 * </pre>
 * No exception, no 503, no required API key. This matches the
 * {@link de.dlr.shepard.publish.minter.MinterRegistry}-derived fail-soft
 * posture every SPI registry in this fork uses.
 *
 * <p><b>What's not in this v0</b> — model overrides, streaming, image
 * generation. Those need richer parameter shapes; the v0 surface is
 * text-in/text-out only. The {@link AiCapability#TEXT} slot is the
 * canonical happy-path; other capabilities resolve through the same
 * {@code firstEnabledFor} dispatch but their richer modes (vision
 * multimodal, structured JSON, embeddings) need follow-up wrappers
 * (AI-MCP-INVOKE-V1 tracked separately in aidocs/16).
 */
@ApplicationScoped
public class AiMcpTools {

  /** Local default model id when no transport is configured. */
  static final String LOCAL_NOOP_MODEL_ID = "local-noop";

  /** Local default note explaining the degraded mode. */
  static final String LOCAL_NOOP_NOTE = "no transport configured";

  @Inject
  AiRegistry aiRegistry;

  /**
   * Instance-injected so the tool stays usable when no plugin contributes
   * an {@link LlmProvider} bean. {@code llmProvider.isResolvable()} is the
   * fail-soft gate.
   */
  @Inject
  Instance<LlmProvider> llmProvider;

  @Inject
  McpContextBridge contextBridge;

  @Inject
  McpToolSupport support;

  // ─── ai_capabilities ────────────────────────────────────────────────────────

  @Tool(
    name = "ai_capabilities",
    description =
      "List the AI capabilities the registry knows about and which transports " +
      "(plugins) advertise each.\n\n" +
      "Capability vocabulary: TEXT, FAST_TEXT, IMAGE_GEN, VISION, EMBEDDING, " +
      "STRUCTURED, TRANSCRIPTION, MODERATION (see " +
      "de.dlr.shepard.spi.ai.AiCapability).\n\n" +
      "Each row:\n" +
      "  capability       — enum name (use as the `capability` arg of `ai_invoke`).\n" +
      "  transports[]     — transport ids advertising this capability (may be empty).\n" +
      "  hasEnabled       — true when at least one transport is enabled.\n" +
      "  providerResolvable — true when an LlmProvider bean is on the classpath.\n\n" +
      "An empty `transports[]` plus `providerResolvable=false` is the canonical " +
      "unconfigured state — `ai_invoke` will return the local-noop response. " +
      "This is the deliberate fail-soft default per CLAUDE.md."
  )
  public String aiCapabilities() {
    return support.run("ai_capabilities", () -> {
      contextBridge.bind();

      boolean providerResolvable = llmProvider != null && llmProvider.isResolvable();
      Map<AiCapability, Set<String>> bindings = aiRegistry == null
        ? Map.of()
        : aiRegistry.bindings();

      List<Map<String, Object>> result = new ArrayList<>(AiCapability.values().length);
      for (AiCapability cap : AiCapability.values()) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("capability", cap.name());
        Set<String> ids = bindings.getOrDefault(cap, Set.of());
        row.put("transports", new ArrayList<>(ids));
        row.put("hasEnabled", aiRegistry != null && aiRegistry.firstEnabledFor(cap).isPresent());
        row.put("providerResolvable", providerResolvable);
        result.add(row);
      }
      return support.toJson(result);
    });
  }

  // ─── ai_invoke ──────────────────────────────────────────────────────────────

  @Tool(
    name = "ai_invoke",
    description =
      "Invoke an AI capability via the registered LlmProvider. v0 surface is " +
      "text-in / text-out (TEXT / FAST_TEXT capabilities); other capabilities " +
      "are accepted but require a transport that advertises them.\n\n" +
      "Parameters:\n" +
      "  capability  — required, one of: TEXT, FAST_TEXT, IMAGE_GEN, VISION, " +
      "                EMBEDDING, STRUCTURED, TRANSCRIPTION, MODERATION.\n" +
      "  inputs      — required, a map with at least `userInstruction` (string).\n" +
      "                Optional keys: `trustedContext`, `pluginSystemPrompt`, " +
      "                `untrustedDocuments` (List<String>), `maxTokens` (int), " +
      "                `temperature` (double).\n\n" +
      "Returns: { result, modelId, confidence, activityAppId, inputTokens, outputTokens, note? }\n\n" +
      "Local default: when no Transport is configured (or no LlmProvider plugin " +
      "is installed), returns:\n" +
      "  { result: \"\", modelId: \"" + LOCAL_NOOP_MODEL_ID + "\", confidence: 0.0,\n" +
      "    note: \"" + LOCAL_NOOP_NOTE + "\" }\n" +
      "No exception, no API key required — this is the canonical fail-soft posture " +
      "(CLAUDE.md \"ship a working local default for every AI capability\")."
  )
  public String aiInvoke(
    @ToolArg(description = "Capability slot name (e.g. TEXT). See `ai_capabilities` for available slots.") String capability,
    @ToolArg(description = "Invocation inputs. At minimum: {\"userInstruction\":\"...\"}. Optional: trustedContext, pluginSystemPrompt, untrustedDocuments[], maxTokens, temperature.") Map<String, Object> inputs
  ) {
    return support.run("ai_invoke", () -> {
      contextBridge.bind();

      AiCapability cap = parseCapability(capability);
      if (inputs == null) {
        throw McpToolSupport.invalidParams("inputs is required.");
      }
      String userInstruction = stringField(inputs, "userInstruction");
      if (userInstruction == null || userInstruction.isBlank()) {
        throw McpToolSupport.invalidParams("inputs.userInstruction is required.");
      }

      // Local default short-circuit — no plugin, no transport.
      boolean providerResolvable = llmProvider != null && llmProvider.isResolvable();
      Optional<Transport> enabled = aiRegistry == null
        ? Optional.empty()
        : aiRegistry.firstEnabledFor(cap);
      if (!providerResolvable || enabled.isEmpty()) {
        return support.toJson(localNoopResponse());
      }

      LlmProvider provider = llmProvider.get();
      if (!provider.isAvailable(cap)) {
        return support.toJson(localNoopResponse());
      }

      // Build the LlmRequest from inputs.
      LlmRequest.Builder b = LlmRequest.builder(cap)
        .userInstruction(userInstruction);
      String trustedContext = stringField(inputs, "trustedContext");
      if (trustedContext != null) b.trustedContext(trustedContext);
      String pluginSystemPrompt = stringField(inputs, "pluginSystemPrompt");
      if (pluginSystemPrompt != null) b.pluginSystemPrompt(pluginSystemPrompt);
      Integer maxTokens = intField(inputs, "maxTokens");
      if (maxTokens != null) b.maxTokens(maxTokens);
      Double temperature = doubleField(inputs, "temperature");
      if (temperature != null) b.temperature(temperature);

      Object docs = inputs.get("untrustedDocuments");
      if (docs instanceof List<?> list) {
        for (Object o : list) {
          if (o != null) b.addUntrustedDocument(o.toString());
        }
      }

      try {
        LlmResponse response = provider.complete(b.build());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("result", response.text());
        body.put("modelId", inferModelId(enabled.get()));
        body.put("confidence", 1.0);
        body.put("activityAppId", response.activityAppId());
        body.put("inputTokens", response.inputTokens());
        body.put("outputTokens", response.outputTokens());
        return support.toJson(body);
      } catch (LlmException | AiException e) {
        // Provider unavailable / upstream failure — degrade rather than 500.
        Map<String, Object> body = localNoopResponse();
        body.put("note", LOCAL_NOOP_NOTE + " (provider error: " + e.getMessage() + ")");
        return support.toJson(body);
      }
    });
  }

  // ─── helpers ────────────────────────────────────────────────────────────────

  private static Map<String, Object> localNoopResponse() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("result", "");
    body.put("modelId", LOCAL_NOOP_MODEL_ID);
    body.put("confidence", 0.0);
    body.put("note", LOCAL_NOOP_NOTE);
    return body;
  }

  static AiCapability parseCapability(String s) {
    if (s == null || s.isBlank()) {
      throw McpToolSupport.invalidParams(
        "capability is required (one of: " + capabilityVocabulary() + ")."
      );
    }
    try {
      return AiCapability.valueOf(s.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException iae) {
      throw McpToolSupport.invalidParams(
        "Unknown capability '" + s + "'. Expected one of: " + capabilityVocabulary() + "."
      );
    }
  }

  private static String capabilityVocabulary() {
    StringBuilder sb = new StringBuilder();
    boolean first = true;
    for (AiCapability c : EnumSet.allOf(AiCapability.class)) {
      if (!first) sb.append(", ");
      sb.append(c.name());
      first = false;
    }
    return sb.toString();
  }

  private static String inferModelId(Transport t) {
    if (t == null) return LOCAL_NOOP_MODEL_ID;
    String id = t.id();
    return id == null ? LOCAL_NOOP_MODEL_ID : id;
  }

  private static String stringField(Map<String, Object> m, String key) {
    Object v = m.get(key);
    return v == null ? null : v.toString();
  }

  private static Integer intField(Map<String, Object> m, String key) {
    Object v = m.get(key);
    if (v == null) return null;
    if (v instanceof Number n) return n.intValue();
    try { return Integer.parseInt(v.toString()); }
    catch (NumberFormatException nfe) {
      throw McpToolSupport.invalidParams("inputs." + key + " must be an integer.");
    }
  }

  private static Double doubleField(Map<String, Object> m, String key) {
    Object v = m.get(key);
    if (v == null) return null;
    if (v instanceof Number n) return n.doubleValue();
    try { return Double.parseDouble(v.toString()); }
    catch (NumberFormatException nfe) {
      throw McpToolSupport.invalidParams("inputs." + key + " must be a number.");
    }
  }
}
