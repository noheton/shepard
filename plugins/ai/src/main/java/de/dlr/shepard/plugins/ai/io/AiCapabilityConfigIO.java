package de.dlr.shepard.plugins.ai.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.plugins.ai.entities.AiCapabilityConfig;

/**
 * AI1 — JSON request/response shape for one capability slot config.
 *
 * <p>The raw {@code apiKey} is <b>never</b> returned in GET responses.
 * When a key is present on the entity, {@link #apiKeySet} is
 * {@code true} and {@link #apiKey} carries the mask {@code "***"}.
 * On PATCH, a non-null, non-{@code "***"} value in {@link #apiKey}
 * is written; if the caller sends {@code "***"} or omits the field,
 * the stored key is left unchanged.
 *
 * <p>{@code @JsonInclude(NON_NULL)} — absent optional fields are
 * omitted from GET responses so clients can distinguish "not set" from
 * "explicitly set to null".
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiCapabilityConfigIO {

  /** Capability name (e.g. {@code "TEXT"}). Read-only in responses. */
  public String capability;

  /** Base URL of the OpenAI-compatible endpoint. */
  public String endpointUrl;

  /** Model identifier (e.g. {@code "gpt-4o"}, {@code "llama3.2"}). */
  public String model;

  /**
   * API key. In GET responses: {@code "***"} if a key is configured,
   * {@code null} if no key is set. In PATCH requests: non-null + not
   * {@code "***"} → update; absent or {@code "***"} → leave unchanged.
   */
  public String apiKey;

  /**
   * {@code true} when a non-null key is stored for this slot.
   * Always present in GET responses; not required in PATCH requests.
   */
  public Boolean apiKeySet;

  /** Transport kind. v0: always {@code "OPENAI_COMPAT"}. */
  public String transport;

  /** Admin-configurable guardrails prefix prepended before the plugin system prompt. */
  public String guardrailsPrefix;

  /** Admin-configurable guardrails suffix appended after the plugin system prompt. */
  public String guardrailsSuffix;

  /** Max tokens cap. Overrides the per-call LlmRequest value. */
  public Integer maxTokens;

  /** Temperature. Overrides the per-call LlmRequest value. */
  public Double temperature;

  /** {@code true} when this capability slot is enabled. */
  public Boolean enabled;

  /**
   * Project an {@link AiCapabilityConfig} entity onto the IO shape,
   * masking the raw API key.
   *
   * @param cfg the entity to project
   * @return the IO representation (apiKey masked)
   */
  public static AiCapabilityConfigIO from(AiCapabilityConfig cfg) {
    AiCapabilityConfigIO io = new AiCapabilityConfigIO();
    io.capability = cfg.getCapability();
    io.endpointUrl = cfg.getEndpointUrl();
    io.model = cfg.getModel();
    io.apiKeySet = cfg.getApiKey() != null && !cfg.getApiKey().isBlank();
    io.apiKey = Boolean.TRUE.equals(io.apiKeySet) ? "***" : null;
    io.transport = cfg.getTransport();
    io.guardrailsPrefix = cfg.getGuardrailsPrefix();
    io.guardrailsSuffix = cfg.getGuardrailsSuffix();
    io.maxTokens = cfg.getMaxTokens();
    io.temperature = cfg.getTemperature();
    io.enabled = cfg.getEnabled();
    return io;
  }
}
