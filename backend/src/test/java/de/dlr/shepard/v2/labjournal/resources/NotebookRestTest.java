package de.dlr.shepard.v2.labjournal.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.file.daos.FileBundleReferenceDAO;
import de.dlr.shepard.context.references.file.entities.FileBundleReference;
import de.dlr.shepard.context.references.file.entities.FileReference;
import de.dlr.shepard.context.references.file.services.SingletonFileReferenceService;
import de.dlr.shepard.data.file.entities.ShepardFile;
import de.dlr.shepard.v2.labjournal.io.NotebookReferenceIO;
import de.dlr.shepard.v2.labjournal.io.NotebookReferenceIO.ReferenceKind;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.Date;
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
@SuppressWarnings("unchecked")
class NotebookRestTest {

  static final String DO_APP_ID = "01957000-0000-7000-8000-000000000002";
  static final long DO_OGM_ID = 99L;
  static final String CALLER = "alice";

  @Mock
  EntityIdResolver entityIdResolver;

  @Mock
  PermissionsService permissionsService;

  @Mock
  SingletonFileReferenceService singletonService;

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
    resource.singletonService = singletonService;
    resource.fileBundleReferenceDAO = fileBundleReferenceDAO;

    when(sc.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
    when(entityIdResolver.resolveLong(DO_APP_ID)).thenReturn(DO_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(DO_OGM_ID, AccessType.Read, CALLER, 0L)).thenReturn(true);

    // Default: no files attached
    when(singletonService.listByDataObject(DO_APP_ID)).thenReturn(List.of());
    when(fileBundleReferenceDAO.findByDataObjectNeo4jId(DO_OGM_ID)).thenReturn(List.of());
  }

  // ─── helpers ──────────────────────────────────────────────────────────────

  private DataObject dataObject() {
    var d = new DataObject(DO_OGM_ID);
    d.setAppId(DO_APP_ID);
    d.setShepardId(DO_OGM_ID);
    return d;
  }

  private User user(String username) {
    var u = new User();
    u.setUsername(username);
    return u;
  }

  private FileReference singleton(String appId, String filename, Long fileSize) {
    var ref = new FileReference(10L);
    ref.setAppId(appId);
    ref.setName("ref-" + filename);
    ref.setDataObject(dataObject());
    ref.setCreatedAt(new Date(1000L));
    ref.setCreatedBy(user("alice"));

    var file = new ShepardFile();
    file.setFilename(filename);
    file.setFileSize(fileSize);
    ref.setFile(file);
    return ref;
  }

  private FileBundleReference bundle(String appId, List<ShepardFile> files) {
    var b = new FileBundleReference(20L);
    b.setAppId(appId);
    b.setName("bundle-" + appId);
    b.setDataObject(dataObject());
    b.setCreatedAt(new Date(2000L));
    b.setCreatedBy(user("bob"));
    for (ShepardFile f : files) b.addFile(f);
    return b;
  }

  private ShepardFile shepardFile(String filename, Long fileSize) {
    var f = new ShepardFile();
    f.setFilename(filename);
    f.setFileSize(fileSize);
    return f;
  }

  // ─── 401 / 403 / 404 ─────────────────────────────────────────────────────

  @Test
  void returns401WhenUnauthenticated() {
    when(sc.getUserPrincipal()).thenReturn(null);
    assertThat(resource.listNotebooks(DO_APP_ID, sc).getStatus()).isEqualTo(401);
  }

  @Test
  void returns404WhenDataObjectNotFound() {
    when(entityIdResolver.resolveLong("unknown-id")).thenThrow(new NotFoundException("not found"));
    assertThat(resource.listNotebooks("unknown-id", sc).getStatus()).isEqualTo(404);
  }

  @Test
  void returns403WhenCallerLacksReadPermission() {
    when(permissionsService.isAccessTypeAllowedForUser(DO_OGM_ID, AccessType.Read, CALLER, 0L)).thenReturn(false);
    assertThat(resource.listNotebooks(DO_APP_ID, sc).getStatus()).isEqualTo(403);
  }

  // ─── 200 empty ────────────────────────────────────────────────────────────

  @Test
  void returns200EmptyListWhenNoFiles() {
    var r = resource.listNotebooks(DO_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    var list = (List<NotebookReferenceIO>) r.getEntity();
    assertThat(list).isEmpty();
  }

  @Test
  void returns200EmptyWhenOnlyNonIpynbFiles() {
    when(singletonService.listByDataObject(DO_APP_ID))
      .thenReturn(List.of(singleton("s-1", "data.csv", 1024L)));
    when(fileBundleReferenceDAO.findByDataObjectNeo4jId(DO_OGM_ID))
      .thenReturn(List.of(bundle("b-1", List.of(shepardFile("report.pdf", 512L)))));

    var r = resource.listNotebooks(DO_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    assertThat((List<?>) r.getEntity()).isEmpty();
  }

  // ─── 200 with results ─────────────────────────────────────────────────────

  @Test
  void returns200WithSingletonIpynbFile() {
    when(singletonService.listByDataObject(DO_APP_ID))
      .thenReturn(List.of(singleton("singleton-app-1", "analysis.ipynb", 8192L)));

    var r = resource.listNotebooks(DO_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    var list = (List<NotebookReferenceIO>) r.getEntity();
    assertThat(list).hasSize(1);

    var io = list.get(0);
    assertThat(io.getAppId()).isEqualTo("singleton-app-1");
    assertThat(io.getFileName()).isEqualTo("analysis.ipynb");
    assertThat(io.getFileSize()).isEqualTo(8192L);
    assertThat(io.getMimeType()).isEqualTo(NotebookRest.IPYNB_MIME_TYPE);
    assertThat(io.getReferenceKind()).isEqualTo(ReferenceKind.SINGLETON);
    assertThat(io.getCreatedBy()).isEqualTo("alice");
  }

  @Test
  void returns200WithBundleFileIpynb() {
    var ipynb = shepardFile("experiment.ipynb", 4096L);
    var pdf = shepardFile("readme.pdf", 256L);
    when(fileBundleReferenceDAO.findByDataObjectNeo4jId(DO_OGM_ID))
      .thenReturn(List.of(bundle("bundle-app-1", List.of(pdf, ipynb))));

    var r = resource.listNotebooks(DO_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    var list = (List<NotebookReferenceIO>) r.getEntity();
    assertThat(list).hasSize(1);

    var io = list.get(0);
    assertThat(io.getAppId()).isEqualTo("bundle-app-1");
    assertThat(io.getFileName()).isEqualTo("experiment.ipynb");
    assertThat(io.getFileSize()).isEqualTo(4096L);
    assertThat(io.getMimeType()).isEqualTo(NotebookRest.IPYNB_MIME_TYPE);
    assertThat(io.getReferenceKind()).isEqualTo(ReferenceKind.BUNDLE_FILE);
    assertThat(io.getCreatedBy()).isEqualTo("bob");
  }

  @Test
  void returns200MixedSingletonAndBundleFiles() {
    when(singletonService.listByDataObject(DO_APP_ID))
      .thenReturn(List.of(singleton("s-1", "model.ipynb", 2048L)));

    var bundle = bundle("b-1", List.of(shepardFile("run.ipynb", 512L), shepardFile("data.csv", 100L)));
    when(fileBundleReferenceDAO.findByDataObjectNeo4jId(DO_OGM_ID))
      .thenReturn(List.of(bundle));

    var r = resource.listNotebooks(DO_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    var list = (List<NotebookReferenceIO>) r.getEntity();
    assertThat(list).hasSize(2);

    // Singletons come first
    assertThat(list.get(0).getReferenceKind()).isEqualTo(ReferenceKind.SINGLETON);
    assertThat(list.get(0).getAppId()).isEqualTo("s-1");
    assertThat(list.get(1).getReferenceKind()).isEqualTo(ReferenceKind.BUNDLE_FILE);
    assertThat(list.get(1).getAppId()).isEqualTo("b-1");
  }

  @Test
  void caseInsensitiveFilter_includesUppercaseExtension() {
    // .IPYNB should be included just like .ipynb
    when(singletonService.listByDataObject(DO_APP_ID))
      .thenReturn(
        List.of(
          singleton("s-upper", "Notebook.IPYNB", 100L),
          singleton("s-mixed", "Mixed.Ipynb", 200L),
          singleton("s-txt", "notes.txt", 300L) // excluded
        )
      );

    var r = resource.listNotebooks(DO_APP_ID, sc);
    var list = (List<NotebookReferenceIO>) r.getEntity();
    assertThat(list).hasSize(2);
    assertThat(list).extracting(NotebookReferenceIO::getAppId).containsExactlyInAnyOrder("s-upper", "s-mixed");
  }

  @Test
  void deletedSingletonIsExcluded() {
    var ref = singleton("s-deleted", "deleted.ipynb", 100L);
    ref.setDeleted(true);
    when(singletonService.listByDataObject(DO_APP_ID)).thenReturn(List.of(ref));

    var r = resource.listNotebooks(DO_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    assertThat((List<?>) r.getEntity()).isEmpty();
  }

  @Test
  void deletedBundleIsExcluded() {
    var b = bundle("b-deleted", List.of(shepardFile("deleted.ipynb", 100L)));
    b.setDeleted(true);
    when(fileBundleReferenceDAO.findByDataObjectNeo4jId(DO_OGM_ID)).thenReturn(List.of(b));

    var r = resource.listNotebooks(DO_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    assertThat((List<?>) r.getEntity()).isEmpty();
  }

  @Test
  void fileSizeNullable() {
    // fileSize may be null for pre-FB1a uploads
    when(singletonService.listByDataObject(DO_APP_ID))
      .thenReturn(List.of(singleton("s-old", "old.ipynb", null)));

    var r = resource.listNotebooks(DO_APP_ID, sc);
    var list = (List<NotebookReferenceIO>) r.getEntity();
    assertThat(list).hasSize(1);
    assertThat(list.get(0).getFileSize()).isNull();
  }

  // ─── isIpynb helper ───────────────────────────────────────────────────────

  @Test
  void isIpynb_acceptsLowercase() {
    assertThat(NotebookRest.isIpynb("notebook.ipynb")).isTrue();
  }

  @Test
  void isIpynb_acceptsUppercase() {
    assertThat(NotebookRest.isIpynb("Notebook.IPYNB")).isTrue();
  }

  @Test
  void isIpynb_rejectsNonIpynb() {
    assertThat(NotebookRest.isIpynb("data.csv")).isFalse();
    assertThat(NotebookRest.isIpynb("notebook.ipynb.bak")).isFalse();
    assertThat(NotebookRest.isIpynb("")).isFalse();
    assertThat(NotebookRest.isIpynb(null)).isFalse();
  }
}
