package de.dlr.shepard.v2.transform.urscript;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.spi.transform.TransformException;
import de.dlr.shepard.spi.transform.TransformExecutor;
import de.dlr.shepard.spi.transform.TransformRequest;
import de.dlr.shepard.spi.transform.TransformResult;
import de.dlr.shepard.v2.transform.urscript.services.UrScriptTrajectoryParams;
import de.dlr.shepard.v2.transform.urscript.services.UrScriptTrajectoryService;
import jakarta.enterprise.inject.spi.CDI;
import java.util.Set;

/**
 * URSCRIPT-TRAJECTORY-1 — the {@link TransformExecutor} for the URScript
 * MAPPING_RECIPE transform shape.
 *
 * <p>Claims the {@code UrScriptTrajectoryShape} IRI. A MAPPING_RECIPE template
 * targeting that shape binds a URScript {@code .urscript}/{@code .script}
 * FileReference + a URDF FileReference + the target DataObject + the
 * TimeseriesContainer. Materializing it resolves the {@code .urscript} + URDF
 * bytes, invokes the URScript interpreter sidecar, persists the resulting joint
 * trajectory as a NEW {@code TimeseriesReference}, and returns a
 * {@link TransformResult#reference reference} result carrying that derived
 * reference's appId.
 *
 * <p>The UR-robot sibling of {@code KrlTrajectoryTransformExecutor}. Both dissolve
 * a robot-language interpret into the generic MAPPING_RECIPE mechanism
 * (aidocs/platform/191 §decision-2). The sole structural difference:
 * <ul>
 *   <li>No companion {@code .dat} files — URScript programs carry all state in a
 *       single source file.</li>
 *   <li>Binding role is {@code urscriptFileAppId} (not {@code srcFileAppId}).</li>
 *   <li>Default sidecar port is 8080 (not 8000).</li>
 * </ul>
 *
 * <h2>Why a ServiceLoader POJO + lazy CDI lookup</h2>
 *
 * <p>{@link TransformExecutor} is a plain ServiceLoader SPI — the registry
 * {@code ServiceLoader.load}s it at startup. To run the interpret it needs the
 * application-scoped {@link UrScriptTrajectoryService} collaborators, so it looks
 * the service up lazily via {@link CDI#current()} at {@link #materialize} time
 * (inside the request scope of the dispatching materialize call). This mirrors the
 * KRL and B4 executors exactly.
 *
 * <h2>Input resolution</h2>
 *
 * <p>The {@code .urscript} + URDF FileReference appIds are taken from the
 * role-keyed {@code inputReferenceAppIds} bindings ({@code urscriptFileAppId} /
 * {@code urdfFileAppId}) when the caller supplies them; otherwise they fall back to
 * the template body's {@code urscriptFileReferenceAppId} /
 * {@code urdfFileReferenceAppId} fields. The target DataObject + container come
 * from the template body.
 */
public final class UrScriptTrajectoryTransformExecutor implements TransformExecutor {

  /** The MAPPING_RECIPE shape IRI this executor claims (the materialize dispatch key). */
  public static final String URSCRIPT_TRAJECTORY_SHAPE_IRI =
    "http://semantics.dlr.de/shepard/transform#UrScriptTrajectoryShape";

  /** Binding role the caller uses for the URScript .urscript/.script FileReference appId. */
  public static final String ROLE_URSCRIPT_FILE = "urscriptFileAppId";

  /** Binding role the caller uses for the URDF FileReference appId. */
  public static final String ROLE_URDF_FILE = "urdfFileAppId";

  /** Header value the caller sets for AI-driven runs (EU AI Act Art. 50). */
  public static final String AGENT_KEY = "aiAgent";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Override
  public Set<String> supportedShapeIris() {
    return Set.of(URSCRIPT_TRAJECTORY_SHAPE_IRI);
  }

  @Override
  public String name() {
    return "UrScriptTrajectoryTransformExecutor";
  }

  @Override
  public TransformResult materialize(TransformRequest req) {
    if (req == null) {
      throw new TransformException("transform.body.invalid", "request must not be null");
    }
    JsonNode body = parseBody(req.templateBodyJson());

    String urscriptAppId = firstNonBlank(
      req.inputReferenceAppIds().get(ROLE_URSCRIPT_FILE),
      text(body, "urscriptFileReferenceAppId")
    );
    if (urscriptAppId == null) {
      throw new TransformException(
        "transform.input.missing",
        "URScript trajectory requires a .urscript/.script FileReference appId (binding role '"
          + ROLE_URSCRIPT_FILE + "' or body.urscriptFileReferenceAppId)"
      );
    }

    String urdfAppId = firstNonBlank(
      req.inputReferenceAppIds().get(ROLE_URDF_FILE),
      text(body, "urdfFileReferenceAppId")
    );
    if (urdfAppId == null) {
      throw new TransformException(
        "transform.input.missing",
        "URScript trajectory requires a URDF FileReference appId (binding role '"
          + ROLE_URDF_FILE + "' or body.urdfFileReferenceAppId)"
      );
    }

    String targetDataObjectAppId = text(body, "targetDataObjectAppId");
    if (targetDataObjectAppId == null) {
      throw new TransformException(
        "transform.body.invalid",
        "URScript trajectory recipe body must declare targetDataObjectAppId"
      );
    }

    String containerAppId = text(body, "timeseriesContainerAppId");
    if (containerAppId == null) {
      throw new TransformException(
        "transform.body.invalid",
        "URScript trajectory recipe body must declare timeseriesContainerAppId"
      );
    }

    UrScriptTrajectoryParams params = new UrScriptTrajectoryParams(
      req.templateAppId(),
      urscriptAppId,
      urdfAppId,
      targetDataObjectAppId,
      containerAppId
    );

    UrScriptTrajectoryService service = lookupService();
    try {
      String derivedRefAppId = service.interpret(params, req.invokerUsername(), text(body, AGENT_KEY));
      return TransformResult.reference(derivedRefAppId, name());
    } catch (UrScriptTrajectoryService.SidecarException ex) {
      // Sidecar unreachable / errored / timed out — a recoverable contract
      // failure the materialize dispatcher maps to 4xx. The operator opt-in
      // sidecar may simply not be running.
      throw new TransformException(
        "transform.body.invalid",
        "URScript interpreter sidecar call failed: " + ex.getMessage()
          + " — bring up the sidecar with COMPOSE_PROFILES=urscript-interpreter docker compose up -d"
      );
    } catch (jakarta.ws.rs.BadRequestException ex) {
      throw new TransformException("transform.input.missing", ex.getMessage());
    } catch (jakarta.ws.rs.NotFoundException ex) {
      throw new TransformException("transform.input.not-found", ex.getMessage());
    }
  }

  // ─── service lookup ──────────────────────────────────────────────────────

  private UrScriptTrajectoryService lookupService() {
    try {
      return CDI.current().select(UrScriptTrajectoryService.class).get();
    } catch (RuntimeException ex) {
      throw new TransformException(
        "transform.internal-error",
        "URScript trajectory executor could not resolve UrScriptTrajectoryService: " + ex.getMessage()
      );
    }
  }

  // ─── body parsing helpers ────────────────────────────────────────────────

  private JsonNode parseBody(String bodyJson) {
    if (bodyJson == null || bodyJson.isBlank()) return MAPPER.createObjectNode();
    try {
      return MAPPER.readTree(bodyJson);
    } catch (JsonProcessingException e) {
      throw new TransformException("transform.body.invalid", "MAPPING_RECIPE body is not valid JSON: " + e.getMessage());
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
