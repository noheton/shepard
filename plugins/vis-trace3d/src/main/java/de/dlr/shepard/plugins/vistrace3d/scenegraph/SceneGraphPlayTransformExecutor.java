package de.dlr.shepard.plugins.vistrace3d.scenegraph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.context.references.file.services.SingletonFileReferenceService;
import de.dlr.shepard.spi.transform.TransformException;
import de.dlr.shepard.spi.transform.TransformExecutor;
import de.dlr.shepard.spi.transform.TransformRequest;
import de.dlr.shepard.spi.transform.TransformResult;
import io.quarkus.logging.Log;
import jakarta.enterprise.inject.spi.CDI;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * V2CONV-B4 — the {@link TransformExecutor} that dissolves the bespoke
 * scene-graph subsystem into the generic MAPPING_RECIPE mechanism.
 *
 * <p>Claims the {@code SceneGraphPlayShape} IRI. A MAPPING_RECIPE template
 * targeting that shape binds a URDF FileReference (the kinematic tree — parsed
 * on demand here, never a stored {@code :DigitalTwinScene} graph), an optional
 * joint TimeseriesReference, and a channel→joint binding map. Materializing it
 * resolves + parses the URDF, reads the binding plan, and returns a
 * {@link TransformResult#view view} result — the "play envelope" (frame tree +
 * joint binding plan) the Trace3D-family renderer plays back.
 *
 * <p>This is the transform-direction sibling of the Trace3D VIEW_RECIPE
 * renderer. Where the bespoke {@code /v2/scene-graphs/from-urdf/{appId}}
 * endpoint materialised the URDF into a stored graph, this executor keeps the
 * URDF FileReference as the single source of truth and projects the kinematic
 * tree on demand — the converged shape mandated by aidocs/platform/191 §2.
 *
 * <h2>Why a ServiceLoader POJO + lazy CDI lookup</h2>
 *
 * <p>{@link TransformExecutor} is a plain ServiceLoader SPI (NOT a CDI bean) —
 * the registry {@code ServiceLoader.load}s it at startup. To resolve the URDF
 * bytes it needs the request-scoped {@link SingletonFileReferenceService}, so
 * it looks the bean up lazily via {@link CDI#current()} at
 * {@link #materialize} time (inside the request scope of the dispatching
 * {@code POST /v2/mappings/{templateAppId}/materialize} call). This mirrors how
 * other plugin SPI POJOs reach core services.
 *
 * <h2>Input resolution</h2>
 *
 * <p>The URDF FileReference appId is taken from the role-keyed
 * {@code inputReferenceAppIds} binding (role {@code urdfFileAppId}) when the
 * caller supplies it; otherwise it falls back to the template body's
 * {@code urdfFileReferenceAppId} field. This honours the "UI passes appIds, the
 * backend resolves" contract.
 */
public final class SceneGraphPlayTransformExecutor implements TransformExecutor {

  /** The MAPPING_RECIPE shape IRI this executor claims (the materialize dispatch key). */
  public static final String SCENE_GRAPH_PLAY_SHAPE_IRI =
    "http://semantics.dlr.de/shepard/transform#SceneGraphPlayShape";

  /** Binding role the caller uses for the URDF FileReference appId. */
  public static final String ROLE_URDF_FILE = "urdfFileAppId";

  /** Binding role the caller uses for the optional joint TimeseriesReference appId. */
  public static final String ROLE_JOINT_TS = "jointTimeseriesAppId";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Override
  public Set<String> supportedShapeIris() {
    return Set.of(SCENE_GRAPH_PLAY_SHAPE_IRI);
  }

  @Override
  public String name() {
    return "SceneGraphPlayTransformExecutor";
  }

  @Override
  public TransformResult materialize(TransformRequest req) {
    if (req == null) {
      throw new TransformException("transform.body.invalid", "request must not be null");
    }
    JsonNode body = parseBody(req.templateBodyJson());

    String urdfAppId = firstNonBlank(
      req.inputReferenceAppIds().get(ROLE_URDF_FILE),
      text(body, "urdfFileReferenceAppId")
    );
    if (urdfAppId == null) {
      throw new TransformException(
        "transform.input.missing",
        "scene-graph play requires a URDF FileReference appId (binding role '"
          + ROLE_URDF_FILE + "' or body.urdfFileReferenceAppId)"
      );
    }

    UrdfKinematics.UrdfModel model = resolveAndParseUrdf(urdfAppId);

    String jointTsAppId = firstNonBlank(
      req.inputReferenceAppIds().get(ROLE_JOINT_TS),
      text(body, "jointTimeseriesReferenceAppId")
    );
    List<Map<String, Object>> bindings = parseJointChannelBindings(body);

    Map<String, Object> envelope = buildPlayEnvelope(urdfAppId, jointTsAppId, model, bindings);
    return TransformResult.view(envelope, name());
  }

  // ─── URDF resolution + parse ─────────────────────────────────────────────────

  private UrdfKinematics.UrdfModel resolveAndParseUrdf(String urdfAppId) {
    SingletonFileReferenceService fileService;
    try {
      fileService = CDI.current().select(SingletonFileReferenceService.class).get();
    } catch (RuntimeException ex) {
      // No CDI context (e.g. a unit test that exercises the executor directly).
      throw new TransformException(
        "transform.internal-error",
        "scene-graph play executor could not resolve the FileReference service: " + ex.getMessage()
      );
    }
    if (fileService.getByAppId(urdfAppId) == null) {
      throw new TransformException(
        "transform.input.not-found",
        "no singleton FileReference with appId " + urdfAppId + " (URDF mint requires an FR1b singleton)"
      );
    }
    var payload = fileService.getPayload(urdfAppId);
    if (payload == null || payload.getInputStream() == null) {
      throw new TransformException(
        "transform.input.not-found",
        "FileReference " + urdfAppId + " has no resolvable payload bytes"
      );
    }
    try (InputStream is = payload.getInputStream()) {
      return UrdfKinematics.parse(is);
    } catch (UrdfKinematics.UrdfParseException pe) {
      throw new TransformException("transform.body.invalid", pe.getMessage());
    } catch (java.io.IOException ioe) {
      throw new TransformException("transform.input.not-found", "failed to read URDF stream: " + ioe.getMessage());
    }
  }

  // ─── Play-envelope construction ──────────────────────────────────────────────

  /**
   * Build the JSON-serialisable play envelope from the parsed kinematic tree.
   * Frame tree (one entry per link, parent resolved from joints) + joint list
   * (with transforms, axis, limits, type) + the channel→joint binding plan.
   */
  Map<String, Object> buildPlayEnvelope(
    String urdfAppId,
    String jointTsAppId,
    UrdfKinematics.UrdfModel model,
    List<Map<String, Object>> bindings
  ) {
    Map<String, Object> envelope = new LinkedHashMap<>();
    envelope.put("kind", "scene-graph-play");
    envelope.put("renderer", "urdf");
    envelope.put("robotName", model.robotName() == null ? "" : model.robotName());
    envelope.put("urdfFileReferenceAppId", urdfAppId);
    envelope.put("rootLink", model.rootLink());

    // Frame tree: parent of a link = the parent link of the joint whose child it is.
    Map<String, String> parentOf = new LinkedHashMap<>();
    for (UrdfKinematics.UrdfJoint j : model.joints()) {
      parentOf.put(j.childLink(), j.parentLink());
    }
    List<Map<String, Object>> frames = new ArrayList<>();
    for (UrdfKinematics.UrdfLink l : model.links()) {
      Map<String, Object> frame = new LinkedHashMap<>();
      frame.put("name", l.name());
      frame.put("parent", parentOf.get(l.name())); // null for root
      frames.add(frame);
    }
    envelope.put("frames", frames);

    List<Map<String, Object>> joints = new ArrayList<>();
    for (UrdfKinematics.UrdfJoint j : model.joints()) {
      Map<String, Object> jm = new LinkedHashMap<>();
      jm.put("name", j.name());
      jm.put("type", j.type());
      jm.put("parent", j.parentLink());
      jm.put("child", j.childLink());
      jm.put("origin", List.of(j.originX(), j.originY(), j.originZ()));
      jm.put("rpy", List.of(j.originRoll(), j.originPitch(), j.originYaw()));
      jm.put("axis", List.of(j.axisX(), j.axisY(), j.axisZ()));
      if (j.limitLower() != null) jm.put("limitLower", j.limitLower());
      if (j.limitUpper() != null) jm.put("limitUpper", j.limitUpper());
      joints.add(jm);
    }
    envelope.put("joints", joints);

    if (jointTsAppId != null) {
      envelope.put("jointTimeseriesReferenceAppId", jointTsAppId);
    }
    envelope.put("jointChannelBindings", bindings);
    // Live channel resolution is deferred (mirrors the Trace3D shape's
    // DECLARED posture — the resolver lands with VIS-S1 live channel reads);
    // the renderer plays the static pose until then.
    envelope.put("playbackStatus", jointTsAppId == null ? "STATIC_POSE" : "DECLARED");
    return envelope;
  }

  // ─── body parsing helpers ────────────────────────────────────────────────────

  private JsonNode parseBody(String bodyJson) {
    if (bodyJson == null || bodyJson.isBlank()) return MAPPER.createObjectNode();
    try {
      return MAPPER.readTree(bodyJson);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new TransformException("transform.body.invalid", "MAPPING_RECIPE body is not valid JSON: " + e.getMessage());
    }
  }

  List<Map<String, Object>> parseJointChannelBindings(JsonNode body) {
    JsonNode b = body.path("jointChannelBindings");
    if (b.isMissingNode() || b.isNull()) return List.of();
    try {
      // The field may be a JSON array directly, or a stringified JSON array.
      JsonNode arr = b.isTextual() ? MAPPER.readTree(b.asText()) : b;
      if (!arr.isArray()) return List.of();
      List<Map<String, Object>> out = new ArrayList<>();
      for (JsonNode entry : arr) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (entry.has("joint")) m.put("joint", entry.get("joint").asText());
        if (entry.has("channelSelector")) m.put("channelSelector", entry.get("channelSelector").asText());
        if (!m.isEmpty()) out.add(m);
      }
      return out;
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      Log.debugf("V2CONV-B4: jointChannelBindings not parseable — treating as empty: %s", e.getMessage());
      return List.of();
    }
  }

  private static String text(JsonNode node, String field) {
    JsonNode v = node.path(field);
    if (!v.isTextual()) return null;
    String s = v.asText();
    return s == null || s.isBlank() ? null : s;
  }

  private static String firstNonBlank(String a, String b) {
    if (a != null && !a.isBlank()) return a;
    if (b != null && !b.isBlank()) return b;
    return null;
  }
}
