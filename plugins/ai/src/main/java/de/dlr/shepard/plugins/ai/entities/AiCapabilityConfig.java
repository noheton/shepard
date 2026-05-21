package de.dlr.shepard.plugins.ai.entities;

import de.dlr.shepard.common.identifier.HasAppId;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;

/**
 * AI1 — runtime-mutable config for one LLM capability slot.
 *
 * <p>One {@code :AiCapabilityConfig} node per {@code AiCapability} enum value.
 * Seeded on first access by {@code AiCapabilityConfigService.getOrSeed()} from
 * the deploy-time {@code application.properties} defaults (or with an all-null,
 * {@code enabled=false} skeleton when no install default is configured).
 *
 * <p>Runtime mutations happen via
 * {@code PATCH /v2/admin/ai/capabilities/{capability}}.
 *
 * <p>The {@code apiKey} is stored as plain text in v0. Encryption at rest
 * is a v0b concern (aidocs/86 §3).
 */
@NodeEntity
@Getter
@Setter
@NoArgsConstructor
@ToString
public class AiCapabilityConfig implements HasAppId {

  @Id
  @GeneratedValue
  private Long id;

  /** Application-level identifier (UUID v7). Minted on first save. */
  @Property("appId")
  private String appId;

  /**
   * Name of the {@code AiCapability} enum constant this node configures
   * (e.g. {@code "TEXT"}, {@code "EMBEDDING"}). Uniqueness guaranteed by
   * the service layer; one config node per capability.
   */
  @Property("capability")
  private String capability;

  /** Base URL of the OpenAI-compatible endpoint (no trailing slash). */
  @Property("endpointUrl")
  private String endpointUrl;

  /** Model identifier passed as {@code model} in the chat completions request. */
  @Property("model")
  private String model;

  /**
   * API key for the endpoint. Stored as plain text (v0).
   * Never returned in GET responses — masked as {@code ***} in the IO shape.
   */
  @Property("apiKey")
  private String apiKey;

  /**
   * Transport kind. v0 only supports {@code "OPENAI_COMPAT"}. The field
   * is stored so future transports (ANTHROPIC, OLLAMA, CUSTOM) can be
   * added without a schema change.
   */
  @Property("transport")
  private String transport;

  /**
   * Prepended before the plugin system prompt in every call to this slot.
   * Admin-configurable; default null (no prefix). Typically used for
   * institution-wide guardrails.
   */
  @Property("guardrailsPrefix")
  private String guardrailsPrefix;

  /**
   * Appended after the plugin system prompt in every call to this slot.
   * Admin-configurable; default null.
   */
  @Property("guardrailsSuffix")
  private String guardrailsSuffix;

  /** Max tokens cap for calls to this slot. Overrides the LlmRequest value when set. */
  @Property("maxTokens")
  private Integer maxTokens;

  /** Temperature for calls to this slot. Overrides the LlmRequest value when set. */
  @Property("temperature")
  private Double temperature;

  /**
   * Master toggle. When {@code false} (the default for a freshly-seeded
   * skeleton), {@code LlmProviderImpl.complete()} throws
   * {@code LlmException("capability not enabled: <cap>")} immediately.
   */
  @Property("enabled")
  private Boolean enabled;

  @Override
  public int hashCode() {
    return Objects.hash(appId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof AiCapabilityConfig other)) return false;
    return Objects.equals(appId, other.appId);
  }
}
