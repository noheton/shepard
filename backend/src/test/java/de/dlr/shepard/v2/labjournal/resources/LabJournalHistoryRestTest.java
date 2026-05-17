package de.dlr.shepard.v2.labjournal.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.labJournal.daos.LabJournalEntryDAO;
import de.dlr.shepard.context.labJournal.daos.LabJournalEntryRevisionDAO;
import de.dlr.shepard.context.labJournal.entities.LabJournalEntry;
import de.dlr.shepard.context.labJournal.entities.LabJournalEntryRevision;
import de.dlr.shepard.v2.labjournal.io.LabJournalRevisionIO;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * J1d — Mockito unit tests for {@link LabJournalHistoryRest}.
 *
 * <p>No CDI container or network required — fields are injected directly.
 */
@SuppressWarnings("unchecked")
class LabJournalHistoryRestTest {

  static final String ENTRY_APP_ID = "01957000-0000-7000-8000-000000000010";
  static final long DO_OGM_ID = 55L;
  static final long ENTRY_OGM_ID = 101L;
  static final String CALLER = "bob";

  @Mock
  LabJournalEntryDAO labJournalEntryDAO;

  @Mock
  LabJournalEntryRevisionDAO labJournalEntryRevisionDAO;

  @Mock
  PermissionsService permissionsService;

  @Mock
  SecurityContext sc;

  @Mock
  Principal principal;

  LabJournalHistoryRest resource;

  DataObject dataObject;
  LabJournalEntry entry;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);

    resource = new LabJournalHistoryRest();
    resource.labJournalEntryDAO = labJournalEntryDAO;
    resource.labJournalEntryRevisionDAO = labJournalEntryRevisionDAO;
    resource.permissionsService = permissionsService;

    dataObject = new DataObject();
    dataObject.setId(DO_OGM_ID);

    entry = new LabJournalEntry();
    entry.setId(ENTRY_OGM_ID);
    entry.setAppId(ENTRY_APP_ID);
    entry.setContent("current content");
    entry.setDataObject(dataObject);

    when(sc.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
    when(labJournalEntryDAO.findByAppId(ENTRY_APP_ID)).thenReturn(entry);
    when(permissionsService.isAccessTypeAllowedForUser(DO_OGM_ID, AccessType.Read, CALLER, 0L)).thenReturn(true);
    when(labJournalEntryRevisionDAO.findByEntry(ENTRY_OGM_ID)).thenReturn(List.of());
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  private LabJournalEntryRevision makeRevision(String appId, String content, int num) {
    User user = new User("bob");

    LabJournalEntryRevision rev = new LabJournalEntryRevision();
    rev.setAppId(appId);
    rev.setContent(content);
    rev.setRevisionNumber(num);
    rev.setCreatedAt(new Date());
    rev.setCreatedBy(user);
    rev.setLabJournalEntry(entry);
    return rev;
  }

  // ── happy-path tests ─────────────────────────────────────────────────────

  @Test
  void history_returns200WithRevisions_whenSeveralEditsExist() {
    // Revisions already ordered newest first by the DAO
    var rev2 = makeRevision("rev-app-id-2", "version 2 content", 2);
    var rev1 = makeRevision("rev-app-id-1", "original content", 1);
    when(labJournalEntryRevisionDAO.findByEntry(ENTRY_OGM_ID)).thenReturn(List.of(rev2, rev1));

    Response r = resource.history(ENTRY_APP_ID, sc);

    assertThat(r.getStatus()).isEqualTo(200);
    List<LabJournalRevisionIO> body = (List<LabJournalRevisionIO>) r.getEntity();
    assertThat(body).hasSize(2);
    // Newest first: revision 2 at index 0
    assertThat(body.get(0).getRevisionNumber()).isEqualTo(2);
    assertThat(body.get(0).getContent()).isEqualTo("version 2 content");
    assertThat(body.get(0).getAppId()).isEqualTo("rev-app-id-2");
    // Older revision at index 1
    assertThat(body.get(1).getRevisionNumber()).isEqualTo(1);
    assertThat(body.get(1).getContent()).isEqualTo("original content");
  }

  @Test
  void history_returns200WithEmptyList_whenEntryNeverEdited() {
    // DAO already returns empty list from setUp
    Response r = resource.history(ENTRY_APP_ID, sc);

    assertThat(r.getStatus()).isEqualTo(200);
    List<LabJournalRevisionIO> body = (List<LabJournalRevisionIO>) r.getEntity();
    assertThat(body).isEmpty();
  }

  // ── auth / not-found tests ────────────────────────────────────────────────

  @Test
  void history_returns401_whenUnauthenticated() {
    when(sc.getUserPrincipal()).thenReturn(null);

    assertThat(resource.history(ENTRY_APP_ID, sc).getStatus()).isEqualTo(401);
  }

  @Test
  void history_returns403_whenCallerLacksReadPermission() {
    when(permissionsService.isAccessTypeAllowedForUser(DO_OGM_ID, AccessType.Read, CALLER, 0L)).thenReturn(false);

    assertThat(resource.history(ENTRY_APP_ID, sc).getStatus()).isEqualTo(403);
  }

  @Test
  void history_returns404_whenEntryNotFound() {
    when(labJournalEntryDAO.findByAppId("unknown-app-id")).thenReturn(null);

    assertThat(resource.history("unknown-app-id", sc).getStatus()).isEqualTo(404);
  }

  @Test
  void history_returns404_whenEntryIsDeleted() {
    entry.setDeleted(true);

    assertThat(resource.history(ENTRY_APP_ID, sc).getStatus()).isEqualTo(404);
  }

  @Test
  void history_returns404_whenDataObjectIsNull() {
    entry.setDataObject(null);

    assertThat(resource.history(ENTRY_APP_ID, sc).getStatus()).isEqualTo(404);
  }

  // ── IO mapping ────────────────────────────────────────────────────────────

  @Test
  void history_revisionIO_mapsFieldsCorrectly() {
    var rev = makeRevision("rev-app-42", "old text", 1);
    when(labJournalEntryRevisionDAO.findByEntry(ENTRY_OGM_ID)).thenReturn(List.of(rev));

    Response r = resource.history(ENTRY_APP_ID, sc);
    List<LabJournalRevisionIO> body = (List<LabJournalRevisionIO>) r.getEntity();

    assertThat(body).hasSize(1);
    LabJournalRevisionIO io = body.get(0);
    assertThat(io.getAppId()).isEqualTo("rev-app-42");
    assertThat(io.getContent()).isEqualTo("old text");
    assertThat(io.getRevisionNumber()).isEqualTo(1);
    assertThat(io.getRevisedAt()).isNotNull();
    // revisedBy should come from the user's username (DisplayNameResolver redacts partial)
    assertThat(io.getRevisedBy()).isNotNull();
  }
}
