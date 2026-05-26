package de.dlr.shepard.v2.shapes.io;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Response body for {@code POST /v2/shapes/render}.
 *
 * <p><b>Beta scope (TPL2b):</b> VIEW_RECIPE templates only. Each
 * {@link ChannelBindingProjectionIO} carries the channel-selector
 * declared in the template body plus a {@code status} field.
 *
 * <p>In this beta release every binding is returned with
 * {@code status = "DECLARED"} and {@code resolved = null} — live
 * channel resolution against the focus DataObject's timeseries
 * references ships in a follow-on (TPL2c), once the TS-ID migration
 * (aidocs/platform/87) provides a stable single-key channel identity.
 * The frontend Trace3D plugin can use the {@code channelSelector}
 * values from each binding to make its own TimescaleDB channel queries.
 *
 * <p><b>Status vocabulary (for now; TPL2c extends it):</b>
 * <ul>
 *   <li>{@code DECLARED} — template declares this binding; live
 *       channel resolution was not attempted (beta).</li>
 *   <li>{@code OK} — channel found on the focus DataObject (future).</li>
 *   <li>{@code MISSING} — channel not found on the focus DataObject
 *       (future). A binding with {@code required = true} and
 *       {@code MISSING} means the renderer cannot operate.</li>
 *   <li>{@code UNIT_MISMATCH} — channel found but unit differs from
 *       the binding's declared {@code unit} IRI (future).</li>
 * </ul>
 */
@Schema(description = "VIEW_RECIPE render projection response.")
public record ShapesRenderResponseIO(
  @Schema(description = "Echoes the templateAppId from the request.")
  String templateAppId,

  @Schema(description = "Echoes the focusShepardId from the request.")
  String focusShepardId,

  @Schema(
    description = "Renderer hint from the template body (e.g. 'tresjs', 'echarts', 'plotly'). " +
    "The frontend uses this to select the correct Vue renderer component."
  )
  String renderer,

  @Schema(description = "Projected channel bindings — one entry per binding declared in the template.")
  List<ChannelBindingProjectionIO> channelBindings
) {

  /**
   * One channel binding from the VIEW_RECIPE template, projected onto
   * the response wire format.
   *
   * <p>In beta all bindings have {@code status = "DECLARED"} and
   * {@code resolved = null}. The frontend consumes {@code channelSelector}
   * directly to build its TS channel queries.
   */
  @Schema(description = "One channel-binding slot from the VIEW_RECIPE template.")
  public record ChannelBindingProjectionIO(
    @Schema(
      description = "Renderer-facing role name (x, y, z, time, color, size, …). " +
      "Conventions are per-renderer-family; Trace3D uses x/y/z/color."
    )
    String role,

    @Schema(
      description = "Channel selector as declared in the template. Today: JSON-encoded 5-tuple " +
      "{measurement, device, location, symbolicName, field}. Post TS-ID migration " +
      "(aidocs/platform/87): a single appId string."
    )
    String channelSelector,

    @Schema(
      description = "Expected QUDT unit IRI (e.g. 'http://qudt.org/vocab/unit/MilliM'). " +
      "Null when the template declares no unit expectation.",
      nullable = true
    )
    String unit,

    @Schema(
      description = "If true the renderer cannot operate when this binding is MISSING. " +
      "If false the renderer degrades gracefully (omits the dimension)."
    )
    boolean required,

    @Schema(
      description = "Resolution status: DECLARED (beta) | OK | MISSING | UNIT_MISMATCH. " +
      "In this beta release always DECLARED — live resolution ships in TPL2c."
    )
    String status,

    @Schema(
      description = "Resolved channel reference. Null in the beta release. " +
      "In TPL2c: non-null when status=OK, containing the TS reference appId.",
      nullable = true
    )
    ResolvedChannelIO resolved
  ) {}

  /**
   * Resolved channel reference, populated only when {@code status = OK}.
   * Null in the beta release.
   */
  @Schema(description = "Resolved timeseries channel reference. Null in beta.")
  public record ResolvedChannelIO(
    @Schema(description = "appId of the TimeseriesReference node whose channel list satisfies this binding.")
    String channelRef
  ) {}
}
