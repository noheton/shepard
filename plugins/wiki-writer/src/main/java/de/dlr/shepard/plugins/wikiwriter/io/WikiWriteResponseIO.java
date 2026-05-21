package de.dlr.shepard.plugins.wikiwriter.io;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response body for
 * {@code POST /v2/collections/{collectionAppId}/data-objects/{dataObjectAppId}/wiki-write}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WikiWriteResponseIO {

  /**
   * The Neo4j OGM id of the {@code LabJournalEntry} that was created.
   * Clients can use this with the upstream
   * {@code GET /shepard/api/.../labJournalEntries/{id}} endpoint.
   */
  private long labJournalEntryId;

  /**
   * The generated Markdown summary that was written as the journal entry content.
   */
  private String generatedSummary;

  /**
   * The {@code appId} of the {@code :AiActivity} provenance node written by
   * the LLM provider, as returned in
   * {@link de.dlr.shepard.spi.ai.LlmResponse#activityAppId()}.
   * May be blank if the provider did not emit a provenance node.
   */
  private String activityAppId;

  /**
   * Number of input tokens consumed by the LLM call.
   */
  private int inputTokens;

  /**
   * Number of output tokens produced by the LLM call.
   */
  private int outputTokens;
}
