package de.dlr.shepard.versioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.daos.PermissionsDAO;
import de.dlr.shepard.auth.permission.io.PermissionsIO;
import de.dlr.shepard.auth.permission.model.Permissions;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserGroupService;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.util.PermissionType;
import jakarta.enterprise.event.Event;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * ENT1a service tests — exercises happy-path + edge cases of
 * {@code createVersion}, {@code listVersions}, {@code getVersion},
 * {@code patchVersionPermissions}, {@code deleteVersion}, plus the
 * label-shape validation rules and ACL inheritance behaviour.
 */
class EntityVersionServiceTest {

  private EntityVersionService service;
  private EntityVersionDAO versionDAO;
  private PermissionsDAO permissionsDAO;
  private UserService userService;
  private UserGroupService userGroupService;
  @SuppressWarnings("unchecked")
  private Event<VersionCreatedEvent> versionCreatedEvent = (Event<VersionCreatedEvent>) mock(Event.class);

  @BeforeEach
  void setUp() {
    versionDAO = mock(EntityVersionDAO.class);
    permissionsDAO = mock(PermissionsDAO.class);
    userService = mock(UserService.class);
    userGroupService = mock(UserGroupService.class);
    @SuppressWarnings("unchecked")
    Event<VersionCreatedEvent> ev = (Event<VersionCreatedEvent>) mock(Event.class);
    versionCreatedEvent = ev;

    service = new EntityVersionService();
    service.versionDAO = versionDAO;
    service.permissionsDAO = permissionsDAO;
    service.userService = userService;
    service.userGroupService = userGroupService;
    service.versionCreatedEvent = versionCreatedEvent;

    // Permissions DAO returns its input back so we can inspect the staged ACL.
    when(permissionsDAO.createOrUpdate(any(Permissions.class))).thenAnswer(inv -> {
      Permissions p = inv.getArgument(0);
      if (p.getAppId() == null) p.setAppId("perm-" + System.nanoTime());
      return p;
    });
    when(versionDAO.save(any(EntityVersion.class))).thenAnswer(inv -> {
      EntityVersion v = inv.getArgument(0);
      if (v.getAppId() == null) v.setAppId("v-app-" + v.getVersionLabel());
      return v;
    });
  }

  private User user(String name) {
    User u = new User();
    u.setUsername(name);
    return u;
  }

  // ─── createVersion: happy path + auto-label ───────────────────────────

  @Test
  void createVersionAutoSuggestsLabelFromOrdinal() {
    when(versionDAO.findMaxOrdinalByParent("01HF-A")).thenReturn(2);
    when(versionDAO.findLatestByParent("01HF-A")).thenReturn(Optional.empty());

    EntityVersion saved = service.createVersion("collection", "01HF-A", null, null, "alice");

    assertEquals("v3", saved.getVersionLabel());
    assertEquals(3, saved.getVersionOrdinal());
    assertEquals("collection", saved.getParentEntityKind());
    assertEquals("alice", saved.getCreatedBy());
    assertNotNull(saved.getAppId());
    verify(versionDAO, times(1)).attachToParent(eq(saved), eq("01HF-A"));
    verify(versionCreatedEvent, times(1)).fire(any(VersionCreatedEvent.class));
  }

  @Test
  void createVersionAutoSuggestsV1WhenNoPriorVersion() {
    when(versionDAO.findMaxOrdinalByParent("01HF-Z")).thenReturn(0);
    when(versionDAO.findLatestByParent("01HF-Z")).thenReturn(Optional.empty());
    EntityVersion saved = service.createVersion("data-object", "01HF-Z", null, null, "alice");
    assertEquals("v1", saved.getVersionLabel());
    assertEquals(1, saved.getVersionOrdinal());
  }

  @Test
  void createVersionAcceptsUserSuppliedSemverLabel() {
    when(versionDAO.findMaxOrdinalByParent("01HF-A")).thenReturn(0);
    when(versionDAO.existsLabelForParent("01HF-A", "1.0.0-rc.1")).thenReturn(false);
    when(versionDAO.findLatestByParent("01HF-A")).thenReturn(Optional.empty());
    EntityVersion saved = service.createVersion("collection", "01HF-A", "1.0.0-rc.1", "first RC", "alice");
    assertEquals("1.0.0-rc.1", saved.getVersionLabel());
    assertEquals("first RC", saved.getNote());
  }

  @Test
  void createVersionRejectsDuplicateLabel() {
    when(versionDAO.findMaxOrdinalByParent("01HF-A")).thenReturn(0);
    when(versionDAO.existsLabelForParent("01HF-A", "v1")).thenReturn(true);
    EntityVersionException eve = assertThrows(EntityVersionException.class, () ->
      service.createVersion("collection", "01HF-A", "v1", null, "alice")
    );
    assertEquals(EntityVersionException.Reason.LABEL_DUPLICATE, eve.reason());
  }

  @Test
  void createVersionRejectsInvalidLabelShape() {
    when(versionDAO.findMaxOrdinalByParent("01HF-A")).thenReturn(0);
    EntityVersionException eve = assertThrows(EntityVersionException.class, () ->
      service.createVersion("collection", "01HF-A", "has whitespace", null, "alice")
    );
    assertEquals(EntityVersionException.Reason.LABEL_INVALID, eve.reason());
  }

  @Test
  void createVersionRejectsPurelyNumericLabel() {
    when(versionDAO.findMaxOrdinalByParent("01HF-A")).thenReturn(0);
    EntityVersionException eve = assertThrows(EntityVersionException.class, () ->
      service.createVersion("collection", "01HF-A", "123", null, "alice")
    );
    assertEquals(EntityVersionException.Reason.LABEL_INVALID, eve.reason());
  }

  @Test
  void createVersionRejectsOverlongLabel() {
    when(versionDAO.findMaxOrdinalByParent("01HF-A")).thenReturn(0);
    String tooLong = "v" + "a".repeat(80);
    EntityVersionException eve = assertThrows(EntityVersionException.class, () ->
      service.createVersion("collection", "01HF-A", tooLong, null, "alice")
    );
    assertEquals(EntityVersionException.Reason.LABEL_INVALID, eve.reason());
  }

  @Test
  void createVersionRejectsOverlongNote() {
    when(versionDAO.findMaxOrdinalByParent("01HF-A")).thenReturn(0);
    when(versionDAO.findLatestByParent("01HF-A")).thenReturn(Optional.empty());
    String hugeNote = "x".repeat(5000);
    EntityVersionException eve = assertThrows(EntityVersionException.class, () ->
      service.createVersion("collection", "01HF-A", null, hugeNote, "alice")
    );
    assertEquals(EntityVersionException.Reason.LABEL_INVALID, eve.reason());
  }

  @Test
  void createVersionRejectsUnsupportedKind() {
    EntityVersionException eve = assertThrows(EntityVersionException.class, () ->
      service.createVersion("bundle", "01HF-A", null, null, "alice")
    );
    assertEquals(EntityVersionException.Reason.KIND_UNSUPPORTED, eve.reason());
  }

  @Test
  void createVersionRejectsBlankParentAppId() {
    assertThrows(IllegalArgumentException.class, () ->
      service.createVersion("collection", "", null, null, "alice")
    );
    assertThrows(IllegalArgumentException.class, () ->
      service.createVersion("collection", null, null, null, "alice")
    );
  }

  // ─── createVersion: ACL inheritance ───────────────────────────────────

  @Test
  void createVersionInheritsPreviousAcl() {
    when(versionDAO.findMaxOrdinalByParent("01HF-A")).thenReturn(1);
    EntityVersion previous = new EntityVersion();
    previous.setVersionLabel("v1");
    Permissions previousAcl = new Permissions();
    previousAcl.setAppId("perm-old");
    previousAcl.setOwner(user("bob"));
    previousAcl.setPermissionType(PermissionType.Private);
    previousAcl.setReader(new ArrayList<>(List.of(user("carol"))));
    previousAcl.setWriter(new ArrayList<>());
    previousAcl.setManager(new ArrayList<>());
    previousAcl.setReaderGroups(new ArrayList<>());
    previousAcl.setWriterGroups(new ArrayList<>());
    previous.setPermissions(previousAcl);
    when(versionDAO.findLatestByParent("01HF-A")).thenReturn(Optional.of(previous));

    EntityVersion saved = service.createVersion("collection", "01HF-A", null, null, "alice");

    ArgumentCaptor<Permissions> aclCaptor = ArgumentCaptor.forClass(Permissions.class);
    verify(permissionsDAO, times(1)).createOrUpdate(aclCaptor.capture());
    Permissions newAcl = aclCaptor.getValue();
    // Cloned (not the same node)
    assertTrue(newAcl != previousAcl);
    // ...but same owner + readers
    assertSame(previousAcl.getOwner(), newAcl.getOwner());
    assertEquals(1, newAcl.getReader().size());
    assertSame(previousAcl.getReader().get(0), newAcl.getReader().get(0));
    assertSame(newAcl, saved.getPermissions());
  }

  @Test
  void createVersionMintsFreshAclWhenNoPriorVersion() {
    when(versionDAO.findMaxOrdinalByParent("01HF-NEW")).thenReturn(0);
    when(versionDAO.findLatestByParent("01HF-NEW")).thenReturn(Optional.empty());
    when(userService.getUserOptional("alice")).thenReturn(Optional.of(user("alice")));

    EntityVersion saved = service.createVersion("collection", "01HF-NEW", null, null, "alice");

    ArgumentCaptor<Permissions> aclCaptor = ArgumentCaptor.forClass(Permissions.class);
    verify(permissionsDAO, times(1)).createOrUpdate(aclCaptor.capture());
    Permissions mintedAcl = aclCaptor.getValue();
    assertEquals(PermissionType.Private, mintedAcl.getPermissionType());
    assertEquals("alice", mintedAcl.getOwner().getUsername());
    assertTrue(mintedAcl.getReader().isEmpty());
    assertTrue(mintedAcl.getWriter().isEmpty());
  }

  // ─── listVersions ─────────────────────────────────────────────────────

  @Test
  void listVersionsFiltersByPerVersionReadAcl() {
    EntityVersion v1 = newVersion("v1");
    Permissions p1 = new Permissions();
    p1.setOwner(user("bob"));
    p1.setReader(new ArrayList<>(List.of(user("alice"))));
    p1.setWriter(new ArrayList<>());
    p1.setManager(new ArrayList<>());
    p1.setReaderGroups(new ArrayList<>());
    p1.setWriterGroups(new ArrayList<>());
    p1.setPermissionType(PermissionType.Private);
    v1.setPermissions(p1);

    EntityVersion v2 = newVersion("v2");
    Permissions p2 = new Permissions();
    p2.setOwner(user("bob"));
    p2.setReader(new ArrayList<>());
    p2.setWriter(new ArrayList<>());
    p2.setManager(new ArrayList<>());
    p2.setReaderGroups(new ArrayList<>());
    p2.setWriterGroups(new ArrayList<>());
    p2.setPermissionType(PermissionType.Private);
    v2.setPermissions(p2);

    when(versionDAO.findAllByParent("01HF-A")).thenReturn(List.of(v2, v1));
    List<EntityVersion> visible = service.listVersions("collection", "01HF-A", "alice");
    assertEquals(1, visible.size());
    assertSame(v1, visible.get(0));
  }

  @Test
  void listVersionsRejectsBadKind() {
    EntityVersionException eve = assertThrows(EntityVersionException.class, () ->
      service.listVersions("bundle", "01HF-A", "alice")
    );
    assertEquals(EntityVersionException.Reason.KIND_UNSUPPORTED, eve.reason());
  }

  // ─── getVersion ───────────────────────────────────────────────────────

  @Test
  void getVersionReturnsRowWhenReader() {
    EntityVersion v = newVersion("v1");
    Permissions p = privateAclWith(user("bob"), List.of(user("alice")), List.of(), List.of());
    v.setPermissions(p);
    when(versionDAO.findByParentAndLabel("01HF-A", "v1")).thenReturn(Optional.of(v));
    EntityVersion out = service.getVersion("collection", "01HF-A", "v1", "alice");
    assertSame(v, out);
  }

  @Test
  void getVersion404WhenMissing() {
    when(versionDAO.findByParentAndLabel("01HF-A", "v9")).thenReturn(Optional.empty());
    EntityVersionException eve = assertThrows(EntityVersionException.class, () ->
      service.getVersion("collection", "01HF-A", "v9", "alice")
    );
    assertEquals(EntityVersionException.Reason.NOT_FOUND, eve.reason());
  }

  @Test
  void getVersion404WhenKindMismatch() {
    EntityVersion v = newVersion("v1");
    v.setParentEntityKind("data-object");
    when(versionDAO.findByParentAndLabel("01HF-A", "v1")).thenReturn(Optional.of(v));
    EntityVersionException eve = assertThrows(EntityVersionException.class, () ->
      service.getVersion("collection", "01HF-A", "v1", "alice")
    );
    assertEquals(EntityVersionException.Reason.NOT_FOUND, eve.reason());
  }

  @Test
  void getVersion403WhenNoRead() {
    EntityVersion v = newVersion("v1");
    Permissions p = privateAclWith(user("bob"), List.of(), List.of(), List.of());
    v.setPermissions(p);
    when(versionDAO.findByParentAndLabel("01HF-A", "v1")).thenReturn(Optional.of(v));
    EntityVersionException eve = assertThrows(EntityVersionException.class, () ->
      service.getVersion("collection", "01HF-A", "v1", "alice")
    );
    assertEquals(EntityVersionException.Reason.FORBIDDEN, eve.reason());
  }

  // ─── patchVersionPermissions ──────────────────────────────────────────

  @Test
  void patchPermissionsRequiresManager() {
    EntityVersion v = newVersion("v1");
    Permissions p = privateAclWith(user("bob"), List.of(user("alice")), List.of(), List.of());
    v.setPermissions(p);
    when(versionDAO.findByParentAndLabel("01HF-A", "v1")).thenReturn(Optional.of(v));
    PermissionsIO body = newPermissionsIO();
    EntityVersionException eve = assertThrows(EntityVersionException.class, () ->
      service.patchVersionPermissions("collection", "01HF-A", "v1", body, "alice")
    );
    assertEquals(EntityVersionException.Reason.FORBIDDEN, eve.reason());
  }

  @Test
  void patchPermissionsHappyPathAsManager() {
    EntityVersion v = newVersion("v1");
    Permissions p = privateAclWith(user("bob"), List.of(), List.of(), List.of(user("alice")));
    v.setPermissions(p);
    when(versionDAO.findByParentAndLabel("01HF-A", "v1")).thenReturn(Optional.of(v));
    PermissionsIO body = newPermissionsIO();
    body.setReader(new String[] { "carol" });
    when(userService.getUserOptional("carol")).thenReturn(Optional.of(user("carol")));
    Permissions updated = service.patchVersionPermissions("collection", "01HF-A", "v1", body, "alice");
    assertEquals(1, updated.getReader().size());
    assertEquals("carol", updated.getReader().get(0).getUsername());
  }

  @Test
  void patchPermissionsOwnerTransferRequiresCurrentOwner() {
    EntityVersion v = newVersion("v1");
    Permissions p = privateAclWith(user("bob"), List.of(), List.of(), List.of(user("alice")));
    v.setPermissions(p);
    when(versionDAO.findByParentAndLabel("01HF-A", "v1")).thenReturn(Optional.of(v));
    PermissionsIO body = newPermissionsIO();
    body.setOwner("carol");
    EntityVersionException eve = assertThrows(EntityVersionException.class, () ->
      service.patchVersionPermissions("collection", "01HF-A", "v1", body, "alice")
    );
    assertEquals(EntityVersionException.Reason.FORBIDDEN, eve.reason());
  }

  // ─── deleteVersion ────────────────────────────────────────────────────

  @Test
  void deleteVersionRefusesLastOne() {
    EntityVersion v = newVersion("v1");
    Permissions p = privateAclWith(user("bob"), List.of(), List.of(), List.of(user("alice")));
    v.setPermissions(p);
    when(versionDAO.findByParentAndLabel("01HF-A", "v1")).thenReturn(Optional.of(v));
    when(versionDAO.findAllByParent("01HF-A")).thenReturn(List.of(v));
    EntityVersionException eve = assertThrows(EntityVersionException.class, () ->
      service.deleteVersion("collection", "01HF-A", "v1", "alice")
    );
    assertEquals(EntityVersionException.Reason.CANNOT_DELETE_ONLY, eve.reason());
    verify(versionDAO, never()).delete(any(EntityVersion.class));
  }

  @Test
  void deleteVersionRequiresManager() {
    EntityVersion v = newVersion("v1");
    Permissions p = privateAclWith(user("bob"), List.of(user("alice")), List.of(), List.of());
    v.setPermissions(p);
    when(versionDAO.findByParentAndLabel("01HF-A", "v1")).thenReturn(Optional.of(v));
    EntityVersionException eve = assertThrows(EntityVersionException.class, () ->
      service.deleteVersion("collection", "01HF-A", "v1", "alice")
    );
    assertEquals(EntityVersionException.Reason.FORBIDDEN, eve.reason());
    verify(versionDAO, never()).delete(any(EntityVersion.class));
  }

  @Test
  void deleteVersionHappyPathAsManager() {
    EntityVersion v1 = newVersion("v1");
    EntityVersion v2 = newVersion("v2");
    Permissions p = privateAclWith(user("bob"), List.of(), List.of(), List.of(user("alice")));
    v2.setPermissions(p);
    when(versionDAO.findByParentAndLabel("01HF-A", "v2")).thenReturn(Optional.of(v2));
    when(versionDAO.findAllByParent("01HF-A")).thenReturn(List.of(v2, v1));
    service.deleteVersion("collection", "01HF-A", "v2", "alice");
    verify(versionDAO, times(1)).delete(v2);
  }

  // ─── label-shape validation ───────────────────────────────────────────

  @Test
  void labelValidationAcceptsLegalShapes() {
    EntityVersionService.validateLabelShape("v1");
    EntityVersionService.validateLabelShape("1.0.0");
    EntityVersionService.validateLabelShape("1.0.0-rc.1");
    EntityVersionService.validateLabelShape("release-march");
    EntityVersionService.validateLabelShape("v23");
  }

  @Test
  void labelValidationRejectsIllegalShapes() {
    assertThrows(EntityVersionException.class, () -> EntityVersionService.validateLabelShape(null));
    assertThrows(EntityVersionException.class, () -> EntityVersionService.validateLabelShape(""));
    assertThrows(EntityVersionException.class, () -> EntityVersionService.validateLabelShape("with space"));
    assertThrows(EntityVersionException.class, () -> EntityVersionService.validateLabelShape("with/slash"));
    assertThrows(EntityVersionException.class, () -> EntityVersionService.validateLabelShape("-leading-hyphen"));
    assertThrows(EntityVersionException.class, () -> EntityVersionService.validateLabelShape("1234"));
  }

  @Test
  void resolveKindFromSegmentMaps() {
    assertEquals("data-object", EntityVersionService.resolveKindFromSegment("data-objects"));
    assertEquals("collection", EntityVersionService.resolveKindFromSegment("collections"));
    assertEquals(null, EntityVersionService.resolveKindFromSegment("bundles"));
    assertEquals(null, EntityVersionService.resolveKindFromSegment(null));
  }

  // ─── helpers ─────────────────────────────────────────────────────────

  private EntityVersion newVersion(String label) {
    EntityVersion v = new EntityVersion();
    v.setVersionLabel(label);
    v.setVersionOrdinal(label.startsWith("v") ? Integer.parseInt(label.substring(1)) : 1);
    v.setParentEntityKind("collection");
    v.setParentEntityAppId("01HF-A");
    v.setAppId("v-app-" + label);
    v.setCreatedBy("bob");
    return v;
  }

  private Permissions privateAclWith(User owner, List<User> readers, List<User> writers, List<User> managers) {
    Permissions p = new Permissions();
    p.setOwner(owner);
    p.setPermissionType(PermissionType.Private);
    p.setReader(new ArrayList<>(readers));
    p.setWriter(new ArrayList<>(writers));
    p.setManager(new ArrayList<>(managers));
    p.setReaderGroups(new ArrayList<>());
    p.setWriterGroups(new ArrayList<>());
    return p;
  }

  private PermissionsIO newPermissionsIO() {
    PermissionsIO io = new PermissionsIO();
    io.setReader(new String[0]);
    io.setWriter(new String[0]);
    io.setManager(new String[0]);
    io.setReaderGroupIds(new long[0]);
    io.setWriterGroupIds(new long[0]);
    return io;
  }
}
