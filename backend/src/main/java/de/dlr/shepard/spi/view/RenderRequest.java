package de.dlr.shepard.spi.view;

import java.util.Map;

/**
 * VIS-S1 — internal SPI request shape for
 * {@link ViewRecipeRenderer#render(RenderRequest)}.
 *
 * <p>Mirrors the wire-shape
 * {@link de.dlr.shepard.v2.shapes.io.ShapesRenderRequestIO} but lives
 * in the SPI package — POJO record, no JSON/OpenAPI annotations — so
 * plugin authors can build against it without pulling MicroProfile or
 * Jackson on the implementation side. The dispatcher constructs the
 * request from the validated wire body + the loaded
 * {@link de.dlr.shepard.template.entities.ShepardTemplate} and passes
 * it to the resolved renderer.
 *
 * <p>{@link #shapeIri()} is included on the request envelope (in
 * addition to being the dispatch key) because a renderer that handles
 * multiple shape IRIs needs to know which one fired this call without
 * re-parsing the template body.
 *
 * <h2>V2CONV-A1b additions (additive — pre-existing 4-arg callers
 * compile unchanged via the {@linkplain #RenderRequest(String, String,
 * String, String) legacy constructor})</h2>
 *
 * <ul>
 *   <li><b>(E1) {@link #params()}</b> — per-call render knobs
 *       ({@code frame}, {@code channel}, {@code mode}, …) the OTvis
 *       viewer and other byte-rooted renderers need. Never null (empty
 *       map for the template-rooted Trace3D path).</li>
 *   <li><b>(E2) {@link #focusFileRefAppId()}</b> — appId of the focus
 *       {@code FileReference} for a <i>file-rooted</i> render (no stored
 *       VIEW_RECIPE template). Null on the template-rooted path.</li>
 *   <li><b>(E3) {@link #payloadResolver()}</b> — a focus-resolution seam
 *       the dispatcher hands the renderer so it can read the focus
 *       FileReference's bytes without itself being a CDI bean (renderers
 *       are ServiceLoader POJOs). Null when the dispatcher has no
 *       resolver wired (e.g. unit tests); renderers must null-check.</li>
 * </ul>
 *
 * @param templateAppId   appId of the VIEW_RECIPE template; null on a
 *                        file-rooted render (E2)
 * @param focusShepardId  appId of the focus DataObject being projected
 * @param shapeIri        the SHACL shape IRI the template targeted —
 *                        already the dispatch key but echoed here for
 *                        multi-shape renderers
 * @param templateBodyJson the raw JSON body of the template — the
 *                        renderer parses out its own knobs
 *                        (channelBindings, colorMap, alignment, …).
 *                        Stays as a String so the SPI doesn't pull a
 *                        JSON-parser dependency. Null/empty on a
 *                        file-rooted render.
 * @param params          per-call render knobs (E1); never null
 * @param focusFileRefAppId appId of the focus FileReference for a
 *                        file-rooted render (E2); null on the
 *                        template-rooted path
 * @param payloadResolver focus-byte resolution seam (E3); may be null
 */
public record RenderRequest(
  String templateAppId,
  String focusShepardId,
  String shapeIri,
  String templateBodyJson,
  Map<String, String> params,
  String focusFileRefAppId,
  FocusPayloadResolver payloadResolver
) {
  /**
   * Canonical constructor — normalises {@link #params()} to a
   * non-null, unmodifiable map so renderers never have to null-check it.
   */
  public RenderRequest {
    params = params == null ? Map.of() : Map.copyOf(params);
  }

  /**
   * V2CONV-A1b — legacy template-rooted constructor. Preserves the
   * pre-A1b 4-arg shape so every existing renderer + test compiles
   * unchanged: empty params, no file-root, no payload resolver.
   *
   * @param templateAppId    appId of the VIEW_RECIPE template
   * @param focusShepardId   appId of the focus DataObject
   * @param shapeIri         the dispatch shape IRI
   * @param templateBodyJson the raw template JSON body
   */
  public RenderRequest(String templateAppId, String focusShepardId, String shapeIri, String templateBodyJson) {
    this(templateAppId, focusShepardId, shapeIri, templateBodyJson, Map.of(), null, null);
  }

  /**
   * Convenience accessor — the value of a per-call param, or null when
   * absent. Avoids a {@code params().get(...)} null-dereference at every
   * renderer call site.
   *
   * @param key the param key (e.g. {@code "frame"}, {@code "channel"})
   * @return the value or null
   */
  public String param(String key) {
    return params == null ? null : params.get(key);
  }
}
