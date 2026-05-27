package de.dlr.shepard.context.collection.entities;

/**
 * Controls how conversation bodies are stored in the PromptLog substrate
 * for a given {@link Collection}.
 *
 * <p>PROMPT-h2. The mode is resolved at PromptRun write-time (PROMPT-a) by
 * reading {@link Collection#getPromptLogMode()}. New Collections default
 * to {@link #HASH_ONLY} — the most conservative posture.
 *
 * <p>Stored as a String on the {@code :Collection} Neo4j node to avoid
 * OGM enum-serialisation friction; validated at the IO layer only.
 *
 * <p>See {@code aidocs/semantics/99-promptlog-design.md §10-11} for the
 * full PII × hash coherence model (ESCALATION-PROMPT-2 resolution).
 */
public enum PromptLogMode {

  /**
   * Store only a SHA-256 hash of the prompt/response body.
   * No raw text is persisted. Safe for all deployment environments.
   * Default for new Collections.
   */
  HASH_ONLY,

  /**
   * Store the body with PII/sensitive content redacted at ingest.
   * Suitable for analytics deployments where aggregate text is needed
   * but user PII must not persist.
   */
  BODY_REDACTED,

  /**
   * Store the body as-is (no hashing, no redaction).
   * Appropriate only for air-gapped deployments or GPAI documentation
   * contexts where full EU AI Act Article 53 traceability is required.
   */
  BODY_RAW;
}
