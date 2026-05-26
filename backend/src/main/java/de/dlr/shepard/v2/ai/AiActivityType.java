package de.dlr.shepard.v2.ai;

/**
 * TPL9 — semantic classification of an AI-driven action recorded by
 * {@link AiProvenanceCapture}. Carried in the {@code X-AI-Activity-Type}
 * request header; stored in the {@code :Activity} provenance node's
 * {@code summary} via the {@code fair2r:aiActionType} predicate.
 *
 * <p>Values mirror the functional capability categories from
 * {@code aidocs/43}. The default, when the header is absent, is
 * {@link #CHAT_RESPONSE} — the least-specific assumption.
 *
 * <p>See {@code aidocs/platform/89-tpl9-fair2r-ai-provenance.md §3}
 * for the full vocabulary rationale. The predicate name constant lives
 * in {@link de.dlr.shepard.spi.ai.Fair2rPredicates#AI_ACTION_TYPE}.
 */
public enum AiActivityType {

  /**
   * The agent suggested or applied semantic annotations to one or more
   * entities (DataObjects, Collections, etc.).
   */
  ANNOTATION_SUGGESTION,

  /**
   * The agent generated or applied an import manifest, typically via
   * the {@code POST /v2/import/...} endpoints or via the MCP import tool.
   */
  IMPORT_MANIFEST_GENERATION,

  /**
   * The agent generated and executed a SPARQL query against the
   * semantic repository, typically via the N1f SPARQL proxy.
   */
  SPARQL_GENERATION,

  /**
   * The agent ran an anomaly-detection routine against one or more
   * timeseries containers (AI1b / AT1 capability family).
   */
  ANOMALY_DETECTION,

  /**
   * The agent performed semantic enrichment — vocabulary alignment,
   * ontology term suggestion, or related semantic metadata operations.
   */
  SEMANTIC_ENRICHMENT,

  /**
   * The agent produced a conversational response. This is the
   * least-specific value and the default when the caller omits the
   * {@code X-AI-Activity-Type} header. May or may not produce a
   * persisted write — see {@link de.dlr.shepard.spi.ai.Fair2rPredicates#RESULTED_IN_WRITE}.
   */
  CHAT_RESPONSE;

  /**
   * Parse a header value to an {@link AiActivityType}, returning
   * {@link #CHAT_RESPONSE} when the value is null, blank, or
   * unrecognised.
   *
   * @param value raw header string (may be {@code null})
   * @return the matching type, or {@code CHAT_RESPONSE} as safe default
   */
  public static AiActivityType fromHeader(String value) {
    if (value == null || value.isBlank()) {
      return CHAT_RESPONSE;
    }
    try {
      return AiActivityType.valueOf(value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      return CHAT_RESPONSE;
    }
  }
}
