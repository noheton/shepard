package de.dlr.shepard.plugins.ai.config;

import com.fasterxml.jackson.databind.JsonNode;
import de.dlr.shepard.plugins.ai.io.AiCapabilityConfigIO;
import de.dlr.shepard.plugins.ai.services.AiCapabilityConfigService;
import de.dlr.shepard.spi.ai.AiCapability;
import de.dlr.shepard.v2.admin.config.spi.ConfigDescriptor;
import de.dlr.shepard.v2.admin.config.spi.ConfigPatchException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * APISIMP-AI-ADMIN-REST — migrates the bespoke AI capability-slot config
 * endpoints onto the generic {@code /v2/admin/config/{feature}} surface.
 *
 * <p>Feature name: {@code "ai"}.
 *
 * <p>{@code GET /v2/admin/config/ai} returns the full list of
 * {@link AiCapabilityConfigIO} (one per {@link AiCapability} value).
 *
 * <p>{@code PATCH /v2/admin/config/ai} accepts an RFC-7396 merge-patch
 * body keyed by capability name:
 * <pre>{@code
 * {
 *   "TEXT":      { "enabled": true, "endpointUrl": "http://..." },
 *   "EMBEDDING": { "model": "text-embedding-3-small" }
 * }
 * }</pre>
 * Each capability key is optional; absent keys leave the slot unchanged.
 * Within a slot, the same RFC-7396 rules apply (absent field = leave
 * alone, explicit null = clear, value = replace).
 *
 * <p>Replaces {@code AiAdminRest} at {@code /v2/admin/ai/capabilities}.
 * The old paths return 404 as of APISIMP-AI-ADMIN-REST.
 */
@ApplicationScoped
public class AiConfigDescriptor implements ConfigDescriptor<List<AiCapabilityConfigIO>> {

  @Inject
  AiCapabilityConfigService service;

  @Override
  public String featureName() {
    return "ai";
  }

  @Override
  public String description() {
    return "LLM capability slot configurations (TEXT, FAST_TEXT, IMAGE_GEN, VISION, EMBEDDING, " +
      "STRUCTURED, TRANSCRIPTION, MODERATION). PATCH body: object keyed by capability name.";
  }

  @Override
  public List<AiCapabilityConfigIO> currentShape() {
    return service.getAllConfigs().stream()
      .map(AiCapabilityConfigIO::from)
      .toList();
  }

  /**
   * Apply a per-slot RFC-7396 merge-patch. The {@code patch} must be a JSON
   * object whose keys are {@link AiCapability} names. Each value is itself a
   * merge-patch applied to that slot (absent fields left alone, explicit null
   * clears). Unknown capability names return 400.
   */
  @Override
  public List<AiCapabilityConfigIO> applyMergePatch(JsonNode patch) throws ConfigPatchException {
    List<String> unknownKeys = new ArrayList<>();
    Iterator<Map.Entry<String, JsonNode>> fields = patch.fields();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> entry = fields.next();
      String capName = entry.getKey();
      AiCapability cap = parseCapability(capName);
      if (cap == null) {
        unknownKeys.add(capName);
        continue;
      }
      JsonNode slotPatch = entry.getValue();
      if (!slotPatch.isObject()) {
        throw ConfigPatchException.badRequest(
          "/problems/ai.config.invalid-slot-patch",
          "Invalid slot patch",
          "Value for capability '" + capName + "' must be a JSON object; got: " + slotPatch.getNodeType()
        );
      }
      AiCapabilityConfigIO io = slotPatchToIO(slotPatch);
      service.upsertConfig(cap, io);
    }
    if (!unknownKeys.isEmpty()) {
      String validValues = List.of(AiCapability.values()).stream()
        .map(Enum::name).toList().toString();
      throw ConfigPatchException.badRequest(
        "/problems/ai.config.unknown-capability",
        "Unknown AI capability",
        "Unknown capability key(s): " + unknownKeys + ". Valid values: " + validValues
      );
    }
    return currentShape();
  }

  // ─── helpers ─────────────────────────────────────────────────────

  private static AiCapability parseCapability(String name) {
    try {
      return AiCapability.valueOf(name);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  /**
   * Map a per-slot {@link JsonNode} patch to {@link AiCapabilityConfigIO}.
   * Fields absent in the node are left null (service treats null as "leave alone").
   */
  private static AiCapabilityConfigIO slotPatchToIO(JsonNode node) {
    AiCapabilityConfigIO io = new AiCapabilityConfigIO();
    if (node.has("endpointUrl")) {
      io.endpointUrl = node.get("endpointUrl").isNull() ? null : node.get("endpointUrl").asText();
    }
    if (node.has("model")) {
      io.model = node.get("model").isNull() ? null : node.get("model").asText();
    }
    if (node.has("apiKey")) {
      io.apiKey = node.get("apiKey").isNull() ? null : node.get("apiKey").asText();
    }
    if (node.has("transport")) {
      io.transport = node.get("transport").isNull() ? null : node.get("transport").asText();
    }
    if (node.has("guardrailsPrefix")) {
      io.guardrailsPrefix = node.get("guardrailsPrefix").isNull()
        ? null
        : node.get("guardrailsPrefix").asText();
    }
    if (node.has("guardrailsSuffix")) {
      io.guardrailsSuffix = node.get("guardrailsSuffix").isNull()
        ? null
        : node.get("guardrailsSuffix").asText();
    }
    if (node.has("maxTokens")) {
      io.maxTokens = node.get("maxTokens").isNull() ? null : node.get("maxTokens").intValue();
    }
    if (node.has("temperature")) {
      io.temperature = node.get("temperature").isNull()
        ? null
        : node.get("temperature").doubleValue();
    }
    if (node.has("enabled")) {
      io.enabled = node.get("enabled").isNull() ? null : node.get("enabled").booleanValue();
    }
    return io;
  }
}
