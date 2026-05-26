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

  /**
   * Speech / audio-to-text — meeting recordings, lab-journal voice
   * notes, video soundtracks. The slot can map to OpenAI Whisper,
   * locally-hosted faster-whisper, or any compliant provider.
   */
  TRANSCRIPTION,

  /**
   * Safety / toxicity classification — a lightweight pre-filter for
   * user-generated content that may flow into prompts. Returns
   * category scores, not narrative text.
   *
   * <p>Optional capability; admins can leave this slot unconfigured
   * if the institution's compliance posture doesn't require it.
   */
  MODERATION,
}
