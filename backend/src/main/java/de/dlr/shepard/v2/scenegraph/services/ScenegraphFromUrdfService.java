package de.dlr.shepard.v2.scenegraph.services;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.identifier.AppIdGenerator;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.references.file.daos.FileBundleReferenceDAO;
import de.dlr.shepard.context.references.file.entities.FileReference;
import de.dlr.shepard.context.references.file.services.SingletonFileReferenceService;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.provenance.services.ProvenanceService;
import de.dlr.shepard.v2.annotations.daos.SemanticAnnotationV2DAO;
import de.dlr.shepard.v2.scenegraph.entities.CoordinateFrame;
import de.dlr.shepard.v2.scenegraph.entities.DigitalTwinScene;
import de.dlr.shepard.v2.scenegraph.entities.FrameKind;
import de.dlr.shepard.v2.scenegraph.entities.JointType;
import de.dlr.shepard.v2.scenegraph.io.CreateFrameRequestIO;
import de.dlr.shepard.v2.scenegraph.io.CreateJointRequestIO;
import de.dlr.shepard.v2.scenegraph.io.CreateSceneRequestIO;
import de.dlr.shepard.v2.scenegraph.services.SceneGraphService.ProvenanceContext;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * SCENEGRAPH-CREATE-FROM-URDF-1 — one-call mint of a {@code :DigitalTwinScene}
 * from a singleton {@link FileReference} carrying URDF XML.
 *
 * <p>Ports the parse-and-build logic from
 * {@code examples/mffd-rdk-urdf-showcase/scenegraph/build_mffd_scene.py}
 * into Java so the bootstrap stops requiring shell access. The shape:
 *
 * <ol>
 *   <li>Resolve the FR1b singleton via {@link SingletonFileReferenceService}.
 *       Reject {@link de.dlr.shepard.context.references.file.entities.FileBundleReference}
 *       appIds with 400 — the {@code reference-IS-the-data} contract
 *       only works for singletons.</li>
 *   <li>Idempotency check: if the FileReference already carries a
 *       {@code urn:shepard:scenegraph:scene-appId} annotation, throw
 *       {@link ExistingSceneException} so the caller can route to the
 *       existing scene rather than silently re-minting.</li>
 *   <li>Permission walk: caller needs {@code Write} on the parent
 *       Collection. Resolved via
 *       {@link PermissionsService#isAccessAllowedForDataObjectAppId}
 *       against the FileReference's parent DataObject appId.</li>
 *   <li>Stream the URDF bytes; parse with {@code javax.xml.parsers}
 *       (java stdlib — no new dependency); extract {@code <link>} +
 *       {@code <joint>} elements.</li>
 *   <li>Call {@link SceneGraphService#createScene} +
 *       {@link SceneGraphService#addFrame} +
 *       {@link SceneGraphService#addJoint} — the service layer owns
 *       the edge writes and the per-mutation Activity capture.</li>
 *   <li>Write the {@code urn:shepard:scenegraph:scene-appId} back-annotation
 *       on the FileReference so {@code OpenInSceneGraphButton.vue}
 *       discovers the scene on next render.</li>
 *   <li>Record one umbrella {@link de.dlr.shepard.provenance.entities.Activity}
 *       for the whole parse-and-build operation in addition to the
 *       per-mutation Activities the service layer mints.</li>
 * </ol>
 *
 * <p>The XML parser is configured with the OWASP secure-processing
 * defaults (no external entities, no DTD loading) — URDF is plain XML
 * and there is no legitimate need for either.
 */
@ApplicationScoped
public class ScenegraphFromUrdfService {

  /** Predicate IRI for the back-annotation on the URDF FileReference. */
  public static final String SCENE_APP_ID_PREDICATE = "urn:shepard:scenegraph:scene-appId";

  /** {@code subjectKind} used when stamping the back-annotation. */
  static final String SUBJECT_KIND_FILE_REFERENCE = "FileReference";

  @Inject SingletonFileReferenceService singletonService;
  @Inject FileBundleReferenceDAO fileBundleReferenceDAO;
  @Inject PermissionsService permissionsService;
  @Inject SceneGraphService sceneGraphService;
  @Inject SemanticAnnotationV2DAO annotationDAO;
  @Inject ProvenanceService provenanceService;

  /**
   * Thrown when the FileReference already carries a
   * {@link #SCENE_APP_ID_PREDICATE} back-annotation. Carries the
   * existing scene's appId so the REST layer can return 409 +
   * the existing pointer (the frontend then routes to it).
   */
  public static class ExistingSceneException extends RuntimeException {
    private final String existingSceneAppId;

    public ExistingSceneException(String existingSceneAppId) {
      super("scene already exists for this FileReference: " + existingSceneAppId);
      this.existingSceneAppId = existingSceneAppId;
    }

    public String getExistingSceneAppId() {
      return existingSceneAppId;
    }
  }

  /**
   * Mint a scene from the URDF carried by the given singleton FileReference.
   *
   * @param fileReferenceAppId UUID v7 of the singleton FileReference (FR1b).
   * @param name optional scene name override (falls back to URDF
   *   {@code <robot name="..."/>} attribute, then to the FileReference name).
   * @param description optional free-form description.
   * @param prov provenance context (carries caller + AI agent marker).
   * @param caller authenticated username (for the permission walk).
   * @return the newly-minted scene.
   * @throws NotFoundException when no FileReference matches the appId.
   * @throws BadRequestException when the appId resolves to a non-singleton
   *   bundle, or the URDF body is not valid XML / lacks a {@code <robot>} root.
   * @throws ForbiddenException when the caller lacks Write on the parent Collection.
   * @throws ExistingSceneException when the FileReference already has a scene-appId
   *   back-annotation (carries the existing scene's appId).
   */
  public DigitalTwinScene createFromUrdf(
    String fileReferenceAppId,
    String name,
    String description,
    ProvenanceContext prov,
    String caller
  ) {
    long startedAt = System.currentTimeMillis();
    if (fileReferenceAppId == null || fileReferenceAppId.isBlank()) {
      throw new BadRequestException("fileReferenceAppId is required");
    }

    // 1 — Resolve singleton; if missing, see whether the appId is a
    //     non-singleton bundle (400) or genuinely unknown (404).
    FileReference ref = singletonService.getByAppId(fileReferenceAppId);
    if (ref == null) {
      if (fileBundleReferenceDAO.findByAppId(fileReferenceAppId) != null) {
        throw new BadRequestException(
          "FileReference " + fileReferenceAppId + " is a multi-file FileBundleReference; "
            + "URDF mint requires a singleton FR1b. Re-upload via POST /v2/files."
        );
      }
      throw new NotFoundException("No FileReference with appId " + fileReferenceAppId);
    }
    if (ref.getDataObject() == null || ref.getDataObject().getAppId() == null) {
      // Graph inconsistency — treat as 404 (matches FileReferenceV2Rest).
      throw new NotFoundException("FileReference " + fileReferenceAppId + " has no parent DataObject");
    }

    // 2 — Permission walk: Write on the parent Collection (resolved via
    //     the DataObject→Collection cypher hop).
    String parentDoAppId = ref.getDataObject().getAppId();
    if (caller == null
        || !permissionsService.isAccessAllowedForDataObjectAppId(parentDoAppId, AccessType.Write, caller)) {
      throw new ForbiddenException(
        "caller lacks Write on the parent Collection of FileReference " + fileReferenceAppId
      );
    }

    // 3 — Idempotency: existing back-annotation → 409.
    String existing = findExistingSceneAppId(fileReferenceAppId);
    if (existing != null) {
      throw new ExistingSceneException(existing);
    }

    // 4 — Stream + parse URDF.
    UrdfModel model;
    try (InputStream is = singletonService.getPayload(fileReferenceAppId).getInputStream()) {
      model = parseUrdf(is);
    } catch (java.io.IOException ioe) {
      throw new BadRequestException("failed to read URDF stream: " + ioe.getMessage());
    } catch (SAXException | ParserConfigurationException xe) {
      throw new BadRequestException("invalid URDF XML: " + xe.getMessage());
    }
    if (model == null || model.links.isEmpty()) {
      throw new BadRequestException("URDF must contain a <robot> root with at least one <link>");
    }

    // 5 — Mint scene + frames + joints via the service layer (each call
    //     records its own per-mutation Activity).
    String resolvedName =
      (name != null && !name.isBlank())
        ? name
        : (model.robotName != null && !model.robotName.isBlank() ? model.robotName : ref.getName());
    CreateSceneRequestIO sceneBody = new CreateSceneRequestIO();
    sceneBody.setName(resolvedName);
    sceneBody.setDescription(description);
    sceneBody.setSourceFileAppId(fileReferenceAppId);
    DigitalTwinScene scene = sceneGraphService.createScene(sceneBody, prov);

    // Materialise frames: walk the joint chain so each child's parent is
    // already resolved. The kinematic root has no incoming joint.
    Map<String, String> linkToAppId = new HashMap<>();
    String rootLink = model.rootLink();
    CoordinateFrame rootFrame = sceneGraphService.addFrame(
      scene.getAppId(),
      buildFrameRequest(rootLink, null, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, frameKindFor(rootLink)),
      prov
    );
    linkToAppId.put(rootLink, rootFrame.getAppId());

    for (UrdfJoint j : model.joints) {
      String parentApp = linkToAppId.get(j.parent);
      if (parentApp == null) {
        Log.warnf("SCENEGRAPH-CREATE-FROM-URDF: skipping joint %s — parent link %s unresolved",
          j.name, j.parent);
        continue;
      }
      if (linkToAppId.containsKey(j.child)) continue;
      CoordinateFrame childFrame = sceneGraphService.addFrame(
        scene.getAppId(),
        buildFrameRequest(j.child, parentApp,
          j.originX, j.originY, j.originZ,
          j.originRoll, j.originPitch, j.originYaw,
          frameKindFor(j.child)),
        prov
      );
      linkToAppId.put(j.child, childFrame.getAppId());
    }
    for (UrdfJoint j : model.joints) {
      String parentApp = linkToAppId.get(j.parent);
      String childApp = linkToAppId.get(j.child);
      if (parentApp == null || childApp == null) continue;
      CreateJointRequestIO jb = new CreateJointRequestIO();
      jb.setName(j.name);
      jb.setParentFrameAppId(parentApp);
      jb.setChildFrameAppId(childApp);
      jb.setAxisX(j.axisX);
      jb.setAxisY(j.axisY);
      jb.setAxisZ(j.axisZ);
      JointType jt = URDF_JOINT_TYPE.getOrDefault(j.type, JointType.FIXED);
      jb.setType(jt);
      if (jt == JointType.REVOLUTE || jt == JointType.PRISMATIC) {
        jb.setLimitMin(j.limitLower);
        jb.setLimitMax(j.limitUpper);
      }
      try {
        sceneGraphService.addJoint(scene.getAppId(), jb, prov);
      } catch (RuntimeException re) {
        Log.warnf(re, "SCENEGRAPH-CREATE-FROM-URDF: addJoint failed for %s", j.name);
      }
    }

    // 6 — Back-annotation on the URDF FileReference. Direct DAO write —
    //     the v2 annotation REST surface would otherwise re-run the
    //     permission walk we just passed.
    writeSceneAppIdAnnotation(fileReferenceAppId, scene.getAppId(),
      prov == null ? null : prov.sourceMode(), caller);

    // 7 — Umbrella Activity for the whole parse-and-build (in addition to
    //     the per-mutation rows the service layer minted). Best-effort.
    try {
      provenanceService.record(
        "CREATE",
        "DigitalTwinScene",
        scene.getAppId(),
        caller,
        "POST /v2/scene-graphs/from-urdf/" + fileReferenceAppId
          + " — minted scene from URDF (" + model.links.size() + " links, "
          + model.joints.size() + " joints)",
        "POST",
        "v2/scene-graphs/from-urdf/" + fileReferenceAppId,
        201,
        startedAt,
        System.currentTimeMillis(),
        null,
        prov == null ? null : prov.sourceMode(),
        prov == null ? null : prov.agentId()
      );
    } catch (RuntimeException re) {
      Log.debugf(re, "SCENEGRAPH-CREATE-FROM-URDF: umbrella Activity capture skipped");
    }

    return scene;
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  private static CreateFrameRequestIO buildFrameRequest(
    String name, String parentAppId,
    double x, double y, double z, double rx, double ry, double rz,
    FrameKind kind
  ) {
    CreateFrameRequestIO fb = new CreateFrameRequestIO();
    fb.setName(name);
    fb.setParentFrameAppId(parentAppId);
    fb.setX(x);
    fb.setY(y);
    fb.setZ(z);
    fb.setRx(rx);
    fb.setRy(ry);
    fb.setRz(rz);
    fb.setKind(kind);
    return fb;
  }

  private static FrameKind frameKindFor(String linkName) {
    if (linkName == null) return FrameKind.FRAME;
    if ("base_link".equals(linkName)) return FrameKind.BASE;
    if ("tool0".equals(linkName) || "flange".equals(linkName)) return FrameKind.TCP;
    return FrameKind.FRAME;
  }

  private static final Map<String, JointType> URDF_JOINT_TYPE = Map.of(
    "revolute", JointType.REVOLUTE,
    "prismatic", JointType.PRISMATIC,
    "fixed", JointType.FIXED,
    "continuous", JointType.CONTINUOUS
  );

  /**
   * Look for an existing {@link #SCENE_APP_ID_PREDICATE} annotation on
   * the FileReference. Returns the existing scene appId from the
   * annotation's {@code valueName} (the literal field used by the
   * SEMA-V6 stack for plain-string objects), or {@code null} when none.
   */
  String findExistingSceneAppId(String fileReferenceAppId) {
    List<SemanticAnnotation> hits = annotationDAO.findFiltered(
      fileReferenceAppId, SUBJECT_KIND_FILE_REFERENCE,
      SCENE_APP_ID_PREDICATE, null, 0, 1
    );
    if (hits == null || hits.isEmpty()) return null;
    SemanticAnnotation hit = hits.get(0);
    String literal = hit.getValueName();
    return literal != null && !literal.isBlank() ? literal : null;
  }

  void writeSceneAppIdAnnotation(String fileReferenceAppId, String sceneAppId,
                                  String sourceMode, String caller) {
    try {
      SemanticAnnotation ann = new SemanticAnnotation();
      ann.setAppId(AppIdGenerator.next());
      ann.setSubjectAppId(fileReferenceAppId);
      ann.setSubjectKind(SUBJECT_KIND_FILE_REFERENCE);
      ann.setPropertyIRI(SCENE_APP_ID_PREDICATE);
      ann.setPropertyName("scene-appId");
      ann.setValueName(sceneAppId);
      ann.setSourceMode(sourceMode == null ? "human" : sourceMode);
      ann.setAgentUsername(caller);
      ann.setConfidence(1.0);
      annotationDAO.createOrUpdate(ann);
    } catch (RuntimeException re) {
      // Best-effort — the scene is already minted; a missing back-annotation
      // only degrades the UI's "open existing scene" detection.
      Log.warnf(re, "SCENEGRAPH-CREATE-FROM-URDF: back-annotation write failed for FileReference %s",
        fileReferenceAppId);
    }
  }

  // ── URDF parsing (java stdlib) ────────────────────────────────────────────

  /**
   * Minimal URDF model carrying only what we need to materialise a
   * {@code :DigitalTwinScene}: link names + joint endpoints + transforms +
   * axis + limits.
   */
  static final class UrdfModel {
    String robotName;
    final List<String> links = new ArrayList<>();
    final List<UrdfJoint> joints = new ArrayList<>();

    String rootLink() {
      // Root = link with no incoming joint.
      java.util.Set<String> children = new java.util.HashSet<>();
      for (UrdfJoint j : joints) children.add(j.child);
      for (String link : links) {
        if (!children.contains(link)) return link;
      }
      throw new BadRequestException("URDF has no root link (cycle?)");
    }
  }

  static final class UrdfJoint {
    String name;
    String type = "fixed";
    String parent;
    String child;
    double originX, originY, originZ;
    double originRoll, originPitch, originYaw;
    double axisX, axisY, axisZ = 1.0;
    Double limitLower;
    Double limitUpper;
  }

  /**
   * Stdlib XML → {@link UrdfModel}. URDF is plain XML; no xacro support.
   *
   * <p>Configures the {@link DocumentBuilderFactory} with OWASP secure
   * defaults (no external entities, no DTD).
   */
  static UrdfModel parseUrdf(InputStream is)
      throws ParserConfigurationException, SAXException, java.io.IOException {
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
    dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    dbf.setXIncludeAware(false);
    dbf.setExpandEntityReferences(false);
    DocumentBuilder db = dbf.newDocumentBuilder();
    Document doc = db.parse(is);
    Element root = doc.getDocumentElement();
    if (root == null || !"robot".equals(root.getNodeName())) {
      throw new BadRequestException("URDF root element must be <robot>");
    }
    UrdfModel model = new UrdfModel();
    model.robotName = root.getAttribute("name");

    NodeList linkEls = root.getElementsByTagName("link");
    for (int i = 0; i < linkEls.getLength(); i++) {
      Element el = (Element) linkEls.item(i);
      // Only direct children of <robot> count as kinematic links.
      if (el.getParentNode() != root) continue;
      String nm = el.getAttribute("name");
      if (nm != null && !nm.isBlank()) model.links.add(nm);
    }

    NodeList jointEls = root.getElementsByTagName("joint");
    for (int i = 0; i < jointEls.getLength(); i++) {
      Element el = (Element) jointEls.item(i);
      if (el.getParentNode() != root) continue;
      UrdfJoint j = new UrdfJoint();
      j.name = el.getAttribute("name");
      String t = el.getAttribute("type");
      if (t != null && !t.isBlank()) j.type = t;
      Element parent = firstChildElement(el, "parent");
      Element child = firstChildElement(el, "child");
      if (parent == null || child == null) continue;
      j.parent = parent.getAttribute("link");
      j.child = child.getAttribute("link");
      Element origin = firstChildElement(el, "origin");
      if (origin != null) {
        double[] xyz = parseTriple(origin.getAttribute("xyz"));
        double[] rpy = parseTriple(origin.getAttribute("rpy"));
        j.originX = xyz[0]; j.originY = xyz[1]; j.originZ = xyz[2];
        j.originRoll = rpy[0]; j.originPitch = rpy[1]; j.originYaw = rpy[2];
      }
      Element axis = firstChildElement(el, "axis");
      if (axis != null) {
        double[] a = parseTriple(axis.getAttribute("xyz"));
        j.axisX = a[0]; j.axisY = a[1]; j.axisZ = a[2];
      }
      Element limit = firstChildElement(el, "limit");
      if (limit != null) {
        String lo = limit.getAttribute("lower");
        String up = limit.getAttribute("upper");
        if (lo != null && !lo.isBlank()) {
          try { j.limitLower = Double.parseDouble(lo); } catch (NumberFormatException nfe) { /* ignore */ }
        }
        if (up != null && !up.isBlank()) {
          try { j.limitUpper = Double.parseDouble(up); } catch (NumberFormatException nfe) { /* ignore */ }
        }
      }
      model.joints.add(j);
    }
    return model;
  }

  private static Element firstChildElement(Element parent, String tag) {
    NodeList kids = parent.getChildNodes();
    for (int i = 0; i < kids.getLength(); i++) {
      Node n = kids.item(i);
      if (n.getNodeType() == Node.ELEMENT_NODE && tag.equals(n.getNodeName())) {
        return (Element) n;
      }
    }
    return null;
  }

  private static double[] parseTriple(String s) {
    if (s == null || s.isBlank()) return new double[] { 0.0, 0.0, 0.0 };
    String[] parts = s.trim().split("\\s+");
    if (parts.length != 3) return new double[] { 0.0, 0.0, 0.0 };
    try {
      return new double[] {
        Double.parseDouble(parts[0]),
        Double.parseDouble(parts[1]),
        Double.parseDouble(parts[2])
      };
    } catch (NumberFormatException nfe) {
      return new double[] { 0.0, 0.0, 0.0 };
    }
  }
}
