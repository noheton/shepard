package de.dlr.shepard.spi.transform;

import java.util.List;
import java.util.Map;

/**
 * V2CONV-B3 — internal SPI request shape for
 * {@link TransformExecutor#materialize(TransformRequest)}.
 *
 * <p>Sibling of the {@link de.dlr.shepard.spi.view.RenderRequest VIEW_RECIPE}
 * request, but for the {@code MAPPING_RECIPE} transform direction: a
 * {@code MAPPING_RECIPE} {@link de.dlr.shepard.template.entities.ShepardTemplate}
 * binds existing input reference {@code appId}s to a <em>derived</em> output
 * (a new reference appId, or a played/rendered view-model / media). Lives in
 * the SPI package — POJO record, no JSON/OpenAPI annotations — so plugin
 * authors (e.g. the future KRL sidecar executor) can build against it without
 * pulling MicroProfile or Jackson on the implementation side.
 *
 * <p>{@link #shapeIri()} is the dispatch key (the {@code mappingRecipeShape}
 * IRI declared in the template body) but is echoed on the envelope so a
 * multi-shape executor knows which shape fired this call without re-parsing
 * the body.
 *
 * <p>The {@link #inputReferenceAppIds()} are resolved server-side from the
 * request (never paths/URLs — the UI passes reference {@code appId}s only, per
 * the CLAUDE.md "UI never asks for paths/URLs" rule); the dispatcher hands the
 * executor the validated appId list keyed by the binding role declared in the
 * template (e.g. {@code "srcFileAppId"} → its appId, {@code "urdfFileAppId"} →
 * its appId).
 *
 * @param templateAppId        appId of the MAPPING_RECIPE template
 * @param shapeIri             the SHACL shape IRI the template targeted —
 *                             also the dispatch key, echoed here for
 *                             multi-shape executors
 * @param inputReferenceAppIds the resolved input reference appIds keyed by the
 *                             binding role (e.g. {@code srcFileAppId},
 *                             {@code urdfFileAppId}); never null, may be empty
 * @param invokerUsername      username of the caller driving the materialize —
 *                             used by the executor for provenance attribution;
 *                             may be null in non-authenticated test contexts
 * @param templateBodyJson     the raw JSON body of the template — the executor
 *                             parses out its own knobs. Stays as a String so
 *                             the SPI doesn't pull a JSON-parser dependency.
 */
public record TransformRequest(
  String templateAppId,
  String shapeIri,
  Map<String, String> inputReferenceAppIds,
  String invokerUsername,
  String templateBodyJson
) {
  public TransformRequest {
    inputReferenceAppIds = inputReferenceAppIds == null ? Map.of() : Map.copyOf(inputReferenceAppIds);
  }

  /**
   * Convenience: the input reference appIds as a flat list, in no particular
   * order. Executors that don't care about binding roles (e.g. the built-in
   * no-op) can use this instead of {@link #inputReferenceAppIds()}.
   *
   * @return the input reference appId values; never null, may be empty
   */
  public List<String> inputReferenceAppIdValues() {
    return List.copyOf(inputReferenceAppIds.values());
  }
}
