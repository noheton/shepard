package de.dlr.shepard.spi.view;

import java.util.List;

/**
 * VIS-S1 — internal SPI response shape for
 * {@link ViewRecipeRenderer#render(RenderRequest)}.
 *
 * <p>Mirrors the wire-shape
 * {@link de.dlr.shepard.v2.shapes.io.ShapesRenderResponseIO} but lives
 * in the SPI package as a POJO record (no JSON/OpenAPI annotations).
 * The dispatcher maps this to the wire IO before returning to the
 * caller, preserving the {@code status} vocabulary
 * (DECLARED / OK / MISSING / UNIT_MISMATCH) byte-for-byte.
 *
 * <p>The {@code renderer} field carries the frontend-component hint
 * (e.g. {@code "tresjs"}, {@code "echarts-gl"}) — the same string the
 * template body declared. A renderer may override it (e.g. to upgrade
 * a tresjs hint to {@code "isaac-usd"} when a sidecar is available)
 * but typically just echoes what the template asked for.
 *
 * @param templateAppId    echo of the request's template appId
 * @param focusShepardId   echo of the request's focus DataObject appId
 * @param renderer         frontend renderer hint (e.g. {@code "tresjs"})
 * @param channelBindings  one entry per declared channel binding;
 *                         renderers populate {@link
 *                         ChannelBindingProjection#status()} with
 *                         OK / MISSING / UNIT_MISMATCH (DECLARED is
 *                         reserved for the in-tree fallback path)
 */
public record RenderResponse(
  String templateAppId,
  String focusShepardId,
  String renderer,
  List<ChannelBindingProjection> channelBindings
) {
  /**
   * One projected channel binding. Mirrors
   * {@link de.dlr.shepard.v2.shapes.io.ShapesRenderResponseIO.ChannelBindingProjectionIO}
   * field-for-field.
   *
   * <p>The {@code status} vocabulary:
   * <ul>
   *   <li>{@code DECLARED} — the in-tree fallback path emits this
   *       when no renderer is registered for the template's shape
   *       IRI. A plugin-side renderer SHOULD emit one of the live
   *       codes below.</li>
   *   <li>{@code OK} — channel found on the focus DataObject;
   *       {@link #resolved()} carries the TS reference appId.</li>
   *   <li>{@code MISSING} — channel not found. A binding with
   *       {@code required=true} and {@code MISSING} status means the
   *       renderer cannot operate.</li>
   *   <li>{@code UNIT_MISMATCH} — channel found but its unit IRI
   *       differs from the binding's declared {@code unit}. The
   *       renderer may degrade gracefully or refuse — its call.</li>
   * </ul>
   *
   * @param role             renderer-facing role name (x, y, z, time,
   *                         color, …)
   * @param channelSelector  selector as declared in the template body
   * @param unit             expected QUDT unit IRI, or null
   * @param required         whether the renderer needs this binding
   *                         to operate
   * @param status           DECLARED / OK / MISSING / UNIT_MISMATCH
   * @param resolved         resolved channel reference when
   *                         {@code status == "OK"}, otherwise null
   */
  public record ChannelBindingProjection(
    String role,
    String channelSelector,
    String unit,
    boolean required,
    String status,
    ResolvedChannel resolved
  ) {}

  /**
   * Resolved channel reference, populated only when
   * {@code status == "OK"}. Mirrors
   * {@link de.dlr.shepard.v2.shapes.io.ShapesRenderResponseIO.ResolvedChannelIO}.
   *
   * @param channelRef appId of the resolved TimeseriesReference node
   */
  public record ResolvedChannel(String channelRef) {}
}
