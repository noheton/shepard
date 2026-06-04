package de.dlr.shepard.v2.transform.krl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.spi.transform.TransformException;
import de.dlr.shepard.spi.transform.TransformExecutor;
import de.dlr.shepard.spi.transform.TransformRequest;
import de.dlr.shepard.spi.transform.TransformResult;
import de.dlr.shepard.v2.transform.krl.services.KrlTrajectoryParams;
import de.dlr.shepard.v2.transform.krl.services.KrlTrajectoryService;
import io.quarkus.logging.Log;
import jakarta.enterprise.inject.spi.CDI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * V2CONV-B5 — the {@link TransformExecutor} that dissolves the bespoke KRL
 * interpret subsystem into the generic MAPPING_RECIPE mechanism.
 *
 * <p>Claims the {@code KrlTrajectoryShape} IRI. A MAPPING_RECIPE template
 * targeting that shape binds a KRL {@code .src}/{@code .krl} FileReference + a
 * URDF FileReference + the target DataObject + the TimeseriesContainer.
 * Materializing it resolves the {@code .src} + URDF bytes, invokes the
 * (reused, relocated) KRL interpreter sidecar, persists the resulting joint
 * trajectory as a NEW {@code TimeseriesReference}, and returns a
 * {@link TransformResult#reference reference} result carrying that derived
 * reference's appId.
 *
 * <p>This is the REFERENCE-direction sibling of the V2CONV-B4
 * {@code SceneGraphPlayTransformExecutor} (which returns a VIEW). Where the
 * bespoke {@code POST /v2/krl/interpret} endpoint owned its own REST surface,
 * IO shapes, and {@code :KrlInterpretActivity} entity, this executor flows
 * through the generic {@code POST /v2/mappings/{templateAppId}/materialize}
 * dispatch — the converged shape mandated by aidocs/platform/191 §2.
 *
 * <h2>Why a ServiceLoader POJO + lazy CDI lookup</h2>
 *
 * <p>{@link TransformExecutor} is a plain ServiceLoader SPI (NOT a CDI bean) —
 * the registry {@code ServiceLoader.load}s it at startup. To run the interpret
 * it needs the request-scoped service collaborators (file resolution,
 * timeseries persistence, provenance), so it looks the application-scoped
 * {@link KrlTrajectoryService} up lazily via {@link CDI#current()} at
 * {@link #materialize} time (inside the request scope of the dispatching
 * materialize call). This mirrors the B4 executor exactly.
 *
 * <h2>Input resolution</h2>
 *
 * <p>The {@code .src} + URDF FileReference appIds are taken from the role-keyed
 * {@code inputReferenceAppIds} bindings ({@code srcFileAppId} /
 * {@code urdfFileAppId}) when the caller supplies them; otherwise they fall back
 * to the template body's {@code srcFileReferenceAppId} /
 * {@code urdfFileReferenceAppId} fields. The target DataObject + container +
 * companion .dat appIds come from the template body.
 */
public final class KrlTrajectoryTransformExecutor implements TransformExecutor {

  /** The MAPPING_RECIPE shape IRI this executor claims (the materialize dispatch key). */
  public static final String KRL_TRAJECTORY_SHAPE_IRI =
    "http://semantics.dlr.de/shepard/transform#KrlTrajectoryShape";

  /** Binding role the caller uses for the KRL .src/.krl FileReference appId. */
  public static final String ROLE_SRC_FILE = "srcFileAppId";

  /** Binding role the caller uses for the URDF FileReference appId. */
  public static final String ROLE_URDF_FILE = "urdfFileAppId";

  /** Header value the caller sets for AI-driven runs (EU AI Act Art. 50). */
  public static final String AGENT_KEY = "aiAgent";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Override
  public Set<String> supportedShapeIris() {
    return Set.of(KRL_TRAJECTORY_SHAPE_IRI);
  }

  @Override
  public String name() {
    return "KrlTrajectoryTransformExecutor";
  }

  @Override
  public TransformResult materialize(TransformRequest req) {
    if (req == null) {
      throw new TransformException("transform.body.invalid", "request must not be null");
    }
    JsonNode body = parseBody(req.templateBodyJson());

    String srcAppId = firstNonBlank(
      req.inputReferenceAppIds().get(ROLE_SRC_FILE),
      text(body, "srcFileReferenceAppId")
    );
    if (srcAppId == null) {
      throw new TransformException(
        "transform.input.missing",
        "KRL trajectory requires a .src/.krl FileReference appId (binding role '"
          + ROLE_SRC_FILE + "' or body.srcFileReferenceAppId)"
      );
    }

    String urdfAppId = firstNonBlank(
      req.inputReferenceAppIds().get(ROLE_URDF_FILE),
      text(body, "urdfFileReferenceAppId")
    );
    if (urdfAppId == null) {
      throw new TransformException(
        "transform.input.missing",
        "KRL trajectory requires a URDF FileReference appId (binding role '"
          + ROLE_URDF_FILE + "' or body.urdfFileReferenceAppId)"
      );
    }

    String targetDataObjectAppId = text(body, "targetDataObjectAppId");
    if (targetDataObjectAppId == null) {
      throw new TransformException(
        "transform.body.invalid",
        "KRL trajectory recipe body must declare targetDataObjectAppId"
      );
    }

    String containerAppId = text(body, "timeseriesContainerAppId");
    if (containerAppId == null) {
      throw new TransformException(
        "transform.body.invalid",
        "KRL trajectory recipe body must declare timeseriesContainerAppId"
      );
    }

    KrlTrajectoryParams params = new KrlTrajectoryParams(
      req.templateAppId(),
      srcAppId,
      urdfAppId,
      targetDataObjectAppId,
      containerAppId,
      parseDatAppIds(body)
    );

    KrlTrajectoryService service = lookupService();
    try {
      String derivedRefAppId = service.interpret(params, req.invokerUsername(), text(body, AGENT_KEY));
      return TransformResult.reference(derivedRefAppId, name());
    } catch (KrlTrajectoryService.SidecarException ex) {
      // Sidecar unreachable / errored / timed out — a recoverable contract
      // failure the materialize dispatcher maps to 4xx. The operator opt-in
      // sidecar may simply not be running.
      throw new TransformException(
        "transform.body.invalid",
        "KRL interpreter sidecar call failed: " + ex.getMessage()
          + " — bring up the sidecar with COMPOSE_PROFILES=krl-interpreter docker compose up -d"
      );
    } catch (jakarta.ws.rs.BadRequestException ex) {
      throw new TransformException("transform.input.missing", ex.getMessage());
    } catch (jakarta.ws.rs.NotFoundException ex) {
      throw new TransformException("transform.input.not-found", ex.getMessage());
    }
  }

  // ─── service lookup ──────────────────────────────────────────────────────

  private KrlTrajectoryService lookupService() {
    try {
      return CDI.current().select(KrlTrajectoryService.class).get();
    } catch (RuntimeException ex) {
      throw new TransformException(
        "transform.internal-error",
        "KRL trajectory executor could not resolve KrlTrajectoryService: " + ex.getMessage()
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

  List<String> parseDatAppIds(JsonNode body) {
    JsonNode b = body.path("datFileReferenceAppIds");
    if (b.isMissingNode() || b.isNull()) return List.of();
    try {
      JsonNode arr = b.isTextual() ? MAPPER.readTree(b.asText()) : b;
      if (!arr.isArray()) return List.of();
      List<String> out = new ArrayList<>();
      for (JsonNode entry : arr) {
        if (entry.isTextual()) {
          String v = entry.asText();
          if (v != null && !v.isBlank()) out.add(v);
        }
      }
      return out;
    } catch (JsonProcessingException e) {
      Log.debugf("V2CONV-B5: datFileReferenceAppIds not parseable — treating as empty: %s", e.getMessage());
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
