package de.dlr.shepard.plugins.spatial;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.spi.view.RenderRequest;
import de.dlr.shepard.spi.view.RenderResponse;
import de.dlr.shepard.spi.view.RenderResponse.ChannelBindingProjection;
import de.dlr.shepard.spi.view.RenderResponse.ResolvedChannel;
import de.dlr.shepard.spi.view.ViewRecipeRenderer;
import java.util.List;
import java.util.Set;

/**
 * SPATIAL-V6-004 — resolves a {@code shepard:BrushTraceShape} VIEW_RECIPE
 * against a focus DataObject by locating the SpatialDataContainer
 * referenced by the {@code traceSource} binding.
 *
 * <p>The template body must carry a {@code traceSourceAppId} key (the UUID v7
 * appId of the SpatialDataContainer). If present and non-blank, the renderer
 * returns a binding with status {@code "OK"}; if absent, it returns
 * {@code "MISSING"}.
 *
 * <p>This is a plain POJO (not a CDI bean) per the SPI contract. Discovery
 * happens via
 * {@code META-INF/services/de.dlr.shepard.spi.view.ViewRecipeRenderer}.
 *
 * <p>Cross-references:
 * <ul>
 *   <li>{@code aidocs/data/90-spatial-as-temporal-sweep.md §7} — BrushTraceShape SHACL spec</li>
 *   <li>SPATIAL-V6-004 in {@code aidocs/16-dispatcher-backlog.md}</li>
 * </ul>
 */
public class BrushTraceViewRecipeRenderer implements ViewRecipeRenderer {

  /** SHACL shape IRI for BrushTrace view recipes. */
  public static final String BRUSH_TRACE_SHAPE_IRI =
      "https://shepard.dlr.de/ontology/BrushTraceShape";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Override
  public Set<String> supportedShapeIris() {
    return Set.of(BRUSH_TRACE_SHAPE_IRI);
  }

  @Override
  public String name() {
    return "BrushTraceViewRecipeRenderer (SPATIAL-V6-004)";
  }

  /**
   * Resolves a BrushTraceShape template body against the focus DataObject.
   *
   * <p>For v0 this is a pure JSON parse — no DB lookup. The template body
   * is expected to carry {@code traceSourceAppId} (the UUID v7 of the
   * SpatialDataContainer). The frontend uses the resolved appId to call
   * {@code GET /v2/spatial-containers/{appId}/trace}.
   *
   * @param req the dispatch request; never null
   * @return a response with one {@code "traceSource"} binding
   */
  @Override
  public RenderResponse render(RenderRequest req) {
    String traceSourceAppId = extractTraceSourceAppId(req.templateBodyJson());

    ChannelBindingProjection traceBinding;
    if (traceSourceAppId != null && !traceSourceAppId.isBlank()) {
      traceBinding = new ChannelBindingProjection(
          "traceSource",
          "traceSourceAppId",
          null,
          true,
          "OK",
          new ResolvedChannel(traceSourceAppId)
      );
    } else {
      traceBinding = new ChannelBindingProjection(
          "traceSource",
          "traceSourceAppId",
          null,
          true,
          "MISSING",
          null
      );
    }

    return new RenderResponse(
        req.templateAppId(),
        req.focusShepardId(),
        "brush-trace",
        List.of(traceBinding)
    );
  }

  /**
   * Extracts the {@code traceSourceAppId} value from the template body JSON.
   *
   * @param json raw template body JSON; may be null or blank
   * @return the appId string, or {@code null} if not found / parse error
   */
  String extractTraceSourceAppId(String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      var node = MAPPER.readTree(json);
      var v = node.get("traceSourceAppId");
      return (v != null && !v.isNull()) ? v.asText() : null;
    } catch (Exception e) {
      return null;
    }
  }
}
