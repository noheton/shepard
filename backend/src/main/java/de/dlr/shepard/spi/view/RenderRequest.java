package de.dlr.shepard.spi.view;

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
 * @param templateAppId   appId of the VIEW_RECIPE template
 * @param focusShepardId  appId of the focus DataObject being projected
 * @param shapeIri        the SHACL shape IRI the template targeted —
 *                        already the dispatch key but echoed here for
 *                        multi-shape renderers
 * @param templateBodyJson the raw JSON body of the template — the
 *                        renderer parses out its own knobs
 *                        (channelBindings, colorMap, alignment, …).
 *                        Stays as a String so the SPI doesn't pull a
 *                        JSON-parser dependency.
 */
public record RenderRequest(
  String templateAppId,
  String focusShepardId,
  String shapeIri,
  String templateBodyJson
) {}
