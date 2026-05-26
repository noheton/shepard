package de.dlr.shepard.v2.ai;

import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.provenance.services.ProvenanceService;
import de.dlr.shepard.spi.ai.Fair2rPredicates;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.Map;

/**
 * TPL9 — captures inbound AI-agent MCP tool calls as provenance
 * {@code :Activity} nodes with {@code actionKind = "AI_ACTION"} and
 * f(ai)²r metadata encoded in the {@code summary} field.
 *
 * <p>This bean is the structural plumbing for AI transparency per
 * {@code aidocs/platform/89-tpl9-fair2r-ai-provenance.md}. It is
 * <em>not</em> for outbound LLM calls — those are handled by
 * {@code plugins/ai/} which writes {@code :AiActivity} nodes.
 *
 * <p>Distinction:
 * <ul>
 *   <li><b>Outbound</b> (AI1 / plugins/ai): Shepard calls an LLM →
 *       writes {@code :AiActivity}. Covers anomaly detection, annotation
 *       suggestions, etc.</li>
 *   <li><b>Inbound</b> (TPL9 / this bean): an AI agent calls Shepard's
 *       MCP surface → writes a supplementary {@code :Activity} with
 *       {@code actionKind = "AI_ACTION"} and f(ai)²r predicates in the
 *       summary. EU AI Act Article 50 transparency hook.</li>
 * </ul>
 *
 * <p>Capture is <strong>best-effort</strong>: this bean never throws.
 * A Neo4j write failure silently drops the provenance record — the
 * underlying tool call is unaffected.
 *
 * <p>No-op posture: when {@code X-AI-Agent} is absent, nothing is
 * recorded. The standard {@code ProvenanceCaptureFilter} already stamps
 * a baseline {@code :Activity} for every 2xx mutation regardless of AI
 * origin.
 *
 * <p>When PROMPT1 ({@code aidocs/semantics/99}) ships, this bean will
 * delegate to the richer {@code PromptRunService} in
 * {@code shepard-plugin-promptlog} instead of the minimal
 * {@code ProvenanceService} delegation below.
 *
 * @see de.dlr.shepard.spi.ai.Fair2rPredicates
 * @see AiActivityType
 */
@ApplicationScoped
public class AiProvenanceCapture {

  /**
   * {@code actionKind} value stamped on {@code :Activity} nodes written
   * by this bean — distinct from the CRUD verbs used by the standard
   * capture filter.
   */
  public static final String ACTION_KIND = "AI_ACTION";

  /**
   * Pseudo-method stamped on the {@code :Activity} node to distinguish
   * MCP tool invocations from regular HTTP verbs.
   */
  private static final String MCP_METHOD = "MCP";

  @Inject
  ProvenanceService provenanceService;

  /**
   * Request-scoped auth context, injected via {@link Instance} to avoid the
   * {@code @ApplicationScoped} → {@code @RequestScoped} scope mismatch.
   * The {@code .get()} call is safe inside an active request scope (every
   * MCP tool invocation runs in one).
   */
  @Inject
  Instance<AuthenticationContext> authContextInstance;

  /**
   * Record one inbound AI-agent MCP tool invocation as a provenance
   * {@code :Activity} node.
   *
   * <p>The activity is stamped with:
   * <ul>
   *   <li>{@code actionKind = "AI_ACTION"}</li>
   *   <li>{@code summary} = compact f(ai)²r attribute string built from
   *       {@code type}, {@code modelId}, and the {@code metadata} map</li>
   *   <li>{@code method = "MCP"}, {@code path = "/v2/mcp/<toolName>"}</li>
   *   <li>{@code agentUsername} from the authenticated JWT principal
   *       (already populated in {@link ProvenanceService}'s call chain)</li>
   * </ul>
   *
   * <p>This method is <strong>never-throw</strong>: every exception is
   * caught and logged at DEBUG. Provenance is observability, not contract.
   *
   * @param type         semantic classification of the action (from
   *                     {@code X-AI-Activity-Type} header or default
   *                     {@link AiActivityType#CHAT_RESPONSE})
   * @param modelId      model identifier reported by the calling agent
   *                     (from {@code X-AI-Model} header; may be
   *                     {@code null})
   * @param promptHash   SHA-256 hash of the user instruction (from
   *                     {@code X-AI-Prompt-Hash} header; may be
   *                     {@code null})
   * @param subjectAppId appId of the primary entity the tool acted on;
   *                     {@code null} when the action has no single target
   * @param metadata     additional f(ai)²r key-value pairs drawn from
   *                     {@link Fair2rPredicates} constants; may be
   *                     {@code null} or empty
   */
  public void record(
    AiActivityType type,
    String modelId,
    String promptHash,
    String subjectAppId,
    Map<String, Object> metadata
  ) {
    record(type, modelId, promptHash, subjectAppId, metadata, null, null);
  }

  /**
   * Extended variant with explicit tool name and agent identifier.
   * Callers in {@code McpToolSupport.run()} use this overload so the
   * path segment and agent identity are captured verbatim.
   *
   * @param type         see {@link #record(AiActivityType, String, String, String, Map)}
   * @param modelId      see {@link #record(AiActivityType, String, String, String, Map)}
   * @param promptHash   see {@link #record(AiActivityType, String, String, String, Map)}
   * @param subjectAppId see {@link #record(AiActivityType, String, String, String, Map)}
   * @param metadata     see {@link #record(AiActivityType, String, String, String, Map)}
   * @param toolName     name of the MCP tool that was invoked; used to
   *                     form the {@code path} field
   * @param agentId      value of the {@code X-AI-Agent} header; the
   *                     agent/framework identifier
   */
  public void record(
    AiActivityType type,
    String modelId,
    String promptHash,
    String subjectAppId,
    Map<String, Object> metadata,
    String toolName,
    String agentId
  ) {
    try {
      AiActivityType effectiveType = type != null ? type : AiActivityType.CHAT_RESPONSE;
      String summary = buildSummary(effectiveType, modelId, agentId, promptHash, metadata);
      String path = toolName != null ? "/v2/mcp/" + toolName : "/v2/mcp";
      String agentUsername = resolveUsername();
      long now = System.currentTimeMillis();
      provenanceService.record(
        ACTION_KIND,
        null,            // targetKind — not known at MCP transport layer
        subjectAppId,
        agentUsername,   // resolved from the per-request AuthenticationContext
        summary,
        MCP_METHOD,
        path,
        200,             // recorded only on successful tool execution
        now,
        now
      );
    } catch (RuntimeException e) {
      // Provenance is observability; never block the tool call on it.
      Log.debugf(e, "TPL9: AI provenance capture failed for type=%s toolName=%s", type, toolName);
    }
  }

  /**
   * Resolve the authenticated username from the per-request
   * {@link AuthenticationContext}. Best-effort — returns {@code null}
   * when there is no active request scope (e.g. in unit tests).
   */
  private String resolveUsername() {
    try {
      AuthenticationContext ctx = authContextInstance.get();
      return ctx == null ? null : ctx.getCurrentUserName();
    } catch (RuntimeException e) {
      return null;
    }
  }

  /**
   * Build a compact f(ai)²r summary string encoding the key provenance
   * predicates. Example:
   * {@code "AI_ACTION type=ANNOTATION_SUGGESTION model=claude-opus-4-7 agent=claude"}
   *
   * <p>This format is a stopgap until PROMPT1 ships its full SHACL RDF
   * substrate. The string is human-readable in the admin activity log and
   * machine-parseable with a simple split-on-space approach. Max 256 chars
   * (enforced by {@code ProvenanceService} truncation).
   */
  private static String buildSummary(
    AiActivityType type,
    String modelId,
    String agentId,
    String promptHash,
    Map<String, Object> metadata
  ) {
    StringBuilder sb = new StringBuilder("AI_ACTION");
    sb.append(" ").append(Fair2rPredicates.AI_ACTION_TYPE).append("=").append(type.name());
    if (modelId != null && !modelId.isBlank()) {
      sb.append(" ").append(Fair2rPredicates.USED_MODEL).append("=").append(modelId);
    }
    if (agentId != null && !agentId.isBlank()) {
      sb.append(" ").append(Fair2rPredicates.INVOKED_BY).append("=").append(agentId);
    }
    if (promptHash != null && !promptHash.isBlank()) {
      sb.append(" ").append(Fair2rPredicates.PROMPT_HASH).append("=").append(promptHash);
    }
    if (metadata != null) {
      for (Map.Entry<String, Object> entry : metadata.entrySet()) {
        if (entry.getValue() != null) {
          sb.append(" ").append(entry.getKey()).append("=").append(entry.getValue());
        }
      }
    }
    return sb.toString();
  }
}
