package de.dlr.shepard.v2.scenegraph.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.file.entities.FileReference;
import de.dlr.shepard.context.references.file.services.SingletonFileReferenceService;
import de.dlr.shepard.v2.scenegraph.entities.DigitalTwinScene;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * SCENEGRAPH-PERMS-1 — unit tests for {@link SceneGraphPermissionService}.
 *
 * <p>All edges fail closed: missing scene, missing source-file, missing
 * FileReference, missing parent DataObject, blank caller. Hand-built scenes
 * (no {@code sourceFileAppId}) admit only the {@code instance-admin} role.
 */
class SceneGraphPermissionServiceTest {

  private static final String SCENE = "01900000-0000-7000-8000-0000000000a1";
  private static final String FILE_APP = "01900000-0000-7000-8000-0000000000b1";
  private static final String DO_APP = "01900000-0000-7000-8000-0000000000c1";
  private static final String CALLER = "alice";

  private SceneGraphPermissionService svc;
  private SceneGraphService sceneGraphService;
  private SingletonFileReferenceService singletonService;
  private PermissionsService permissionsService;

  @BeforeEach
  void setUp() {
    svc = new SceneGraphPermissionService();
    sceneGraphService = mock(SceneGraphService.class);
    singletonService = mock(SingletonFileReferenceService.class);
    permissionsService = mock(PermissionsService.class);
    svc.sceneGraphService = sceneGraphService;
    svc.singletonFileReferenceService = singletonService;
    svc.permissionsService = permissionsService;
  }

  // ── isAllowed (read/edit-time walk) ─────────────────────────────────────

  @Test
  void isAllowed_returnsTrue_whenCollectionGrantsAccess() {
    primeScene(SCENE, FILE_APP);
    primeFileRef(FILE_APP, DO_APP);
    when(permissionsService.isAccessAllowedForDataObjectAppId(DO_APP, AccessType.Read, CALLER))
      .thenReturn(true);

    assertThat(svc.isAllowed(SCENE, AccessType.Read, CALLER, false)).isTrue();
  }

  @Test
  void isAllowed_returnsFalse_whenCollectionDenies() {
    primeScene(SCENE, FILE_APP);
    primeFileRef(FILE_APP, DO_APP);
    when(permissionsService.isAccessAllowedForDataObjectAppId(DO_APP, AccessType.Write, CALLER))
      .thenReturn(false);

    assertThat(svc.isAllowed(SCENE, AccessType.Write, CALLER, false)).isFalse();
  }

  @Test
  void isAllowed_returnsFalse_whenSceneMissing() {
    when(sceneGraphService.findScene(SCENE)).thenReturn(null);
    assertThat(svc.isAllowed(SCENE, AccessType.Read, CALLER, false)).isFalse();
    verify(permissionsService, never()).isAccessAllowedForDataObjectAppId(any(), any(), any());
  }

  @Test
  void isAllowed_returnsFalse_whenFileReferenceMissing() {
    primeScene(SCENE, FILE_APP);
    when(singletonService.getByAppId(FILE_APP)).thenReturn(null);
    assertThat(svc.isAllowed(SCENE, AccessType.Read, CALLER, false)).isFalse();
  }

  @Test
  void isAllowed_returnsFalse_whenFileReferenceHasNoParentDataObject() {
    primeScene(SCENE, FILE_APP);
    FileReference ref = new FileReference();
    ref.setAppId(FILE_APP);
    // No DataObject set.
    when(singletonService.getByAppId(FILE_APP)).thenReturn(ref);
    assertThat(svc.isAllowed(SCENE, AccessType.Read, CALLER, false)).isFalse();
  }

  @Test
  void isAllowed_handBuiltScene_failsClosed_forNonAdmin() {
    DigitalTwinScene scene = new DigitalTwinScene();
    scene.setAppId(SCENE);
    scene.setSourceFileAppId(null);
    when(sceneGraphService.findScene(SCENE)).thenReturn(scene);
    assertThat(svc.isAllowed(SCENE, AccessType.Read, CALLER, false)).isFalse();
    verify(permissionsService, never()).isAccessAllowedForDataObjectAppId(any(), any(), any());
  }

  @Test
  void isAllowed_handBuiltScene_admitsAdmin() {
    DigitalTwinScene scene = new DigitalTwinScene();
    scene.setAppId(SCENE);
    scene.setSourceFileAppId("   ");  // blank counts as null
    when(sceneGraphService.findScene(SCENE)).thenReturn(scene);
    assertThat(svc.isAllowed(SCENE, AccessType.Write, CALLER, true)).isTrue();
  }

  @Test
  void isAllowed_returnsFalse_forBlankInputs() {
    assertThat(svc.isAllowed(null, AccessType.Read, CALLER, false)).isFalse();
    assertThat(svc.isAllowed("", AccessType.Read, CALLER, false)).isFalse();
    assertThat(svc.isAllowed(SCENE, AccessType.Read, null, false)).isFalse();
    assertThat(svc.isAllowed(SCENE, AccessType.Read, "", false)).isFalse();
  }

  // ── canCreateFromSourceFile ─────────────────────────────────────────────

  @Test
  void canCreateFromSourceFile_returnsTrue_whenCollectionGrantsWrite() {
    primeFileRef(FILE_APP, DO_APP);
    when(permissionsService.isAccessAllowedForDataObjectAppId(DO_APP, AccessType.Write, CALLER))
      .thenReturn(true);
    assertThat(svc.canCreateFromSourceFile(FILE_APP, CALLER)).isTrue();
  }

  @Test
  void canCreateFromSourceFile_returnsFalse_whenFileMissing() {
    when(singletonService.getByAppId(FILE_APP)).thenReturn(null);
    assertThat(svc.canCreateFromSourceFile(FILE_APP, CALLER)).isFalse();
  }

  @Test
  void canCreateFromSourceFile_returnsFalse_forBlankInputs() {
    assertThat(svc.canCreateFromSourceFile(null, CALLER)).isFalse();
    assertThat(svc.canCreateFromSourceFile(FILE_APP, null)).isFalse();
  }

  // ── helpers ─────────────────────────────────────────────────────────────

  private void primeScene(String sceneAppId, String sourceFileAppId) {
    DigitalTwinScene scene = new DigitalTwinScene();
    scene.setAppId(sceneAppId);
    scene.setSourceFileAppId(sourceFileAppId);
    when(sceneGraphService.findScene(sceneAppId)).thenReturn(scene);
  }

  private void primeFileRef(String fileAppId, String parentDoAppId) {
    FileReference ref = new FileReference();
    ref.setAppId(fileAppId);
    DataObject parent = new DataObject();
    parent.setAppId(parentDoAppId);
    ref.setDataObject(parent);
    when(singletonService.getByAppId(fileAppId)).thenReturn(ref);
  }
}
