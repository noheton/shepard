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
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import de.dlr.shepard.v2.labjournal.io.LabJournalRevisionIO;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.Date;
import java.util.List;
import java.util.stream.IntStream;
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
    when(permissionsService.isAccessTypeAllowedForUser(DO_OGM_ID, AccessType.Read, CALLER)).thenReturn(true);
    // Default: entry has no revisions
    when(labJournalEntryRevisionDAO.countByEntry(ENTRY_OGM_ID)).thenReturn(0L);
    when(labJournalEntryRevisionDAO.findByEntry(ENTRY_OGM_ID, 0, 50)).thenReturn(List.of());
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

  @SuppressWarnings("unchecked")
  private static PagedResponseIO<LabJournalRevisionIO> body(Response r) {
    return (PagedResponseIO<LabJournalRevisionIO>) r.getEntity();
  }

  // ── happy-path tests ─────────────────────────────────────────────────────

  @Test
  void history_returns200WithRevisions_whenSeveralEditsExist() {
    var rev2 = makeRevision("rev-app-id-2", "version 2 content", 2);
    var rev1 = makeRevision("rev-app-id-1", "original content", 1);
    when(labJournalEntryRevisionDAO.countByEntry(ENTRY_OGM_ID)).thenReturn(2L);
    when(labJournalEntryRevisionDAO.findByEntry(ENTRY_OGM_ID, 0, 50)).thenReturn(List.of(rev2, rev1));

    Response r = resource.history(ENTRY_APP_ID, 0, 50, sc);

    assertThat(r.getStatus()).isEqualTo(200);
    var b = body(r);
    assertThat(b.total()).isEqualTo(2);
    assertThat(b.items()).hasSize(2);
    assertThat(b.items().get(0).getRevisionNumber()).isEqualTo(2);
    assertThat(b.items().get(0).getContent()).isEqualTo("version 2 content");
    assertThat(b.items().get(0).getAppId()).isEqualTo("rev-app-id-2");
    assertThat(b.items().get(1).getRevisionNumber()).isEqualTo(1);
    assertThat(b.items().get(1).getContent()).isEqualTo("original content");
  }

  @Test
  void history_returns200WithEmptyItems_whenEntryNeverEdited() {
    Response r = resource.history(ENTRY_APP_ID, 0, 50, sc);

    assertThat(r.getStatus()).isEqualTo(200);
    var b = body(r);
    assertThat(b.items()).isEmpty();
    assertThat(b.total()).isEqualTo(0);
  }

  // ── auth / not-found tests ────────────────────────────────────────────────

  @Test
  void history_returns401_whenUnauthenticated() {
    when(sc.getUserPrincipal()).thenReturn(null);

    assertThat(resource.history(ENTRY_APP_ID, 0, 50, sc).getStatus()).isEqualTo(401);
  }

  @Test
  void history_returns403_whenCallerLacksReadPermission() {
    when(permissionsService.isAccessTypeAllowedForUser(DO_OGM_ID, AccessType.Read, CALLER)).thenReturn(false);

    assertThat(resource.history(ENTRY_APP_ID, 0, 50, sc).getStatus()).isEqualTo(403);
  }

  @Test
  void history_returns404_whenEntryNotFound() {
    when(labJournalEntryDAO.findByAppId("unknown-app-id")).thenReturn(null);

    assertThat(resource.history("unknown-app-id", 0, 50, sc).getStatus()).isEqualTo(404);
  }

  @Test
  void history_returns404_whenEntryIsDeleted() {
    entry.setDeleted(true);

    assertThat(resource.history(ENTRY_APP_ID, 0, 50, sc).getStatus()).isEqualTo(404);
  }

  @Test
  void history_returns404_whenDataObjectIsNull() {
    entry.setDataObject(null);

    assertThat(resource.history(ENTRY_APP_ID, 0, 50, sc).getStatus()).isEqualTo(404);
  }

  // ── IO mapping ────────────────────────────────────────────────────────────

  @Test
  void history_revisionIO_mapsFieldsCorrectly() {
    var rev = makeRevision("rev-app-42", "old text", 1);
    when(labJournalEntryRevisionDAO.countByEntry(ENTRY_OGM_ID)).thenReturn(1L);
    when(labJournalEntryRevisionDAO.findByEntry(ENTRY_OGM_ID, 0, 50)).thenReturn(List.of(rev));

    Response r = resource.history(ENTRY_APP_ID, 0, 50, sc);
    var b = body(r);

    assertThat(b.items()).hasSize(1);
    LabJournalRevisionIO io = b.items().get(0);
    assertThat(io.getAppId()).isEqualTo("rev-app-42");
    assertThat(io.getContent()).isEqualTo("old text");
    assertThat(io.getRevisionNumber()).isEqualTo(1);
    assertThat(io.getRevisedAt()).isNotNull();
    assertThat(io.getRevisedBy()).isNotNull();
  }

  // ── pagination tests ──────────────────────────────────────────────────────

  @Test
  void history_pagination_secondPageReturnsSlice() {
    // 5 revisions total, page=1, pageSize=2 → DB gets skip=2, limit=2 → returns revs[2..3]
    var revs = IntStream.rangeClosed(1, 5)
        .mapToObj(i -> makeRevision("rev-" + i, "content-" + i, i))
        .toList();
    when(labJournalEntryRevisionDAO.countByEntry(ENTRY_OGM_ID)).thenReturn(5L);
    // DB is asked for skip=2, limit=2 and returns revisions 3 and 4 (what the DB would give)
    when(labJournalEntryRevisionDAO.findByEntry(ENTRY_OGM_ID, 2, 2))
        .thenReturn(List.of(revs.get(2), revs.get(3)));

    Response r = resource.history(ENTRY_APP_ID, 1, 2, sc);

    assertThat(r.getStatus()).isEqualTo(200);
    var b = body(r);
    assertThat(b.total()).isEqualTo(5);
    assertThat(b.page()).isEqualTo(1);
    assertThat(b.pageSize()).isEqualTo(2);
    assertThat(b.items()).hasSize(2);
    assertThat(b.items().get(0).getRevisionNumber()).isEqualTo(3); // index 2
    assertThat(b.items().get(1).getRevisionNumber()).isEqualTo(4); // index 3
  }

  @Test
  void history_pagination_beyondEndReturnsEmptyItems() {
    when(labJournalEntryRevisionDAO.countByEntry(ENTRY_OGM_ID)).thenReturn(1L);
    // skip=min(5*50, 1)=1 → beyond end → DB returns empty
    when(labJournalEntryRevisionDAO.findByEntry(ENTRY_OGM_ID, 1, 50)).thenReturn(List.of());

    Response r = resource.history(ENTRY_APP_ID, 5, 50, sc);

    assertThat(r.getStatus()).isEqualTo(200);
    var b = body(r);
    assertThat(b.total()).isEqualTo(1);
    assertThat(b.items()).isEmpty();
  }

  @Test
  void history_pagination_xTotalCountHeaderPresent() {
    when(labJournalEntryRevisionDAO.countByEntry(ENTRY_OGM_ID)).thenReturn(2L);
    when(labJournalEntryRevisionDAO.findByEntry(ENTRY_OGM_ID, 0, 50))
        .thenReturn(List.of(
            makeRevision("rev-1", "content-1", 1),
            makeRevision("rev-2", "content-2", 2)));

    Response r = resource.history(ENTRY_APP_ID, 0, 50, sc);

    assertThat(r.getStatus()).isEqualTo(200);
    assertThat(r.getHeaderString("X-Total-Count")).isEqualTo("2");
  }

  @Test
  void history_pagination_emptyHistoryXTotalCountIsZero() {
    // Default setUp: countByEntry=0, findByEntry(0, 50)=[]
    Response r = resource.history(ENTRY_APP_ID, 0, 50, sc);

    assertThat(r.getStatus()).isEqualTo(200);
    assertThat(r.getHeaderString("X-Total-Count")).isEqualTo("0");
  }

  @Test
  void history_pagination_usesDbSkipLimit_notInMemorySlice() {
    // Verify that the REST layer passes skip=page*pageSize to the DAO, not loading all
    when(labJournalEntryRevisionDAO.countByEntry(ENTRY_OGM_ID)).thenReturn(100L);
    when(labJournalEntryRevisionDAO.findByEntry(ENTRY_OGM_ID, 20, 10))
        .thenReturn(List.of(makeRevision("rev-x", "x", 80)));

    Response r = resource.history(ENTRY_APP_ID, 2, 10, sc);

    assertThat(r.getStatus()).isEqualTo(200);
    var b = body(r);
    assertThat(b.total()).isEqualTo(100);
    assertThat(b.page()).isEqualTo(2);
    assertThat(b.pageSize()).isEqualTo(10);
    assertThat(b.items()).hasSize(1);
    assertThat(b.items().get(0).getRevisionNumber()).isEqualTo(80);
  }
}
