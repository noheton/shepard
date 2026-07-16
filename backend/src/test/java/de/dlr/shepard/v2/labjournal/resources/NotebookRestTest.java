package de.dlr.shepard.v2.labjournal.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.references.file.daos.FileBundleReferenceDAO;
import de.dlr.shepard.context.references.file.daos.SingletonFileReferenceDAO;
import de.dlr.shepard.context.references.file.daos.SingletonFileReferenceDAO.NotebookProjection;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import de.dlr.shepard.v2.labjournal.io.NotebookReferenceIO;
import de.dlr.shepard.v2.labjournal.io.NotebookReferenceIO.ReferenceKind;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * J1b — Mockito unit tests for {@link NotebookRest}.
 *
 * <p>No CDI container or network required — fields are injected directly,
 * matching the pattern from {@link LabJournalRenderRestTest}.
 */
class NotebookRestTest {

  static final String DO_APP_ID = "01957000-0000-7000-8000-000000000002";
  static final long DO_OGM_ID = 99L;
  static final String CALLER = "alice";

  @Mock
  EntityIdResolver entityIdResolver;

  @Mock
  PermissionsService permissionsService;

  @Mock
  SingletonFileReferenceDAO singletonFileReferenceDAO;

  @Mock
  FileBundleReferenceDAO fileBundleReferenceDAO;

  @Mock
  SecurityContext sc;

  @Mock
  Principal principal;

  NotebookRest resource;

  @BeforeEach
  void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);

    resource = new NotebookRest();
    resource.entityIdResolver = entityIdResolver;
    resource.permissionsService = permissionsService;
    resource.singletonFileReferenceDAO = singletonFileReferenceDAO;
    resource.fileBundleReferenceDAO = fileBundleReferenceDAO;

    when(sc.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
    when(entityIdResolver.resolveLong(DO_APP_ID)).thenReturn(DO_OGM_ID);
    // Production gates listing on the appId-based read check.
    when(permissionsService.isAccessAllowedForDataObjectAppId(DO_APP_ID, AccessType.Read, CALLER)).thenReturn(true);

    // Default: no notebooks attached
    when(singletonFileReferenceDAO.countNotebooks(DO_APP_ID)).thenReturn(0L);
    when(fileBundleReferenceDAO.countNotebooks(DO_APP_ID)).thenReturn(0L);
  }

  // ─── helpers ──────────────────────────────────────────────────────────────

  private NotebookProjection projection(String appId, String filename, Long fileSize, String createdBy) {
    return new NotebookProjection(
      appId, filename, fileSize,
      Instant.ofEpochMilli(1000L).toString(),
      createdBy
    );
  }

  // ─── 401 / 403 / 404 ─────────────────────────────────────────────────────

  @Test
  void returns401WhenUnauthenticated() {
    when(sc.getUserPrincipal()).thenReturn(null);
    assertThat(resource.listNotebooks(DO_APP_ID, 0, 50, sc).getStatus()).isEqualTo(401);
  }

  @Test
  void returns404WhenDataObjectNotFound() {
    when(entityIdResolver.resolveLong("unknown-id")).thenThrow(new NotFoundException("not found"));
    assertThat(resource.listNotebooks("unknown-id", 0, 50, sc).getStatus()).isEqualTo(404);
  }

  @Test
  void returns403WhenCallerLacksReadPermission() {
    when(permissionsService.isAccessAllowedForDataObjectAppId(DO_APP_ID, AccessType.Read, CALLER)).thenReturn(false);
    assertThat(resource.listNotebooks(DO_APP_ID, 0, 50, sc).getStatus()).isEqualTo(403);
  }

  // ─── 200 empty ────────────────────────────────────────────────────────────

  @Test
  void returns200EmptyListWhenNoFiles() {
    var r = resource.listNotebooks(DO_APP_ID, 0, 50, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    assertThat(((PagedResponseIO<?>) r.getEntity()).items()).isEmpty();
  }

  @Test
  void returns200EmptyWhenOnlyNonIpynbFiles() {
    // .ipynb filter is enforced in Cypher; DAOs return count=0 for non-ipynb files
    var r = resource.listNotebooks(DO_APP_ID, 0, 50, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    assertThat(((PagedResponseIO<?>) r.getEntity()).items()).isEmpty();
  }

  // ─── 200 with results ─────────────────────────────────────────────────────

  @Test
  void returns200WithSingletonIpynbFile() {
    var proj = projection("singleton-app-1", "analysis.ipynb", 8192L, "alice");
    when(singletonFileReferenceDAO.countNotebooks(DO_APP_ID)).thenReturn(1L);
    // singletonLimit = min(pageSize=50, singletonCount=1) = 1
    when(singletonFileReferenceDAO.listNotebooks(DO_APP_ID, 0L, 1)).thenReturn(List.of(proj));

    var r = resource.listNotebooks(DO_APP_ID, 0, 50, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    var paged = (PagedResponseIO<NotebookReferenceIO>) r.getEntity();
    var items = paged.items();
    assertThat(items).hasSize(1);
    assertThat(paged.total()).isEqualTo(1L);
    assertThat(paged.page()).isEqualTo(0);
    assertThat(paged.pageSize()).isEqualTo(50);

    var io = items.get(0);
    assertThat(io.getAppId()).isEqualTo("singleton-app-1");
    assertThat(io.getFileName()).isEqualTo("analysis.ipynb");
    assertThat(io.getFileSize()).isEqualTo(8192L);
    assertThat(io.getMimeType()).isEqualTo(NotebookRest.IPYNB_MIME_TYPE);
    assertThat(io.getReferenceKind()).isEqualTo(ReferenceKind.SINGLETON);
    assertThat(io.getCreatedBy()).isEqualTo("alice");
  }

  @Test
  void returns200WithBundleFileIpynb() {
    var proj = projection("bundle-app-1", "experiment.ipynb", 4096L, "bob");
    when(fileBundleReferenceDAO.countNotebooks(DO_APP_ID)).thenReturn(1L);
    when(fileBundleReferenceDAO.listNotebooks(DO_APP_ID, 0L, 50)).thenReturn(List.of(proj));

    var r = resource.listNotebooks(DO_APP_ID, 0, 50, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    var items = ((PagedResponseIO<NotebookReferenceIO>) r.getEntity()).items();
    assertThat(items).hasSize(1);

    var io = items.get(0);
    assertThat(io.getAppId()).isEqualTo("bundle-app-1");
    assertThat(io.getFileName()).isEqualTo("experiment.ipynb");
    assertThat(io.getFileSize()).isEqualTo(4096L);
    assertThat(io.getMimeType()).isEqualTo(NotebookRest.IPYNB_MIME_TYPE);
    assertThat(io.getReferenceKind()).isEqualTo(ReferenceKind.BUNDLE_FILE);
    assertThat(io.getCreatedBy()).isEqualTo("bob");
  }

  @Test
  void returns200MixedSingletonAndBundleFiles() {
    // singletonLimit = min(50, 1) = 1; bundleSpace = 50 - 1 = 49
    when(singletonFileReferenceDAO.countNotebooks(DO_APP_ID)).thenReturn(1L);
    when(singletonFileReferenceDAO.listNotebooks(DO_APP_ID, 0L, 1))
      .thenReturn(List.of(projection("s-1", "model.ipynb", 2048L, "alice")));

    when(fileBundleReferenceDAO.countNotebooks(DO_APP_ID)).thenReturn(1L);
    when(fileBundleReferenceDAO.listNotebooks(DO_APP_ID, 0L, 49))
      .thenReturn(List.of(projection("b-1", "run.ipynb", 512L, "bob")));

    var r = resource.listNotebooks(DO_APP_ID, 0, 50, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    var items = ((PagedResponseIO<NotebookReferenceIO>) r.getEntity()).items();
    assertThat(items).hasSize(2);

    // Singletons come first
    assertThat(items.get(0).getReferenceKind()).isEqualTo(ReferenceKind.SINGLETON);
    assertThat(items.get(0).getAppId()).isEqualTo("s-1");
    assertThat(items.get(1).getReferenceKind()).isEqualTo(ReferenceKind.BUNDLE_FILE);
    assertThat(items.get(1).getAppId()).isEqualTo("b-1");
  }

  @Test
  void caseInsensitiveFilter_includesUppercaseExtension() {
    // Case-insensitive filter is enforced in Cypher (toLower(..) ENDS WITH '.ipynb')
    var p1 = projection("s-upper", "Notebook.IPYNB", 100L, "alice");
    var p2 = projection("s-mixed", "Mixed.Ipynb", 200L, "alice");
    when(singletonFileReferenceDAO.countNotebooks(DO_APP_ID)).thenReturn(2L);
    // singletonLimit = min(pageSize=50, singletonCount=2) = 2
    when(singletonFileReferenceDAO.listNotebooks(DO_APP_ID, 0L, 2)).thenReturn(List.of(p1, p2));

    var r = resource.listNotebooks(DO_APP_ID, 0, 50, sc);
    var items = ((PagedResponseIO<NotebookReferenceIO>) r.getEntity()).items();
    assertThat(items).hasSize(2);
    assertThat(items).extracting(NotebookReferenceIO::getAppId).containsExactlyInAnyOrder("s-upper", "s-mixed");
  }

  @Test
  void deletedSingletonIsExcluded() {
    // WHERE (r.deleted IS NULL OR r.deleted = false) in Cypher; DAO returns count=0
    var r = resource.listNotebooks(DO_APP_ID, 0, 50, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    assertThat(((PagedResponseIO<?>) r.getEntity()).items()).isEmpty();
  }

  @Test
  void deletedBundleIsExcluded() {
    // WHERE (r.deleted IS NULL OR r.deleted = false) in Cypher; DAO returns count=0
    var r = resource.listNotebooks(DO_APP_ID, 0, 50, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    assertThat(((PagedResponseIO<?>) r.getEntity()).items()).isEmpty();
  }

  @Test
  void fileSizeNullable() {
    // fileSize may be null for pre-FB1a uploads
    var proj = projection("s-old", "old.ipynb", null, "alice");
    when(singletonFileReferenceDAO.countNotebooks(DO_APP_ID)).thenReturn(1L);
    // singletonLimit = min(50, 1) = 1
    when(singletonFileReferenceDAO.listNotebooks(DO_APP_ID, 0L, 1)).thenReturn(List.of(proj));

    var r = resource.listNotebooks(DO_APP_ID, 0, 50, sc);
    var items = ((PagedResponseIO<NotebookReferenceIO>) r.getEntity()).items();
    assertThat(items).hasSize(1);
    assertThat(items.get(0).getFileSize()).isNull();
  }

  // ─── pagination ───────────────────────────────────────────────────────────

  @Test
  void paginationSlicesCorrectly() {
    // 3 notebooks total; request page=1 with pageSize=2 → singletonSkip=2, limit=1
    when(singletonFileReferenceDAO.countNotebooks(DO_APP_ID)).thenReturn(3L);
    when(singletonFileReferenceDAO.listNotebooks(DO_APP_ID, 2L, 1))
      .thenReturn(List.of(projection("s-3", "c.ipynb", 300L, "alice")));

    var r = resource.listNotebooks(DO_APP_ID, 1, 2, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    var paged = (PagedResponseIO<NotebookReferenceIO>) r.getEntity();
    assertThat(paged.total()).isEqualTo(3L);
    assertThat(paged.page()).isEqualTo(1);
    assertThat(paged.pageSize()).isEqualTo(2);
    assertThat(paged.items()).hasSize(1);
    assertThat(paged.items().get(0).getAppId()).isEqualTo("s-3");
  }

  @Test
  void paginationBeyondLastPageReturnsEmptyItems() {
    // page=5, pageSize=50 → skip=250 > total=1; singletonSkip=1, singletonLimit=0
    when(singletonFileReferenceDAO.countNotebooks(DO_APP_ID)).thenReturn(1L);

    var r = resource.listNotebooks(DO_APP_ID, 5, 50, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    var paged = (PagedResponseIO<NotebookReferenceIO>) r.getEntity();
    assertThat(paged.total()).isEqualTo(1L);
    assertThat(paged.items()).isEmpty();
  }
}
