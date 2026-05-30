package de.dlr.shepard.v2.scenegraph.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.mongoDB.NamedInputStream;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.file.daos.FileBundleReferenceDAO;
import de.dlr.shepard.context.references.file.entities.FileBundleReference;
import de.dlr.shepard.context.references.file.entities.FileReference;
import de.dlr.shepard.context.references.file.services.SingletonFileReferenceService;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.provenance.services.ProvenanceService;
import de.dlr.shepard.v2.annotations.daos.SemanticAnnotationV2DAO;
import de.dlr.shepard.v2.scenegraph.entities.CoordinateFrame;
import de.dlr.shepard.v2.scenegraph.entities.DigitalTwinScene;
import de.dlr.shepard.v2.scenegraph.entities.Joint;
import de.dlr.shepard.v2.scenegraph.io.CreateFrameRequestIO;
import de.dlr.shepard.v2.scenegraph.io.CreateJointRequestIO;
import de.dlr.shepard.v2.scenegraph.io.CreateSceneRequestIO;
import de.dlr.shepard.v2.scenegraph.services.SceneGraphService.ProvenanceContext;
import de.dlr.shepard.v2.scenegraph.services.ScenegraphFromUrdfService.ExistingSceneException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * SCENEGRAPH-CREATE-FROM-URDF-1 — unit tests for
 * {@link ScenegraphFromUrdfService}.
 *
 * <p>Plain Mockito; the URDF parser is exercised over an inline two-link /
 * one-joint sample so the focus stays on the orchestration shape (resolve →
 * permission → idempotency → parse → service-layer minting → back-annotation
 * → umbrella Activity).
 */
class ScenegraphFromUrdfServiceTest {

  private static final String FILE_REF_APP_ID = "file-ref-app";
  private static final String PARENT_DO_APP_ID = "parent-do-app";
  private static final String CALLER = "alice";

  private static final String SAMPLE_URDF =
    "<robot name=\"two_link\">" +
      "<link name=\"base_link\"/>" +
      "<link name=\"tool0\"/>" +
      "<joint name=\"j1\" type=\"revolute\">" +
        "<parent link=\"base_link\"/>" +
        "<child link=\"tool0\"/>" +
        "<origin xyz=\"0.1 0.2 0.3\" rpy=\"0.0 0.0 0.4\"/>" +
        "<axis xyz=\"0 0 1\"/>" +
        "<limit lower=\"-3.14\" upper=\"3.14\"/>" +
      "</joint>" +
    "</robot>";

  private ScenegraphFromUrdfService service;
  private SingletonFileReferenceService singletonService;
  private FileBundleReferenceDAO bundleDAO;
  private PermissionsService permissions;
  private SceneGraphService sceneGraphService;
  private SemanticAnnotationV2DAO annotationDAO;
  private ProvenanceService provenanceService;

  @BeforeEach
  void setUp() {
    service = new ScenegraphFromUrdfService();
    singletonService = mock(SingletonFileReferenceService.class);
    bundleDAO = mock(FileBundleReferenceDAO.class);
    permissions = mock(PermissionsService.class);
    sceneGraphService = mock(SceneGraphService.class);
    annotationDAO = mock(SemanticAnnotationV2DAO.class);
    provenanceService = mock(ProvenanceService.class);
    service.singletonService = singletonService;
    service.fileBundleReferenceDAO = bundleDAO;
    service.permissionsService = permissions;
    service.sceneGraphService = sceneGraphService;
    service.annotationDAO = annotationDAO;
    service.provenanceService = provenanceService;
  }

  // ── happy path ───────────────────────────────────────────────────────────

  @Test
  void mintsSceneFromValidUrdf_andStampsBackAnnotation() {
    FileReference singleton = singletonWithParent(PARENT_DO_APP_ID);
    when(singletonService.getByAppId(FILE_REF_APP_ID)).thenReturn(singleton);
    when(permissions.isAccessAllowedForDataObjectAppId(PARENT_DO_APP_ID, AccessType.Write, CALLER))
      .thenReturn(true);
    when(annotationDAO.findFiltered(eq(FILE_REF_APP_ID), anyString(), anyString(), any(), anyInt(), anyInt()))
      .thenReturn(List.of());
    when(singletonService.getPayload(FILE_REF_APP_ID))
      .thenReturn(new NamedInputStream("oid", new ByteArrayInputStream(SAMPLE_URDF.getBytes()),
        "kr.urdf", (long) SAMPLE_URDF.length()));

    DigitalTwinScene scene = new DigitalTwinScene();
    scene.setAppId("scene-app");
    scene.setName("two_link");
    when(sceneGraphService.createScene(any(CreateSceneRequestIO.class), any(ProvenanceContext.class)))
      .thenReturn(scene);
    AtomicInteger frameCounter = new AtomicInteger();
    when(sceneGraphService.addFrame(eq("scene-app"), any(CreateFrameRequestIO.class), any(ProvenanceContext.class)))
      .thenAnswer(inv -> {
        CoordinateFrame f = new CoordinateFrame();
        f.setAppId("frame-" + frameCounter.incrementAndGet());
        return f;
      });
    when(sceneGraphService.addJoint(eq("scene-app"), any(CreateJointRequestIO.class), any(ProvenanceContext.class)))
      .thenReturn(new Joint());

    DigitalTwinScene minted = service.createFromUrdf(
      FILE_REF_APP_ID, null, null, ProvenanceContext.human(CALLER), CALLER
    );

    assertThat(minted.getAppId()).isEqualTo("scene-app");

    // Scene was created with the URDF robot name + sourceFileAppId pointing
    // back at the URDF FileReference.
    ArgumentCaptor<CreateSceneRequestIO> sceneCap = ArgumentCaptor.forClass(CreateSceneRequestIO.class);
    verify(sceneGraphService).createScene(sceneCap.capture(), any(ProvenanceContext.class));
    assertThat(sceneCap.getValue().getName()).isEqualTo("two_link");
    assertThat(sceneCap.getValue().getSourceFileAppId()).isEqualTo(FILE_REF_APP_ID);

    // One frame per link (2) + one joint.
    verify(sceneGraphService, atLeastOnce())
      .addFrame(eq("scene-app"), any(CreateFrameRequestIO.class), any(ProvenanceContext.class));
    verify(sceneGraphService).addJoint(eq("scene-app"), any(CreateJointRequestIO.class), any(ProvenanceContext.class));

    // Back-annotation written on the FileReference.
    ArgumentCaptor<SemanticAnnotation> annCap = ArgumentCaptor.forClass(SemanticAnnotation.class);
    verify(annotationDAO).createOrUpdate(annCap.capture());
    SemanticAnnotation ann = annCap.getValue();
    assertThat(ann.getSubjectAppId()).isEqualTo(FILE_REF_APP_ID);
    assertThat(ann.getSubjectKind()).isEqualTo("FileReference");
    assertThat(ann.getPropertyIRI()).isEqualTo(ScenegraphFromUrdfService.SCENE_APP_ID_PREDICATE);
    assertThat(ann.getValueName()).isEqualTo("scene-app");
    assertThat(ann.getAgentUsername()).isEqualTo(CALLER);

    // Umbrella Activity recorded.
    verify(provenanceService).record(
      eq("CREATE"), eq("DigitalTwinScene"), eq("scene-app"), eq(CALLER),
      anyString(), eq("POST"), anyString(), eq(201), anyLong(), anyLong(),
      any(), any(), any()
    );
  }

  @Test
  void mintsSceneFromValidUrdf_withCallerProvidedName_overridesRobotName() {
    setupHappy();
    service.createFromUrdf(FILE_REF_APP_ID, "Custom Name", "Desc", ProvenanceContext.human(CALLER), CALLER);

    ArgumentCaptor<CreateSceneRequestIO> sceneCap = ArgumentCaptor.forClass(CreateSceneRequestIO.class);
    verify(sceneGraphService).createScene(sceneCap.capture(), any(ProvenanceContext.class));
    assertThat(sceneCap.getValue().getName()).isEqualTo("Custom Name");
    assertThat(sceneCap.getValue().getDescription()).isEqualTo("Desc");
  }

  // ── idempotency ───────────────────────────────────────────────────────────

  @Test
  void idempotent_existingAnnotationThrowsExistingSceneException() {
    FileReference singleton = singletonWithParent(PARENT_DO_APP_ID);
    when(singletonService.getByAppId(FILE_REF_APP_ID)).thenReturn(singleton);
    when(permissions.isAccessAllowedForDataObjectAppId(PARENT_DO_APP_ID, AccessType.Write, CALLER))
      .thenReturn(true);
    SemanticAnnotation existing = new SemanticAnnotation();
    existing.setValueName("existing-scene-app");
    when(annotationDAO.findFiltered(eq(FILE_REF_APP_ID), anyString(),
        eq(ScenegraphFromUrdfService.SCENE_APP_ID_PREDICATE), any(), anyInt(), anyInt()))
      .thenReturn(List.of(existing));

    ExistingSceneException thrown = assertThrows(
      ExistingSceneException.class,
      () -> service.createFromUrdf(FILE_REF_APP_ID, null, null, ProvenanceContext.human(CALLER), CALLER)
    );
    assertThat(thrown.getExistingSceneAppId()).isEqualTo("existing-scene-app");
    // No scene created, no payload streamed.
    verify(sceneGraphService, never()).createScene(any(), any());
    verify(singletonService, never()).getPayload(anyString());
  }

  // ── error branches ────────────────────────────────────────────────────────

  @Test
  void bundleAppIdRejectedAs400() {
    when(singletonService.getByAppId(FILE_REF_APP_ID)).thenReturn(null);
    when(bundleDAO.findByAppId(FILE_REF_APP_ID)).thenReturn(new FileBundleReference());

    assertThrows(
      BadRequestException.class,
      () -> service.createFromUrdf(FILE_REF_APP_ID, null, null, ProvenanceContext.human(CALLER), CALLER)
    );
  }

  @Test
  void missingFileReferenceIs404() {
    when(singletonService.getByAppId(FILE_REF_APP_ID)).thenReturn(null);
    when(bundleDAO.findByAppId(FILE_REF_APP_ID)).thenReturn(null);

    assertThrows(
      NotFoundException.class,
      () -> service.createFromUrdf(FILE_REF_APP_ID, null, null, ProvenanceContext.human(CALLER), CALLER)
    );
  }

  @Test
  void missingDataObjectOnFileReferenceIs404() {
    FileReference orphan = new FileReference();
    orphan.setName("orphan");
    when(singletonService.getByAppId(FILE_REF_APP_ID)).thenReturn(orphan);

    assertThrows(
      NotFoundException.class,
      () -> service.createFromUrdf(FILE_REF_APP_ID, null, null, ProvenanceContext.human(CALLER), CALLER)
    );
  }

  @Test
  void callerWithoutWritePermissionIs403() {
    FileReference singleton = singletonWithParent(PARENT_DO_APP_ID);
    when(singletonService.getByAppId(FILE_REF_APP_ID)).thenReturn(singleton);
    when(permissions.isAccessAllowedForDataObjectAppId(PARENT_DO_APP_ID, AccessType.Write, CALLER))
      .thenReturn(false);

    assertThrows(
      ForbiddenException.class,
      () -> service.createFromUrdf(FILE_REF_APP_ID, null, null, ProvenanceContext.human(CALLER), CALLER)
    );
  }

  @Test
  void invalidUrdfXmlIs400() {
    FileReference singleton = singletonWithParent(PARENT_DO_APP_ID);
    when(singletonService.getByAppId(FILE_REF_APP_ID)).thenReturn(singleton);
    when(permissions.isAccessAllowedForDataObjectAppId(PARENT_DO_APP_ID, AccessType.Write, CALLER))
      .thenReturn(true);
    when(annotationDAO.findFiltered(eq(FILE_REF_APP_ID), anyString(), anyString(), any(), anyInt(), anyInt()))
      .thenReturn(List.of());
    String junk = "<not><valid>xml";
    when(singletonService.getPayload(FILE_REF_APP_ID))
      .thenReturn(new NamedInputStream("oid", new ByteArrayInputStream(junk.getBytes()),
        "bad.urdf", (long) junk.length()));

    assertThrows(
      BadRequestException.class,
      () -> service.createFromUrdf(FILE_REF_APP_ID, null, null, ProvenanceContext.human(CALLER), CALLER)
    );
  }

  @Test
  void xmlWithoutRobotRootIs400() {
    FileReference singleton = singletonWithParent(PARENT_DO_APP_ID);
    when(singletonService.getByAppId(FILE_REF_APP_ID)).thenReturn(singleton);
    when(permissions.isAccessAllowedForDataObjectAppId(PARENT_DO_APP_ID, AccessType.Write, CALLER))
      .thenReturn(true);
    when(annotationDAO.findFiltered(eq(FILE_REF_APP_ID), anyString(), anyString(), any(), anyInt(), anyInt()))
      .thenReturn(List.of());
    String nonRobot = "<root><link name=\"a\"/></root>";
    when(singletonService.getPayload(FILE_REF_APP_ID))
      .thenReturn(new NamedInputStream("oid", new ByteArrayInputStream(nonRobot.getBytes()),
        "x.xml", (long) nonRobot.length()));

    assertThrows(
      BadRequestException.class,
      () -> service.createFromUrdf(FILE_REF_APP_ID, null, null, ProvenanceContext.human(CALLER), CALLER)
    );
  }

  // ── parser edge cases ─────────────────────────────────────────────────────

  @Test
  void parser_extractsLinksAndJointsCorrectly() throws Exception {
    ScenegraphFromUrdfService.UrdfModel model =
      ScenegraphFromUrdfService.parseUrdf(new ByteArrayInputStream(SAMPLE_URDF.getBytes()));
    assertThat(model.robotName).isEqualTo("two_link");
    assertThat(model.links).containsExactly("base_link", "tool0");
    assertThat(model.joints).hasSize(1);
    ScenegraphFromUrdfService.UrdfJoint j = model.joints.get(0);
    assertThat(j.name).isEqualTo("j1");
    assertThat(j.type).isEqualTo("revolute");
    assertThat(j.parent).isEqualTo("base_link");
    assertThat(j.child).isEqualTo("tool0");
    assertThat(j.originX).isEqualTo(0.1);
    assertThat(j.originY).isEqualTo(0.2);
    assertThat(j.originZ).isEqualTo(0.3);
    assertThat(j.originYaw).isEqualTo(0.4);
    assertThat(j.axisZ).isEqualTo(1.0);
    assertThat(j.limitLower).isEqualTo(-3.14);
    assertThat(j.limitUpper).isEqualTo(3.14);
    assertThat(model.rootLink()).isEqualTo("base_link");
  }

  @Test
  void parser_rejectsDoctypeForXxeSafety() {
    String evil =
      "<?xml version=\"1.0\"?>" +
      "<!DOCTYPE robot [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>" +
      "<robot name=\"a\"><link name=\"l\"/></robot>";
    assertThrows(
      Exception.class,
      () -> ScenegraphFromUrdfService.parseUrdf(new ByteArrayInputStream(evil.getBytes()))
    );
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private FileReference singletonWithParent(String parentAppId) {
    DataObject parent = new DataObject();
    parent.setAppId(parentAppId);
    FileReference ref = new FileReference();
    ref.setName("kr-urdf");
    ref.setDataObject(parent);
    return ref;
  }

  private void setupHappy() {
    FileReference singleton = singletonWithParent(PARENT_DO_APP_ID);
    when(singletonService.getByAppId(FILE_REF_APP_ID)).thenReturn(singleton);
    when(permissions.isAccessAllowedForDataObjectAppId(PARENT_DO_APP_ID, AccessType.Write, CALLER))
      .thenReturn(true);
    when(annotationDAO.findFiltered(eq(FILE_REF_APP_ID), anyString(), anyString(), any(), anyInt(), anyInt()))
      .thenReturn(List.of());
    when(singletonService.getPayload(FILE_REF_APP_ID))
      .thenReturn(new NamedInputStream("oid", new ByteArrayInputStream(SAMPLE_URDF.getBytes()),
        "kr.urdf", (long) SAMPLE_URDF.length()));
    DigitalTwinScene scene = new DigitalTwinScene();
    scene.setAppId("scene-app");
    when(sceneGraphService.createScene(any(CreateSceneRequestIO.class), any(ProvenanceContext.class)))
      .thenReturn(scene);
    when(sceneGraphService.addFrame(eq("scene-app"), any(CreateFrameRequestIO.class), any(ProvenanceContext.class)))
      .thenAnswer(inv -> {
        CoordinateFrame f = new CoordinateFrame();
        f.setAppId("frame-x");
        return f;
      });
    when(sceneGraphService.addJoint(eq("scene-app"), any(CreateJointRequestIO.class), any(ProvenanceContext.class)))
      .thenReturn(new Joint());
  }
}
