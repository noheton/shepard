package de.dlr.shepard.v2.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.context.labJournal.entities.LabJournalEntry;
import de.dlr.shepard.context.labJournal.services.LabJournalEntryService;
import io.quarkiverse.mcp.server.McpException;
import jakarta.ws.rs.NotFoundException;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * MCP-COV-06 — unit tests for {@link LabJournalMcpTools}.
 *
 * <p>Per-tool: happy path + auth/perm failure + bad arg, totalling
 * a healthy mix of branches. Service-layer assertions (creator check,
 * collection-permission check) are mock-driven.
 */
class LabJournalMcpToolsTest {

  static final String DO_APP_ID    = "018f9c5a-7e26-7000-d000-000000000001";
  static final long   DO_OGM_ID    = 42L;
  static final String ENTRY_APP_ID = "018f9c5a-7e26-7000-d000-000000000010";
  static final long   ENTRY_OGM_ID = 99L;

  @Mock LabJournalEntryService labJournalEntryService;
  @Mock EntityIdResolver entityIdResolver;
  @Mock McpContextBridge contextBridge;

  LabJournalMcpTools tools;
  McpToolSupport support;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    support = new McpToolSupport();
    support.entityIdResolver = entityIdResolver;
    support.objectMapper = new ObjectMapper();

    tools = new LabJournalMcpTools();
    tools.labJournalEntryService = labJournalEntryService;
    tools.entityIdResolver = entityIdResolver;
    tools.contextBridge = contextBridge;
    tools.support = support;
  }

  private LabJournalEntry entry(String appId, long id, String content, String username) {
    LabJournalEntry e = new LabJournalEntry();
    e.setAppId(appId);
    e.setId(id);
    e.setContent(content);
    e.setCreatedAt(new Date(1700000000_000L));
    if (username != null) {
      User u = new User();
      u.setUsername(username);
      e.setCreatedBy(u);
    }
    return e;
  }

  // ── lab_journal_list ─────────────────────────────────────────────────────

  @Test
  void listReturnsEntriesForDataObject() throws Exception {
    when(entityIdResolver.resolveLong(DO_APP_ID)).thenReturn(DO_OGM_ID);
    when(labJournalEntryService.getLabJournalEntriesByDataObjectId(DO_OGM_ID))
      .thenReturn(List.of(
        entry(ENTRY_APP_ID, ENTRY_OGM_ID, "calibration ok", "alice"),
        entry("018f9c5a-7e26-7000-d000-000000000011", 100L, "noted", "bob")
      ));

    String json = tools.labJournalList(DO_APP_ID);

    JsonNode arr = new ObjectMapper().readTree(json);
    assertTrue(arr.isArray());
    assertEquals(2, arr.size());
    assertEquals(ENTRY_APP_ID, arr.get(0).get("appId").asText());
    assertEquals("calibration ok", arr.get(0).get("content").asText());
    assertEquals("alice", arr.get(0).get("createdBy").asText());
  }

  @Test
  void listRejectsBlankDataObjectAppId() {
    McpException ex = assertThrows(McpException.class, () -> tools.labJournalList(""));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  @Test
  void listMapsUnknownDataObjectToInvalidParams() {
    when(entityIdResolver.resolveLong(DO_APP_ID))
      .thenThrow(new NotFoundException("nope"));
    McpException ex = assertThrows(McpException.class, () -> tools.labJournalList(DO_APP_ID));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
    assertTrue(ex.getMessage().contains(DO_APP_ID));
  }

  // ── lab_journal_create ───────────────────────────────────────────────────

  @Test
  void createReturnsPersistedEntry() throws Exception {
    when(entityIdResolver.resolveLong(DO_APP_ID)).thenReturn(DO_OGM_ID);
    LabJournalEntry created = entry(ENTRY_APP_ID, ENTRY_OGM_ID, "test fired", "alice");
    when(labJournalEntryService.createLabJournalEntry(DO_OGM_ID, "test fired"))
      .thenReturn(created);

    String json = tools.labJournalCreate(DO_APP_ID, "test fired");
    JsonNode row = new ObjectMapper().readTree(json);
    assertEquals(ENTRY_APP_ID, row.get("appId").asText());
    assertEquals("test fired", row.get("content").asText());
    verify(labJournalEntryService).createLabJournalEntry(DO_OGM_ID, "test fired");
  }

  @Test
  void createRejectsNullContent() {
    McpException ex = assertThrows(McpException.class,
      () -> tools.labJournalCreate(DO_APP_ID, null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  @Test
  void createPropagatesForbiddenAsCustomError() {
    when(entityIdResolver.resolveLong(DO_APP_ID)).thenReturn(DO_OGM_ID);
    when(labJournalEntryService.createLabJournalEntry(eq(DO_OGM_ID), any()))
      .thenThrow(new jakarta.ws.rs.ForbiddenException("no edit perm"));
    McpException ex = assertThrows(McpException.class,
      () -> tools.labJournalCreate(DO_APP_ID, "x"));
    assertEquals(-32002, ex.getJsonRpcErrorCode());
  }

  // ── lab_journal_update ───────────────────────────────────────────────────

  @Test
  void updateReturnsUpdatedEntry() throws Exception {
    when(entityIdResolver.resolveWithLabels(ENTRY_APP_ID))
      .thenReturn(new EntityIdResolver.LabeledResolution(ENTRY_OGM_ID, List.of("LabJournalEntry")));
    LabJournalEntry existing = entry(ENTRY_APP_ID, ENTRY_OGM_ID, "old", "alice");
    when(labJournalEntryService.getLabJournalEntry(ENTRY_OGM_ID)).thenReturn(existing);
    LabJournalEntry updated = entry(ENTRY_APP_ID, ENTRY_OGM_ID, "new", "alice");
    updated.setUpdatedAt(new Date(1700000001_000L));
    when(labJournalEntryService.updateLabJournalEntry(ENTRY_OGM_ID, "new")).thenReturn(updated);

    String json = tools.labJournalUpdate(ENTRY_APP_ID, "new");
    JsonNode row = new ObjectMapper().readTree(json);
    assertEquals("new", row.get("content").asText());
    assertTrue(row.has("updatedAt"));
  }

  @Test
  void updateBlocksNonCreator() {
    when(entityIdResolver.resolveWithLabels(ENTRY_APP_ID))
      .thenReturn(new EntityIdResolver.LabeledResolution(ENTRY_OGM_ID, List.of("LabJournalEntry")));
    LabJournalEntry existing = entry(ENTRY_APP_ID, ENTRY_OGM_ID, "old", "alice");
    when(labJournalEntryService.getLabJournalEntry(ENTRY_OGM_ID)).thenReturn(existing);
    doThrow(new InvalidAuthException("not creator"))
      .when(labJournalEntryService).assertIsCreator(existing);

    McpException ex = assertThrows(McpException.class,
      () -> tools.labJournalUpdate(ENTRY_APP_ID, "new"));
    assertEquals(-32002, ex.getJsonRpcErrorCode());
  }

  @Test
  void updateMapsWrongEntityTypeToInvalidParams() {
    when(entityIdResolver.resolveWithLabels(ENTRY_APP_ID))
      .thenReturn(new EntityIdResolver.LabeledResolution(ENTRY_OGM_ID, List.of("Collection")));
    McpException ex = assertThrows(McpException.class,
      () -> tools.labJournalUpdate(ENTRY_APP_ID, "new"));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  // ── lab_journal_delete ───────────────────────────────────────────────────

  @Test
  void deleteReturnsConfirmation() throws Exception {
    when(entityIdResolver.resolveWithLabels(ENTRY_APP_ID))
      .thenReturn(new EntityIdResolver.LabeledResolution(ENTRY_OGM_ID, List.of("LabJournalEntry")));
    LabJournalEntry existing = entry(ENTRY_APP_ID, ENTRY_OGM_ID, "old", "alice");
    when(labJournalEntryService.getLabJournalEntry(ENTRY_OGM_ID)).thenReturn(existing);
    when(labJournalEntryService.deleteLabJournalEntry(ENTRY_OGM_ID)).thenReturn(true);

    String json = tools.labJournalDelete(ENTRY_APP_ID);
    JsonNode row = new ObjectMapper().readTree(json);
    assertTrue(row.get("deleted").asBoolean());
    assertEquals(ENTRY_APP_ID, row.get("appId").asText());
  }

  @Test
  void deleteRejectsBlankAppId() {
    McpException ex = assertThrows(McpException.class, () -> tools.labJournalDelete(""));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }
}
