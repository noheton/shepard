package de.dlr.shepard.context.references.file.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.references.file.daos.FileBundleReferenceDAO;
import de.dlr.shepard.context.references.file.daos.FileGroupDAO;
import de.dlr.shepard.context.references.file.entities.FileBundleReference;
import de.dlr.shepard.context.references.file.entities.FileGroup;
import de.dlr.shepard.context.references.file.io.CreateFileGroupIO;
import de.dlr.shepard.data.file.entities.ShepardFile;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link FileGroupService} (FR1a). Covers the
 * default-group create / patch / delete / file-attach flows plus the
 * "refuse to delete the last group" and "refuse to delete a group with
 * files" guards.
 */
@QuarkusComponentTest
public class FileGroupServiceTest {

  @InjectMock
  FileGroupDAO fileGroupDAO;

  @InjectMock
  FileBundleReferenceDAO fileBundleReferenceDAO;

  @InjectMock
  UserService userService;

  @InjectMock
  DateHelper dateHelper;

  @Inject
  FileGroupService service;

  private static final String BUNDLE_APP_ID = "0190d1f8-7c4d-7d8a-91a5-b7c2d3e4f506";
  private static final String GROUP_APP_ID = "0190d1f8-7c4d-7d8a-91a5-b7c2d3e4f507";

  // ─── createGroup ──────────────────────────────────────────────────────────

  @Test
  public void createGroup_assignsAutoIndexAndPersists() {
    User user = new User("alice");
    Date now = new Date(123L);
    FileBundleReference bundle = new FileBundleReference(1L);
    bundle.setAppId(BUNDLE_APP_ID);

    when(userService.getCurrentUser()).thenReturn(user);
    when(dateHelper.getDate()).thenReturn(now);
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(bundle);
    when(fileGroupDAO.findMaxIndexUnderBundle(BUNDLE_APP_ID)).thenReturn(2);
    when(fileGroupDAO.createOrUpdate(any(FileGroup.class))).thenAnswer(inv -> {
      FileGroup g = inv.getArgument(0);
      g.setId(42L);
      g.setAppId(GROUP_APP_ID);
      return g;
    });
    when(fileBundleReferenceDAO.createOrUpdate(any(FileBundleReference.class))).thenAnswer(inv -> inv.getArgument(0));

    CreateFileGroupIO req = new CreateFileGroupIO();
    req.setName("phase1");

    FileGroup created = service.createGroup(BUNDLE_APP_ID, req);

    assertEquals("phase1", created.getName());
    assertEquals(3, created.getIndex(), "auto-assigned index = max(2) + 1");
    assertEquals(now, created.getCreatedAt());
    assertEquals(user, created.getCreatedBy());
    assertEquals(GROUP_APP_ID, created.getAppId());
  }

  @Test
  public void createGroup_respectsExplicitIndex() {
    User user = new User("alice");
    FileBundleReference bundle = new FileBundleReference(1L);
    bundle.setAppId(BUNDLE_APP_ID);

    when(userService.getCurrentUser()).thenReturn(user);
    when(dateHelper.getDate()).thenReturn(new Date());
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(bundle);
    when(fileGroupDAO.createOrUpdate(any(FileGroup.class))).thenAnswer(inv -> inv.getArgument(0));
    when(fileBundleReferenceDAO.createOrUpdate(any(FileBundleReference.class))).thenAnswer(inv -> inv.getArgument(0));

    CreateFileGroupIO req = new CreateFileGroupIO();
    req.setName("phase1");
    req.setIndex(7);

    FileGroup created = service.createGroup(BUNDLE_APP_ID, req);
    assertEquals(7, created.getIndex(), "explicit index wins over auto-assignment");
  }

  @Test
  public void createGroup_blankNameFails() {
    CreateFileGroupIO req = new CreateFileGroupIO();
    req.setName("  ");
    assertThrows(BadRequestException.class, () -> service.createGroup(BUNDLE_APP_ID, req));
  }

  @Test
  public void createGroup_nullNameFails() {
    CreateFileGroupIO req = new CreateFileGroupIO();
    req.setName(null);
    assertThrows(BadRequestException.class, () -> service.createGroup(BUNDLE_APP_ID, req));
  }

  @Test
  public void createGroup_missingBundleFails() {
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(null);
    CreateFileGroupIO req = new CreateFileGroupIO();
    req.setName("phase1");
    assertThrows(NotFoundException.class, () -> service.createGroup(BUNDLE_APP_ID, req));
  }

  // ─── patchGroup ───────────────────────────────────────────────────────────

  @Test
  public void patchGroup_updatesProvidedFields() {
    User user = new User("alice");
    Date now = new Date(456L);
    FileGroup existing = new FileGroup(1L);
    existing.setName("old");
    existing.setDescription("old desc");
    existing.setIndex(0);
    when(fileGroupDAO.findByAppId(GROUP_APP_ID)).thenReturn(existing);
    when(userService.getCurrentUser()).thenReturn(user);
    when(dateHelper.getDate()).thenReturn(now);
    when(fileGroupDAO.createOrUpdate(any(FileGroup.class))).thenAnswer(inv -> inv.getArgument(0));

    Map<String, Object> patch = new HashMap<>();
    patch.put("name", "new");
    patch.put("description", null); // explicit clear
    patch.put("index", 5L);

    FileGroup patched = service.patchGroup(GROUP_APP_ID, patch);
    assertEquals("new", patched.getName());
    assertNull(patched.getDescription(), "explicit null in merge-patch clears the field");
    assertEquals(5, patched.getIndex());
    assertEquals(now, patched.getUpdatedAt());
    assertEquals(user, patched.getUpdatedBy());
  }

  @Test
  public void patchGroup_blankNameRejected() {
    FileGroup existing = new FileGroup(1L);
    existing.setName("old");
    when(fileGroupDAO.findByAppId(GROUP_APP_ID)).thenReturn(existing);
    Map<String, Object> patch = Map.of("name", "  ");
    assertThrows(BadRequestException.class, () -> service.patchGroup(GROUP_APP_ID, patch));
  }

  @Test
  public void patchGroup_missingFails() {
    when(fileGroupDAO.findByAppId(GROUP_APP_ID)).thenReturn(null);
    assertThrows(NotFoundException.class, () -> service.patchGroup(GROUP_APP_ID, Map.of("name", "x")));
  }

  @Test
  public void patchGroup_nullPatchRejected() {
    FileGroup existing = new FileGroup(1L);
    when(fileGroupDAO.findByAppId(GROUP_APP_ID)).thenReturn(existing);
    assertThrows(BadRequestException.class, () -> service.patchGroup(GROUP_APP_ID, null));
  }

  @Test
  public void patchGroup_attributesNullClears() {
    User user = new User("alice");
    FileGroup existing = new FileGroup(1L);
    existing.setName("g");
    existing.setAttributes(new HashMap<>(Map.of("k", "v")));
    when(fileGroupDAO.findByAppId(GROUP_APP_ID)).thenReturn(existing);
    when(userService.getCurrentUser()).thenReturn(user);
    when(dateHelper.getDate()).thenReturn(new Date());
    when(fileGroupDAO.createOrUpdate(any(FileGroup.class))).thenAnswer(inv -> inv.getArgument(0));

    Map<String, Object> patch = new HashMap<>();
    patch.put("attributes", null);
    FileGroup patched = service.patchGroup(GROUP_APP_ID, patch);
    assertTrue(patched.getAttributes().isEmpty());
  }

  @Test
  public void patchGroup_attributesMapMerges() {
    User user = new User("alice");
    FileGroup existing = new FileGroup(1L);
    existing.setName("g");
    when(fileGroupDAO.findByAppId(GROUP_APP_ID)).thenReturn(existing);
    when(userService.getCurrentUser()).thenReturn(user);
    when(dateHelper.getDate()).thenReturn(new Date());
    when(fileGroupDAO.createOrUpdate(any(FileGroup.class))).thenAnswer(inv -> inv.getArgument(0));

    Map<String, Object> patch = Map.of("attributes", Map.of("phase", "ramp_up", "operator", "alice"));
    FileGroup patched = service.patchGroup(GROUP_APP_ID, patch);
    assertEquals("ramp_up", patched.getAttributes().get("phase"));
    assertEquals("alice", patched.getAttributes().get("operator"));
  }

  // ─── deleteGroup ──────────────────────────────────────────────────────────

  @Test
  public void deleteGroup_refusesIfHasFilesAndNotForced() {
    FileGroup g = new FileGroup(1L);
    g.setName("g");
    g.setFiles(List.of(new ShepardFile("oid", new Date(), "f.bin", "md5")));
    when(fileGroupDAO.findByAppId(GROUP_APP_ID)).thenReturn(g);
    when(fileGroupDAO.findBundleAppIdForGroup(GROUP_APP_ID)).thenReturn(BUNDLE_APP_ID);
    // siblings: 2 groups so the "last group" guard doesn't trip first
    var siblings = new ArrayList<FileGroup>();
    siblings.add(g);
    siblings.add(new FileGroup(2L));
    when(fileGroupDAO.findByBundleAppId(BUNDLE_APP_ID)).thenReturn(siblings);

    assertThrows(BadRequestException.class, () -> service.deleteGroup(GROUP_APP_ID, false));
  }

  @Test
  public void deleteGroup_forcedDeletesEvenWithFiles() {
    FileGroup g = new FileGroup(1L);
    g.setName("g");
    g.setFiles(List.of(new ShepardFile("oid", new Date(), "f.bin", "md5")));
    when(fileGroupDAO.findByAppId(GROUP_APP_ID)).thenReturn(g);
    when(fileGroupDAO.findBundleAppIdForGroup(GROUP_APP_ID)).thenReturn(BUNDLE_APP_ID);
    var siblings = new ArrayList<FileGroup>();
    siblings.add(g);
    siblings.add(new FileGroup(2L));
    when(fileGroupDAO.findByBundleAppId(BUNDLE_APP_ID)).thenReturn(siblings);
    when(userService.getCurrentUser()).thenReturn(new User("alice"));
    when(dateHelper.getDate()).thenReturn(new Date());
    when(fileGroupDAO.createOrUpdate(any(FileGroup.class))).thenAnswer(inv -> inv.getArgument(0));

    service.deleteGroup(GROUP_APP_ID, true);
    assertTrue(g.isDeleted());
  }

  @Test
  public void deleteGroup_refusesIfLastGroup() {
    FileGroup g = new FileGroup(1L);
    g.setName("only");
    when(fileGroupDAO.findByAppId(GROUP_APP_ID)).thenReturn(g);
    when(fileGroupDAO.findBundleAppIdForGroup(GROUP_APP_ID)).thenReturn(BUNDLE_APP_ID);
    when(fileGroupDAO.findByBundleAppId(BUNDLE_APP_ID)).thenReturn(List.of(g));

    assertThrows(BadRequestException.class, () -> service.deleteGroup(GROUP_APP_ID, true));
  }

  @Test
  public void deleteGroup_missingFails() {
    when(fileGroupDAO.findByAppId(GROUP_APP_ID)).thenReturn(null);
    assertThrows(NotFoundException.class, () -> service.deleteGroup(GROUP_APP_ID, false));
  }

  // ─── attachFile ───────────────────────────────────────────────────────────

  @Test
  public void attachFile_addsToGroupAndBundleShadow() {
    FileGroup g = new FileGroup(1L);
    g.setName("g");
    g.setFiles(new ArrayList<>());
    FileBundleReference bundle = new FileBundleReference(2L);
    bundle.setAppId(BUNDLE_APP_ID);
    bundle.setFiles(new ArrayList<>());

    when(fileGroupDAO.findByAppId(GROUP_APP_ID)).thenReturn(g);
    when(fileGroupDAO.findBundleAppIdForGroup(GROUP_APP_ID)).thenReturn(BUNDLE_APP_ID);
    when(fileBundleReferenceDAO.findByAppId(BUNDLE_APP_ID)).thenReturn(bundle);
    when(fileGroupDAO.createOrUpdate(any(FileGroup.class))).thenAnswer(inv -> inv.getArgument(0));
    when(fileBundleReferenceDAO.createOrUpdate(any(FileBundleReference.class))).thenAnswer(inv -> inv.getArgument(0));

    ShepardFile f = new ShepardFile("oid-x", new Date(), "x.bin", "md5x");
    FileGroup updated = service.attachFile(GROUP_APP_ID, f);
    assertEquals(1, updated.getFiles().size());
    assertEquals("oid-x", updated.getFiles().get(0).getOid());
    // Compatibility shadow on the bundle.
    assertEquals(1, bundle.getFiles().size());
  }

  @Test
  public void attachFile_missingGroupFails() {
    when(fileGroupDAO.findByAppId(GROUP_APP_ID)).thenReturn(null);
    ShepardFile f = new ShepardFile("oid", new Date(), "f", "md5");
    assertThrows(NotFoundException.class, () -> service.attachFile(GROUP_APP_ID, f));
  }

  // ─── listGroups / getByAppId / findBundleAppIdForGroup ────────────────────

  @Test
  public void listGroups_passesThroughToDAO() {
    var g1 = new FileGroup(1L);
    var g2 = new FileGroup(2L);
    when(fileGroupDAO.findByBundleAppId(BUNDLE_APP_ID)).thenReturn(List.of(g1, g2));
    var got = service.listGroups(BUNDLE_APP_ID);
    assertEquals(2, got.size());
  }

  @Test
  public void getByAppId_passesThroughToDAO() {
    var g = new FileGroup(1L);
    when(fileGroupDAO.findByAppId(GROUP_APP_ID)).thenReturn(g);
    assertEquals(g, service.getByAppId(GROUP_APP_ID));
  }

  @Test
  public void findBundleAppIdForGroup_passesThroughToDAO() {
    when(fileGroupDAO.findBundleAppIdForGroup(GROUP_APP_ID)).thenReturn(BUNDLE_APP_ID);
    assertEquals(BUNDLE_APP_ID, service.findBundleAppIdForGroup(GROUP_APP_ID));
  }

  @Test
  public void findBundleAppIdForGroup_returnsNullWhenAbsent() {
    when(fileGroupDAO.findBundleAppIdForGroup(GROUP_APP_ID)).thenReturn(null);
    assertNull(service.findBundleAppIdForGroup(GROUP_APP_ID));
  }
}
