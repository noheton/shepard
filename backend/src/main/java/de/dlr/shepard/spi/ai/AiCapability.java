package de.dlr.shepard.spi.ai;

/**
 * Capability slots that a configured LLM provider may fulfil.
 * Plugins declare their dependencies via {@link RequiresAiCapability}.
 *
 * <p>Each slot maps to one `:AiCapabilityConfig` Neo4j singleton that an
 * instance-admin configures via {@code GET/PATCH /v2/admin/ai/capabilities/{capability}}.
 */
public enum AiCapability {

  /** General text completion / chat — the workhorse capability. */
  TEXT,

  /** Like TEXT but optimised for speed over quality (small/fast model). */
  FAST_TEXT,

  /** Image generation (DALL-E style). */
  IMAGE_GEN,

  /** Vision — image + text → text (multimodal). */
  VISION,

  /** Sentence/passage embeddings for semantic search. */
  EMBEDDING,

  /** Structured output / JSON mode (e.g. function-calling models). */
  STRUCTURED,
}
