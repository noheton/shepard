package de.dlr.shepard.v2.mcp;

import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.context.labJournal.entities.LabJournalEntry;
import de.dlr.shepard.context.labJournal.services.LabJournalEntryService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP-COV-06 — Lab journal MCP tools.
 *
 * <p>Thin wrappers over {@link LabJournalEntryService} so an MCP caller can
 * list, create, update, and delete {@link LabJournalEntry} rows attached to
 * a DataObject without the 5-tuple-style friction of the REST path. The
 * service layer is the canonical authority for permission gating
 * (collection-level Read/Edit checks + creator-only update/delete via
 * {@link LabJournalEntryService#assertIsCreator}); these tools delegate
 * straight through.
 *
 * <p>Shape mismatch with the task spec: a {@link LabJournalEntry} has only
 * a {@code content} field — there is no {@code title}/{@code body} split.
 * The MCP tool follows the entity, not the aspirational spec.
 *
 * <p>Error mapping is inherited from {@link McpToolSupport#run}:
 * {@code NotFoundException} → -32602 INVALID_PARAMS (caller-fixable),
 * {@code ForbiddenException} / {@code InvalidAuthException} → -32002
 * FORBIDDEN.
 */
@ApplicationScoped
public class LabJournalMcpTools {

  @Inject
  LabJournalEntryService labJournalEntryService;

  @Inject
  EntityIdResolver entityIdResolver;

  @Inject
  McpContextBridge contextBridge;

  @Inject
  McpToolSupport support;

  // ─── lab_journal_list ───────────────────────────────────────────────────────

  @Tool(
    name = "lab_journal_list",
    description =
      "List every non-deleted LabJournalEntry attached to the given DataObject, " +
      "newest-first.\n\n" +
      "Parameters:\n" +
      "  dataObjectAppId — UUID v7 of the owning DataObject.\n\n" +
      "Returns a JSON array. Each row:\n" +
      "  appId      — UUID v7 of the entry (use this for lab_journal_update / _delete).\n" +
      "  id         — legacy numeric id.\n" +
      "  content    — entry body (free-text, sanitised HTML).\n" +
      "  createdAt, createdBy.\n" +
      "  updatedAt  — null until the entry has been edited.\n\n" +
      "Auth: Read on the owning Collection. Forbidden → -32002."
  )
  public String labJournalList(
    @ToolArg(description = "UUID v7 of the owning DataObject (from `list_data_objects` or `get_data_object`).") String dataObjectAppId
  ) {
    return support.run("lab_journal_list", () -> {
      contextBridge.bind();
      if (dataObjectAppId == null || dataObjectAppId.isBlank()) {
        throw McpToolSupport.invalidParams("dataObjectAppId is required.");
      }
      long ogmId;
      try {
        ogmId = entityIdResolver.resolveLong(dataObjectAppId);
      } catch (NotFoundException nfe) {
        throw McpToolSupport.invalidParams("No DataObject with appId " + dataObjectAppId);
      }
      List<LabJournalEntry> entries = labJournalEntryService.getLabJournalEntriesByDataObjectId(ogmId);
      List<Map<String, Object>> result = new ArrayList<>(entries.size());
      for (LabJournalEntry e : entries) {
        result.add(toRow(e));
      }
      return support.toJson(result);
    });
  }

  // ─── lab_journal_create ─────────────────────────────────────────────────────

  @Tool(
    name = "lab_journal_create",
    description =
      "Create a new LabJournalEntry on a DataObject.\n\n" +
      "Parameters:\n" +
      "  dataObjectAppId — UUID v7 of the owning DataObject.\n" +
      "  content         — entry body (free-text; HTML allowed, sanitised by " +
      "                    the service layer).\n\n" +
      "Note: the underlying entity carries a single `content` field; there is " +
      "no separate title/body split.\n\n" +
      "Auth: Edit on the owning Collection. Forbidden → -32002. The entry's " +
      "createdBy is set to the calling principal."
  )
  public String labJournalCreate(
    @ToolArg(description = "UUID v7 of the owning DataObject.") String dataObjectAppId,
    @ToolArg(description = "Entry content (free-text; HTML allowed).") String content
  ) {
    return support.run("lab_journal_create", () -> {
      contextBridge.bind();
      if (dataObjectAppId == null || dataObjectAppId.isBlank()) {
        throw McpToolSupport.invalidParams("dataObjectAppId is required.");
      }
      if (content == null) {
        throw McpToolSupport.invalidParams("content is required.");
      }
      long ogmId;
      try {
        ogmId = entityIdResolver.resolveLong(dataObjectAppId);
      } catch (NotFoundException nfe) {
        throw McpToolSupport.invalidParams("No DataObject with appId " + dataObjectAppId);
      }
      LabJournalEntry created = labJournalEntryService.createLabJournalEntry(ogmId, content);
      return support.toJson(toRow(created));
    });
  }

  // ─── lab_journal_update ─────────────────────────────────────────────────────

  @Tool(
    name = "lab_journal_update",
    description =
      "Update the content of an existing LabJournalEntry.\n\n" +
      "Parameters:\n" +
      "  entryAppId — UUID v7 of the entry to update (from `lab_journal_list`).\n" +
      "  content    — new content (overwrites the previous body).\n\n" +
      "The prior content is preserved as an append-only revision (J1d) so the " +
      "history is queryable via the lab journal revision REST endpoints.\n\n" +
      "Auth: Edit on the owning Collection. Only the entry's creator may update " +
      "it (J1d creator-only rule); non-creators get -32002."
  )
  public String labJournalUpdate(
    @ToolArg(description = "UUID v7 of the entry to update.") String entryAppId,
    @ToolArg(description = "Replacement content (overwrites the prior body; previous body is kept as a revision).") String content
  ) {
    return support.run("lab_journal_update", () -> {
      contextBridge.bind();
      if (entryAppId == null || entryAppId.isBlank()) {
        throw McpToolSupport.invalidParams("entryAppId is required.");
      }
      if (content == null) {
        throw McpToolSupport.invalidParams("content is required.");
      }
      long ogmId = support.resolveOfType(entryAppId, "LabJournalEntry", "entryAppId");
      // Creator-only check matches the REST PATCH path (LabJournalEntryRest).
      LabJournalEntry existing = labJournalEntryService.getLabJournalEntry(ogmId);
      if (existing == null) {
        throw McpToolSupport.invalidParams("No LabJournalEntry with appId " + entryAppId);
      }
      labJournalEntryService.assertIsCreator(existing);
      LabJournalEntry updated = labJournalEntryService.updateLabJournalEntry(ogmId, content);
      if (updated == null) {
        throw McpToolSupport.invalidParams("No LabJournalEntry with appId " + entryAppId);
      }
      return support.toJson(toRow(updated));
    });
  }

  // ─── lab_journal_delete ─────────────────────────────────────────────────────

  @Tool(
    name = "lab_journal_delete",
    description =
      "Soft-delete a LabJournalEntry (the entry is hidden from lists but its " +
      "history is preserved for audit).\n\n" +
      "Parameters:\n" +
      "  entryAppId — UUID v7 of the entry to delete.\n\n" +
      "Returns: {deleted: true, appId}.\n\n" +
      "Auth: Edit on the owning Collection. The REST layer enforces a " +
      "creator-only rule via {@code assertIsCreator}; non-creators get -32002."
  )
  public String labJournalDelete(
    @ToolArg(description = "UUID v7 of the entry to delete.") String entryAppId
  ) {
    return support.run("lab_journal_delete", () -> {
      contextBridge.bind();
      if (entryAppId == null || entryAppId.isBlank()) {
        throw McpToolSupport.invalidParams("entryAppId is required.");
      }
      long ogmId = support.resolveOfType(entryAppId, "LabJournalEntry", "entryAppId");
      LabJournalEntry existing = labJournalEntryService.getLabJournalEntry(ogmId);
      if (existing == null) {
        throw McpToolSupport.invalidParams("No LabJournalEntry with appId " + entryAppId);
      }
      labJournalEntryService.assertIsCreator(existing);
      boolean ok = labJournalEntryService.deleteLabJournalEntry(ogmId);
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("deleted", ok);
      body.put("appId", entryAppId);
      return support.toJson(body);
    });
  }

  // ─── helpers ────────────────────────────────────────────────────────────────

  private static Map<String, Object> toRow(LabJournalEntry e) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("appId", e.getAppId());
    row.put("id", e.getId());
    row.put("content", e.getContent());
    row.put("createdAt", e.getCreatedAt());
    row.put(
      "createdBy",
      e.getCreatedBy() == null ? null : e.getCreatedBy().getUsername()
    );
    row.put("updatedAt", e.getUpdatedAt());
    return row;
  }
}
