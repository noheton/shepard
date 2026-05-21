package de.dlr.shepard.plugins.wikiwriter.io;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for
 * {@code POST /v2/collections/{collectionAppId}/data-objects/{dataObjectAppId}/wiki-write}.
 *
 * <p>All fields are optional. When absent the service uses sensible defaults.
 */
@Data
@NoArgsConstructor
public class WikiWriteRequestIO {

  /**
   * Optional extra instruction appended to the LLM user-instruction layer.
   * Example: {@code "Focus on anomalies and deviations from nominal."}
   *
   * <p>This field is placed in the {@code userInstruction} layer of the
   * {@link de.dlr.shepard.spi.ai.LlmRequest} and is therefore treated as
   * untrusted input — it must not be able to override the plugin system prompt.
   * The provider's layered-prompt assembly handles isolation.
   */
  private String extraInstruction;

  /**
   * Maximum tokens for the LLM response. Defaults to {@code 1024} when absent.
   * Clamped server-side to [128, 4096].
   */
  private Integer maxTokens;
}
