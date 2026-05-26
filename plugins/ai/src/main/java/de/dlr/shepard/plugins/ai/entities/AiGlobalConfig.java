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
 * AI1a — runtime-mutable global AI-plugin config singleton.
 *
 * <p>Mirrors the {@code :UnhideConfig} (UH1a) / {@code :SemanticConfig}
 * (N1c2) shapes per CLAUDE.md "Always: surface operator knobs in
 * the admin config". One {@code :AiGlobalConfig} node is seeded on
 * first startup from {@code shepard.ai.*} install-time defaults;
 * subsequent PATCHes against {@code /v2/admin/ai/config} mutate
 * this node in place.
 *
 * <p>Field set is the cross-capability global posture per
 * {@code aidocs/platform/86-ai-plugin-design.md §10}:
 *
 * <ul>
 *   <li>{@link #injectionGuardEnabled} — pre-flight scan toggle</li>
 *   <li>{@link #injectionGuardSensitivity} — {@code LOW|MEDIUM|HIGH}</li>
 *   <li>{@link #blockOnSuspiciousContent} — fail-closed on flag</li>
 *   <li>{@link #storePromptText} — store raw user instruction text
 *       (rather than just its hash) on the {@code :AiActivity} node;
 *       required for AI Act compliance audits in some regulatory
 *       environments</li>
 *   <li>{@link #auditAllCalls} — log read-only / ephemeral calls
 *       too, not just write-resulting ones</li>
 * </ul>
 *
 * <p>Per-capability slot config (endpointUrl, model, apiKey,
 * transport, …) lives on {@link AiCapabilityConfig}; this singleton
 * carries only the cross-capability posture knobs.
 *
 * <p><b>Precedence.</b> Runtime field values win; deploy-time
 * {@code shepard.ai.*} properties are install defaults that seed
 * the singleton on first start.
 */
@NodeEntity
@Getter
@Setter
@NoArgsConstructor
@ToString
public class AiGlobalConfig implements HasAppId {

  /**
   * Sensitivity level for the doc 86 §6.2 pre-flight injection scan.
   * {@code HIGH} additionally enables the §6.3 canary-output check.
   */
  public enum InjectionGuardSensitivity {
    LOW,
    MEDIUM,
    HIGH,
  }

  @Id
  @GeneratedValue
  private Long id;

  @Property("appId")
  private String appId;

  /**
   * Master toggle for the pre-flight injection scan over
   * {@code untrustedDocuments[]} + MCP tool results. Default
   * {@code true} — a fresh install never accidentally pipes
   * unscanned external content into a model prompt.
   */
  @Property("injectionGuardEnabled")
  private boolean injectionGuardEnabled = true;

  /**
   * Sensitivity level. {@code MEDIUM} matches the doc 86 §6.2
   * default; {@code HIGH} additionally enables the §6.3 canary
   * output validation pass.
   *
   * <p>Stored as a string so future enum value additions don't
   * require a Neo4j migration.
   */
  @Property("injectionGuardSensitivity")
  private String injectionGuardSensitivity = InjectionGuardSensitivity.MEDIUM.name();

  /**
   * When {@code true}, flagged content blocks the call entirely
   * (the {@code :AiActivity} node is still written with
   * {@code injectionFlagged=true} for the audit trail). When
   * {@code false} (default), flagged calls log + proceed —
   * matches doc 86 §6.2's safer-default posture.
   */
  @Property("blockOnSuspiciousContent")
  private boolean blockOnSuspiciousContent = false;

  /**
   * When {@code true}, the {@code :AiActivity} node carries the
   * raw user-instruction text (per the {@code promptText}
   * f(ai)²r predicate). When {@code false} (default, privacy
   * posture), only the SHA-256 hash is stored. AI Act compliance
   * audits in some regulatory environments require {@code true}.
   */
  @Property("storePromptText")
  private boolean storePromptText = false;

  /**
   * When {@code true}, every LLM call writes an {@code :AiActivity}
   * node — including read-only / ephemeral UI summaries that don't
   * persist artefacts. When {@code false} (default), only
   * write-resulting calls produce provenance.
   */
  @Property("auditAllCalls")
  private boolean auditAllCalls = false;

  /** For test fixtures only — bypasses OGM's id assignment. */
  public AiGlobalConfig(long id) {
    this.id = id;
  }

  /** Convenience accessor that parses the string-stored enum. */
  public InjectionGuardSensitivity getSensitivityEnum() {
    if (injectionGuardSensitivity == null) return InjectionGuardSensitivity.MEDIUM;
    try {
      return InjectionGuardSensitivity.valueOf(injectionGuardSensitivity);
    } catch (IllegalArgumentException e) {
      return InjectionGuardSensitivity.MEDIUM;
    }
  }

  @Override
  public int hashCode() {
    return Objects.hash(appId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof AiGlobalConfig other)) return false;
    return Objects.equals(appId, other.appId);
  }
}
