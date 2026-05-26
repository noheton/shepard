package de.dlr.shepard.spi.ai;

/**
 * AI1a — reserved predicate names that will eventually mint typed
 * RDF triples on the {@code :AiActivity} provenance node, per the
 * <a href="https://github.com/noheton/f-ai-r">f(ai)²r</a>
 * vocabulary (see {@code project_fair2r_integration.md}).
 *
 * <p>These are <strong>{@link String} constants, not enforced DTO
 * field types</strong>. Decision baked in by the spawning brief:
 *
 * <ul>
 *   <li>Capturing f(ai)²r predicates as type-system-enforced fields
 *       on {@link LlmRequest} / {@link LlmResponse} would force an
 *       SPI signature break every time the vocabulary grows.</li>
 *   <li>Reserving the names here lets the provenance writer (in the
 *       plugin module) attach them to {@code :AiActivity} nodes via
 *       key-value attributes without a typed DTO churn.</li>
 *   <li>When the TPL9 (Task Provenance Layer) binding lands and
 *       we mint actual {@code fair2r:*} RDF, these constants are
 *       the single source of truth — change the URL prefix here,
 *       every emitter is updated.</li>
 * </ul>
 *
 * <p>The {@link #NAMESPACE_URI} is the canonical f(ai)²r vocabulary
 * IRI. Predicate names are short identifiers; the typical RDF
 * emitter concatenates {@code NAMESPACE_URI + name} to produce
 * full IRIs.
 *
 * <p>This class is intentionally not an {@code enum}: enum values
 * are closed; {@link String} constants leave the door open for an
 * operator to attach a one-off vocabulary term in their plugin
 * without a backend release. The set documented here is the
 * <i>recommended core</i> — emitters MAY use any other f(ai)²r
 * predicate string.
 *
 * @see #NAMESPACE_URI
 */
public final class Fair2rPredicates {

  /**
   * Canonical f(ai)²r vocabulary namespace IRI. When TPL9 (or any
   * future RDF emitter) materialises typed triples on
   * {@code :AiActivity}, predicate IRIs are formed as
   * {@code NAMESPACE_URI + <name>}.
   *
   * <p>Subject to revision when the f(ai)²r working group publishes
   * a stable PURL — the indirection through this constant is the
   * structural fix.
   */
  public static final String NAMESPACE_URI = "https://w3id.org/fair2r/v1#";

  /** The model identifier the inference was performed by. */
  public static final String USED_MODEL = "usedModel";

  /** The provider the inference was performed against. */
  public static final String USED_PROVIDER = "usedProvider";

  /**
   * SHA-256 hash of the user instruction (privacy default).
   * Replaced by the raw text when {@code storePromptText=true}
   * (per doc 86 §8 — AI Act compliance audits).
   */
  public static final String PROMPT_HASH = "promptHash";

  /**
   * Raw user-instruction text (only present when
   * {@code storePromptText=true}).
   */
  public static final String PROMPT_TEXT = "promptText";

  /** Capability slot the call was routed through. */
  public static final String CAPABILITY = "capability";

  /** Input token count reported by the provider. */
  public static final String INPUT_TOKENS = "inputTokens";

  /** Output token count reported by the provider. */
  public static final String OUTPUT_TOKENS = "outputTokens";

  /** Hash of guardrailsPrefix + guardrailsSuffix at call time. */
  public static final String GUARDRAILS_VERSION = "guardrailsVersion";

  /**
   * Whether the pre-flight injection scan flagged any
   * {@code untrustedDocuments[]} or MCP tool results.
   */
  public static final String INJECTION_FLAGGED = "injectionFlagged";

  /**
   * Whether the call's result was used to write a persisted
   * artefact (vs. ephemeral UI summary) — drives the
   * {@code aiGenerated=true} badge on downstream nodes.
   */
  public static final String RESULTED_IN_WRITE = "resultedInWrite";

  /**
   * Plugin identifier of the caller (e.g. {@code "wiki-writer"},
   * {@code "anomaly-detection"}).
   */
  public static final String INVOKED_BY = "invokedBy";

  /**
   * Human user the call was associated with (the {@code :User}'s
   * {@code appId}). Always present — autonomous calls use the
   * configured service-account user.
   */
  public static final String ASSOCIATED_USER = "associatedUser";

  /**
   * Streaming variant indicator — {@code true} when the response
   * was assembled from an SSE stream rather than a single HTTP
   * response.
   */
  public static final String WAS_STREAMED = "wasStreamed";

  /**
   * Semantic type of the AI action, drawn from
   * {@link de.dlr.shepard.v2.ai.AiActivityType}.
   * Carried in the {@code X-AI-Activity-Type} request header on inbound
   * MCP calls and stored in the {@code :Activity} provenance node via
   * TPL9 ({@code aidocs/platform/89}).
   */
  public static final String AI_ACTION_TYPE = "aiActionType";

  private Fair2rPredicates() {
    // utility class; not constructable
  }
}
