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
 * AI1 — provenance node written for every LLM call.
 *
 * <p>One {@code :AiActivity} node per completed (or attempted)
 * {@code LlmProviderImpl.complete()} invocation. The node's
 * {@code appId} is returned to the calling plugin as
 * {@code LlmResponse.activityAppId()} so it can attach the provenance
 * record to whatever artefact the call produces.
 *
 * <p>Calling plugins must NOT write their own activity node — the
 * {@code LlmProvider} implementation handles that exclusively (per the
 * SPI contract in {@code LlmProvider.complete()} Javadoc).
 *
 * <p>Per {@code aidocs/86 §8}:
 * <ul>
 *   <li>{@link #promptHash} is SHA-256 hex of the fully assembled prompt
 *       (privacy default — the raw text is not stored).</li>
 *   <li>{@link #inputTokens}/{@link #outputTokens} are recorded for
 *       provenance, not billing (billing is delegated to the gateway).</li>
 * </ul>
 */
@NodeEntity
@Getter
@Setter
@NoArgsConstructor
@ToString
public class AiActivity implements HasAppId {

  @Id
  @GeneratedValue
  private Long id;

  /** Application-level identifier (UUID v7). Minted on save by GenericDAO. */
  @Property("appId")
  private String appId;

  /** Name of the {@code AiCapability} enum constant (e.g. {@code "TEXT"}). */
  @Property("capability")
  private String capability;

  /** Plugin id of the caller (e.g. {@code "wiki-writer"}). */
  @Property("pluginId")
  private String pluginId;

  /** Model identifier passed in the request (e.g. {@code "gpt-4o"}). */
  @Property("modelId")
  private String modelId;

  /**
   * Provider label derived from the endpoint URL or config (e.g.
   * {@code "openai"}, {@code "litellm"}, {@code "ollama"}).
   */
  @Property("provider")
  private String provider;

  /**
   * SHA-256 hex of the fully-assembled prompt text (system + user layers
   * concatenated). Raw prompt text is never stored (privacy default;
   * {@code storePromptText} admin config controls an opt-in exception —
   * not implemented in v0).
   */
  @Property("promptHash")
  private String promptHash;

  /** Input token count reported by the model (from {@code usage.prompt_tokens}). */
  @Property("inputTokens")
  private Integer inputTokens;

  /** Output token count reported by the model (from {@code usage.completion_tokens}). */
  @Property("outputTokens")
  private Integer outputTokens;

  /** Epoch millis when the call was completed. */
  @Property("occurredAt")
  private Long occurredAt;

  @Override
  public int hashCode() {
    return Objects.hash(appId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof AiActivity other)) return false;
    return Objects.equals(appId, other.appId);
  }
}
