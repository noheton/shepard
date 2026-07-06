package de.dlr.shepard.plugins.wikiwriter.io;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response body for
 * {@code POST /v2/data-objects/{dataObjectAppId}/wiki-write}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WikiWriteResponseIO {

  /**
   * The Neo4j OGM id of the {@code LabJournalEntry} that was created.
   * Clients can use this with the upstream
   * {@code GET /shepard/api/.../labJournalEntries/{id}} endpoint.
   *
   * @deprecated Use {@link #labJournalEntryAppId} (UUID v7 stable identifier).
   *             This field will be removed in the L2e deprecation window.
   */
  @Deprecated
  @JsonIgnore
  private long labJournalEntryId;

  /**
   * The {@code appId} (UUID v7) of the {@code LabJournalEntry} that was created.
   * Use {@code GET /v2/data-objects/{dataObjectAppId}} and inspect the lab journal
   * entries panel, or reference this appId in semantic annotations.
   */
  private String labJournalEntryAppId;

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
