package de.dlr.shepard.plugins.wikiwriter.services;

import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.context.labJournal.entities.LabJournalEntry;
import de.dlr.shepard.context.labJournal.services.LabJournalEntryService;
import de.dlr.shepard.plugins.wikiwriter.io.WikiWriteRequestIO;
import de.dlr.shepard.plugins.wikiwriter.io.WikiWriteResponseIO;
import de.dlr.shepard.spi.ai.AiCapability;
import de.dlr.shepard.spi.ai.LlmProvider;
import de.dlr.shepard.spi.ai.LlmRequest;
import de.dlr.shepard.spi.ai.LlmResponse;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.Map;

/**
 * WW1 — Core service for the wiki-writer plugin.
 *
 * <p>Assembles a layered LLM prompt from the target DataObject and its
 * Collection siblings, calls the {@link LlmProvider} TEXT capability,
 * and writes the result as a
 * {@link de.dlr.shepard.context.labJournal.entities.LabJournalEntry}.
 *
 * <p>The {@link LlmProvider} is injected via CDI {@link Instance} so
 * that this bean remains valid (and does not prevent startup) when
 * {@code shepard-plugin-ai} is absent from the classpath. Call sites
 * must check {@link #isAvailable()} before invoking {@link #wikiWrite}.
 */
@ApplicationScoped
public class WikiWriterService {

  /** System prompt given to the LLM. Stays in the trusted layer. */
  private static final String SYSTEM_PROMPT =
    "You are a technical documentation assistant for a research data management system. " +
    "You receive structured metadata about a research DataObject and its sibling DataObjects " +
    "in the same Collection. Your task is to write a concise, well-structured Markdown lab " +
    "journal entry for the target DataObject. " +
    "Write in a neutral, scientific tone. " +
    "Include: a brief summary of what the DataObject represents, its status, key attributes, " +
    "and how it relates to other DataObjects in the Collection (predecessors, successors, " +
    "siblings). If the DataObject has a description, incorporate it. " +
    "Do not invent data that is not present in the metadata. " +
    "Output Markdown only — no preamble, no explanation outside the entry itself.";

  /** Default max tokens when the caller does not specify. */
  private static final int DEFAULT_MAX_TOKENS = 1024;

  /** Clamped lower bound for maxTokens. */
  private static final int MIN_MAX_TOKENS = 128;

  /** Clamped upper bound for maxTokens. */
  private static final int MAX_MAX_TOKENS = 4096;

  @Inject
  Instance<LlmProvider> llmProvider;

  @Inject
  CollectionService collectionService;

  @Inject
  DataObjectService dataObjectService;

  @Inject
  LabJournalEntryService labJournalEntryService;

  /**
   * Returns {@code true} when an {@link LlmProvider} is available and the
   * TEXT capability is configured at runtime.
   */
  public boolean isAvailable() {
    if (!llmProvider.isResolvable()) return false;
    return llmProvider.get().isAvailable(AiCapability.TEXT);
  }

  /**
   * Generates a Markdown summary of the target DataObject and writes it as
   * a {@link LabJournalEntry} on that DataObject.
   *
   * @param collectionOgmId the Neo4j OGM id of the parent Collection
   * @param dataObjectOgmId the Neo4j OGM id of the target DataObject
   * @param request         optional caller overrides (extraInstruction, maxTokens)
   * @return a populated {@link WikiWriteResponseIO} with entry id + provenance
   * @throws LlmException if the LLM call fails or returns a guardrail rejection
   */
  public WikiWriteResponseIO wikiWrite(
    long collectionOgmId,
    long dataObjectOgmId,
    WikiWriteRequestIO request
  ) {
    // Load the full collection (with DataObjects) for context.
    Collection collection = collectionService.getCollectionWithDataObjectsAndIncomingReferences(collectionOgmId);

    // Find the target DataObject.
    DataObject target = dataObjectService.getDataObject(collectionOgmId, dataObjectOgmId);

    // Build the trusted context from the Collection and DataObject metadata.
    String trustedContext = buildTrustedContext(collection, target);

    // Clamp and default maxTokens.
    int maxTokens = DEFAULT_MAX_TOKENS;
    if (request != null && request.getMaxTokens() != null) {
      maxTokens = Math.min(Math.max(request.getMaxTokens(), MIN_MAX_TOKENS), MAX_MAX_TOKENS);
    }

    // Build the user instruction layer.
    String userInstruction = buildUserInstruction(target, request);

    // Assemble the LlmRequest.
    LlmRequest llmRequest = LlmRequest.builder(AiCapability.TEXT)
      .pluginSystemPrompt(SYSTEM_PROMPT)
      .trustedContext(trustedContext)
      .userInstruction(userInstruction)
      .maxTokens(maxTokens)
      .temperature(0.3)
      .build();

    // Call the provider.
    LlmResponse llmResponse = llmProvider.get().complete(llmRequest);
    String generatedSummary = llmResponse.text();

    Log.debugf(
      "WW1: generated summary for DataObject %d (%d input tokens, %d output tokens, activity=%s)",
      dataObjectOgmId,
      llmResponse.inputTokens(),
      llmResponse.outputTokens(),
      llmResponse.activityAppId()
    );

    // Write the LabJournalEntry.
    LabJournalEntry entry = labJournalEntryService.createLabJournalEntry(dataObjectOgmId, generatedSummary);

    return new WikiWriteResponseIO(
      entry.getId(),
      generatedSummary,
      llmResponse.activityAppId(),
      llmResponse.inputTokens(),
      llmResponse.outputTokens()
    );
  }

  // ── Private helpers ────────────────────────────────────────────────────────

  /**
   * Assembles the trusted context string from Collection + DataObject metadata.
   * All values come from the database — no untrusted user input here.
   */
  private String buildTrustedContext(Collection collection, DataObject target) {
    StringBuilder sb = new StringBuilder();

    // Collection context.
    sb.append("## Collection\n");
    sb.append("- Name: ").append(safe(collection.getName())).append("\n");
    if (collection.getDescription() != null && !collection.getDescription().isBlank()) {
      sb.append("- Description: ").append(collection.getDescription()).append("\n");
    }
    sb.append("- Status: ").append(safe(collection.getStatus())).append("\n");
    sb.append("\n");

    // Target DataObject.
    sb.append("## Target DataObject\n");
    sb.append("- Name: ").append(safe(target.getName())).append("\n");
    sb.append("- AppId: ").append(safe(target.getAppId())).append("\n");
    sb.append("- Status: ").append(safe(target.getStatus())).append("\n");
    if (target.getDescription() != null && !target.getDescription().isBlank()) {
      sb.append("- Description: ").append(target.getDescription()).append("\n");
    }
    if (target.getAttributes() != null && !target.getAttributes().isEmpty()) {
      sb.append("- Attributes:\n");
      for (Map.Entry<String, String> attr : target.getAttributes().entrySet()) {
        sb.append("  - ").append(attr.getKey()).append(": ").append(attr.getValue()).append("\n");
      }
    }
    sb.append("\n");

    // Siblings (other DataObjects in the Collection, excluding target).
    if (collection.getDataObjects() != null && !collection.getDataObjects().isEmpty()) {
      sb.append("## Sibling DataObjects in Collection\n");
      for (DataObject sibling : collection.getDataObjects()) {
        if (sibling.getId() != null && sibling.getId().equals(target.getId())) continue;
        if (sibling.isDeleted()) continue;
        sb.append("- ").append(safe(sibling.getName()))
          .append(" (status: ").append(safe(sibling.getStatus())).append(")");
        if (sibling.getDescription() != null && !sibling.getDescription().isBlank()) {
          // Keep sibling descriptions brief.
          String desc = sibling.getDescription();
          if (desc.length() > 120) desc = desc.substring(0, 120) + "…";
          sb.append(" — ").append(desc);
        }
        sb.append("\n");
      }
      sb.append("\n");
    }

    // Predecessors.
    if (target.getPredecessors() != null && !target.getPredecessors().isEmpty()) {
      sb.append("## Predecessors\n");
      for (DataObject pred : target.getPredecessors()) {
        if (!pred.isDeleted()) {
          sb.append("- ").append(safe(pred.getName()))
            .append(" (status: ").append(safe(pred.getStatus())).append(")\n");
        }
      }
      sb.append("\n");
    }

    // Successors.
    if (target.getSuccessors() != null && !target.getSuccessors().isEmpty()) {
      sb.append("## Successors\n");
      for (DataObject succ : target.getSuccessors()) {
        if (!succ.isDeleted()) {
          sb.append("- ").append(safe(succ.getName()))
            .append(" (status: ").append(safe(succ.getStatus())).append(")\n");
        }
      }
      sb.append("\n");
    }

    return sb.toString();
  }

  /**
   * Builds the user instruction layer. May include the caller's optional
   * {@code extraInstruction}. This is the untrusted layer — it is placed
   * after the trusted context in the layered prompt assembly.
   */
  private String buildUserInstruction(DataObject target, WikiWriteRequestIO request) {
    String base = "Write a Markdown lab journal entry for DataObject \"" + safe(target.getName()) + "\". " +
      "Use the metadata provided above. Keep the entry concise and factual.";
    if (request != null && request.getExtraInstruction() != null && !request.getExtraInstruction().isBlank()) {
      base = base + " " + request.getExtraInstruction().strip();
    }
    return base;
  }

  private static String safe(String value) {
    return value != null ? value : "(unknown)";
  }
}
