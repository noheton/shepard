package de.dlr.shepard.spi.view;

import java.util.Set;

/**
 * VIS-S1 — SPI contract for a backend resolver that turns a
 * {@code VIEW_RECIPE} {@link de.dlr.shepard.template.entities.ShepardTemplate}
 * (and a focus DataObject {@code appId}) into a renderer-agnostic
 * {@link RenderResponse} envelope.
 *
 * <p>Implementations are plain POJOs registered through
 * {@code META-INF/services/de.dlr.shepard.spi.view.ViewRecipeRenderer}
 * — NOT CDI beans. Discovery happens via
 * {@link java.util.ServiceLoader#load(Class)} in
 * {@link ViewRecipeRendererRegistry} at startup. This matches the
 * {@link de.dlr.shepard.spi.payload.PayloadKind PayloadKind} P-series
 * SPI shape and sidesteps the Quarkus {@code @Inject ServiceLoader}
 * dance (no {@code quarkus.arc.unremovable-types} entries needed).
 *
 * <h2>Dispatch key — the shape IRI</h2>
 *
 * <p>Each {@code VIEW_RECIPE} template body carries a
 * {@code viewRecipeShape} field naming the SHACL {@code sh:NodeShape}
 * it targets, e.g.
 * {@code "http://semantics.dlr.de/shepard-ui/trace3d#Trace3DViewShape"}.
 * The {@link ViewRecipeRendererRegistry#resolve(String)} lookup is
 * keyed by that IRI. {@link #supportedShapeIris()} returns the set
 * this renderer claims; the registry refuses to start when two
 * renderers claim the same IRI (fail-fast on build-time conflict).
 *
 * <p>The {@code renderer} field in the template body (e.g.
 * {@code "tresjs"}) is a frontend-component hint, not the dispatch
 * key. Multiple shapes can share a renderer hint; only the shape IRI
 * uniquely names the backend resolver. Don't conflate them.
 *
 * <h2>Failure posture</h2>
 *
 * <p>A renderer's {@link #render(RenderRequest)} method should return
 * a {@link RenderResponse} with per-binding {@code status} codes
 * (OK / MISSING / UNIT_MISMATCH) rather than throw. Throw only for
 * unrecoverable conditions (DAO failure, malformed body the dispatcher
 * already guarded against). The dispatcher catches
 * {@link RuntimeException}, logs at WARN, and surfaces an HTTP 500
 * with a clear RFC 7807 body to the caller.
 *
 * <h2>Cross-references</h2>
 *
 * <ul>
 *   <li>{@code aidocs/16} — VIS-S1 row</li>
 *   <li>{@code aidocs/semantics/98 §1.2} — render endpoint contract</li>
 *   <li>{@code aidocs/agent-findings/trace3d-spike.md §3} — frame
 *       envelope contract (mirrored in {@link RenderResponse})</li>
 * </ul>
 *
 * @see ViewRecipeRendererRegistry
 * @see RenderRequest
 * @see RenderResponse
 */
public interface ViewRecipeRenderer {
  /**
   * SHACL {@code sh:NodeShape} IRIs this renderer claims. The
   * registry indexes implementations by the union of these returns.
   *
   * <p>Each IRI must be globally unique across the discovered
   * renderers — duplicate registration is a fail-fast condition at
   * registry startup, with a clear error naming both registrants.
   *
   * <p>An implementation may declare multiple IRIs to handle a
   * family of shapes (e.g. the future {@code vis-trace3d} module
   * may register both {@code trace3d:Trace3DViewShape} and
   * {@code mesh:MeshViewShape} under one renderer class). An empty
   * return is legal but disables registration — the renderer is
   * effectively dormant.
   *
   * @return the set of shape IRIs this renderer handles; never null
   */
  Set<String> supportedShapeIris();

  /**
   * Project the template body onto the focus DataObject and return
   * the renderer-agnostic envelope. The envelope shape mirrors the
   * {@link de.dlr.shepard.v2.shapes.io.ShapesRenderResponseIO} wire
   * contract — see {@link RenderResponse} for the field-by-field
   * mapping.
   *
   * <p>Implementations should populate per-binding
   * {@link RenderResponse.ChannelBindingProjection#status() status}
   * codes (OK / MISSING / UNIT_MISMATCH) rather than throw on
   * resolution failures; the dispatcher copies these through to the
   * wire envelope unchanged. Throw only for unrecoverable conditions.
   *
   * @param req the dispatch request — never null
   * @return the projected envelope — never null; bindings may be empty
   * @throws RenderException for typed renderer failures (validation,
   *         missing-channel-when-required, etc.)
   * @throws RuntimeException for unexpected failures; the dispatcher
   *         logs at WARN and surfaces an HTTP 500
   */
  RenderResponse render(RenderRequest req);

  /**
   * Human-readable name surfaced in startup logs and the future
   * {@code GET /v2/admin/view-recipe-renderers} admin endpoint.
   * Default: simple class name. Override when the impl name isn't
   * informative (e.g. a generic {@code DispatchingRenderer}).
   *
   * @return a non-empty short name
   */
  default String name() {
    return getClass().getSimpleName();
  }
}
