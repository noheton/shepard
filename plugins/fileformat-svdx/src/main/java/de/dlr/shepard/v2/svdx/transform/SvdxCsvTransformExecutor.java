package de.dlr.shepard.v2.svdx.transform;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.spi.transform.TransformException;
import de.dlr.shepard.spi.transform.TransformExecutor;
import de.dlr.shepard.spi.transform.TransformRequest;
import de.dlr.shepard.spi.transform.TransformResult;
import de.dlr.shepard.v2.svdx.services.SvdxCsvIngestParams;
import de.dlr.shepard.v2.svdx.services.SvdxCsvIngestResult;
import de.dlr.shepard.v2.svdx.services.SvdxCsvIngestionService;
import jakarta.enterprise.inject.spi.CDI;
import java.util.Set;

/**
 * V2CONV-A7 — the {@link TransformExecutor} that dissolves the bespoke
 * {@code POST /v2/svdx/ingest} REST surface into the generic MAPPING_RECIPE
 * mechanism.
 *
 * <p>Claims the {@code SvdxCsvIngestShape} IRI. A MAPPING_RECIPE template
 * targeting that shape binds an SVDX {@code .svdx} FileReference + its TwinCAT
 * Scope Export {@code .csv} sibling FileReference (role-keyed, e.g.
 * {@code svdxFileAppId}/{@code csvFileAppId}) plus the target DataObject and an
 * optional TimeseriesContainer. Materializing it resolves the bound appIds,
 * delegates the unchanged CSV→TimescaleDB ingest to the (reused)
 * {@link SvdxCsvIngestionService}, and returns a
 * {@link TransformResult#reference reference} result carrying the derived
 * {@code TimeseriesReference} appId.
 *
 * <p>This is the exact sibling of the V2CONV-B5 {@code KrlTrajectoryTransformExecutor}
 * (KRL {@code .src} + URDF → derived TimeseriesReference) and the URScript
 * trajectory executor: a Tier-3 top-level {@code /v2/<x>} plugin namespace
 * converging onto {@code POST /v2/mappings/{templateAppId}/materialize}, the
 * shape mandated by aidocs/platform/191. Where the bespoke endpoint owned its own
 * REST resource + {@code SvdxIngestRequestIO}/{@code SvdxIngestResponseIO} IO
 * shapes, this executor flows through the generic SPI dispatch.
 *
 * <h2>Why a ServiceLoader POJO + lazy CDI lookup</h2>
 *
 * <p>{@link TransformExecutor} is a plain ServiceLoader SPI (NOT a CDI bean) — the
 * registry {@code ServiceLoader.load}s it at startup, so it has no injected
 * fields. To run the ingest it needs the request-scoped service collaborators
 * (file resolution, timeseries persistence, permission gate), so it resolves the
 * application-scoped {@link SvdxCsvIngestionService} lazily via
 * {@link CDI#current()} at {@link #materialize} time (inside the request scope of
 * the dispatching materialize call). This mirrors the KRL/URScript executors
 * exactly.
 *
 * <h2>Input resolution</h2>
 *
 * <p>The {@code .svdx} + {@code .csv} FileReference appIds are taken from the
 * role-keyed {@code inputReferenceAppIds} bindings ({@code svdxFileAppId} /
 * {@code csvFileAppId}) when the caller supplies them; otherwise they fall back to
 * the template body's {@code svdxFileReferenceAppId} /
 * {@code csvFileReferenceAppId} fields. The target DataObject + optional container
 * + optional reference name come from the template body. The deterministic
 * idempotency contract is unchanged (the {@code svdx-ingest:<svdxId>+<csvId>}
 * reference name in {@link SvdxCsvIngestionService}), so re-materialize is a no-op
 * replay.
 */
public final class SvdxCsvTransformExecutor implements TransformExecutor {

  /** The MAPPING_RECIPE shape IRI this executor claims (the materialize dispatch key). */
  public static final String SVDX_CSV_INGEST_SHAPE_IRI =
    "http://semantics.dlr.de/shepard/transform#SvdxCsvIngestShape";

  /** Binding role the caller uses for the .svdx FileReference appId. */
  public static final String ROLE_SVDX_FILE = "svdxFileAppId";

  /** Binding role the caller uses for the .csv FileReference appId. */
  public static final String ROLE_CSV_FILE = "csvFileAppId";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Override
  public Set<String> supportedShapeIris() {
    return Set.of(SVDX_CSV_INGEST_SHAPE_IRI);
  }

  @Override
  public String name() {
    return "SvdxCsvTransformExecutor";
  }

  @Override
  public TransformResult materialize(TransformRequest req) {
    if (req == null) {
      throw new TransformException("transform.body.invalid", "request must not be null");
    }
    JsonNode body = parseBody(req.templateBodyJson());

    String svdxAppId = firstNonBlank(
      req.inputReferenceAppIds().get(ROLE_SVDX_FILE),
      text(body, "svdxFileReferenceAppId")
    );
    if (svdxAppId == null) {
      throw new TransformException(
        "transform.input.missing",
        "SVDX CSV ingest requires a .svdx FileReference appId (binding role '"
          + ROLE_SVDX_FILE + "' or body.svdxFileReferenceAppId)"
      );
    }

    String csvAppId = firstNonBlank(
      req.inputReferenceAppIds().get(ROLE_CSV_FILE),
      text(body, "csvFileReferenceAppId")
    );
    if (csvAppId == null) {
      throw new TransformException(
        "transform.input.missing",
        "SVDX CSV ingest requires a .csv FileReference appId (binding role '"
          + ROLE_CSV_FILE + "' or body.csvFileReferenceAppId)"
      );
    }

    String targetDataObjectAppId = text(body, "targetDataObjectAppId");
    if (targetDataObjectAppId == null) {
      throw new TransformException(
        "transform.body.invalid",
        "SVDX CSV ingest recipe body must declare targetDataObjectAppId"
      );
    }

    // Optional — when absent, the service mints a fresh container in the Collection.
    String containerAppId = text(body, "timeseriesContainerAppId");
    // Optional — the service defaults to the deterministic idempotency name.
    String referenceName = text(body, "referenceName");

    SvdxCsvIngestParams params = new SvdxCsvIngestParams(
      svdxAppId,
      csvAppId,
      targetDataObjectAppId,
      containerAppId,
      referenceName
    );

    SvdxCsvIngestionService service = lookupService();
    try {
      SvdxCsvIngestResult result = service.ingest(params, req.invokerUsername());
      return TransformResult.reference(result.timeseriesReferenceAppId(), name());
    } catch (InvalidPathException notFound) {
      // 404-equivalent — DataObject or one of the FileReferences not found.
      throw new TransformException("transform.input.not-found", notFound.getMessage());
    } catch (InvalidBodyException badBody) {
      // 400/422-equivalent — files don't belong to the DataObject, CSV unparseable, etc.
      throw new TransformException("transform.input.missing", badBody.getMessage());
    } catch (SecurityException forbidden) {
      // No 403 transform code exists; the materialize endpoint already gates on
      // @RolesAllowed("authenticated"). A deeper Write-permission failure on the
      // target DataObject surfaces as a body.invalid (422) with the cause message.
      throw new TransformException(
        "transform.body.invalid",
        "caller lacks Write permission for the SVDX ingest: " + forbidden.getMessage()
      );
    }
  }

  // ─── service lookup ──────────────────────────────────────────────────────

  private SvdxCsvIngestionService lookupService() {
    try {
      return CDI.current().select(SvdxCsvIngestionService.class).get();
    } catch (RuntimeException ex) {
      throw new TransformException(
        "transform.internal-error",
        "SVDX CSV ingest executor could not resolve SvdxCsvIngestionService: " + ex.getMessage()
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
