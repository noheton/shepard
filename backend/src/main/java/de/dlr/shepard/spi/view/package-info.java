/**
 * VIS-S1 — {@code ViewRecipeRenderer} SPI.
 *
 * <p>Cross-cutting platform piece for the VIS-* plugin family
 * (vis-trace3d, vis-cad, vis-fem, vis-volume). The {@code POST
 * /v2/shapes/render} endpoint reads the requested
 * {@link de.dlr.shepard.template.entities.ShepardTemplate ShepardTemplate}
 * (a {@code VIEW_RECIPE}), pulls its {@code viewRecipeShape} IRI from
 * the body, and routes the request to a
 * {@link de.dlr.shepard.spi.view.ViewRecipeRenderer} discovered via
 * {@link java.util.ServiceLoader} on the
 * {@link de.dlr.shepard.spi.view.ViewRecipeRendererRegistry}. When no
 * renderer claims the shape IRI, the endpoint falls back to the
 * in-tree {@code DECLARED}-status projection it shipped in TPL2b — so
 * upstream operators see zero wire-shape change after this SPI lands.
 *
 * <h2>The dispatch key</h2>
 *
 * Renderers register against the IRI of the SHACL {@code sh:NodeShape}
 * they handle. A VIEW_RECIPE template body declares its target shape
 * in the {@code viewRecipeShape} field, e.g.
 *
 * <pre>{@code
 * {
 *   "viewRecipeShape": "http://semantics.dlr.de/shepard-ui/trace3d#Trace3DViewShape",
 *   "renderer": "tresjs",
 *   "channelBindings": [ ... ]
 * }
 * }</pre>
 *
 * The {@code renderer} field is a frontend-component hint (Vue
 * component selector); the {@code viewRecipeShape} IRI is the
 * backend-resolver dispatch key. They serve different layers and must
 * not be conflated — multiple shapes can share a renderer hint (every
 * Three.js view declares {@code renderer = "tresjs"}); only the shape
 * IRI uniquely names the backend resolver.
 *
 * <h2>SPI contract</h2>
 *
 * Renderers are plain POJOs registered through
 * {@code META-INF/services/de.dlr.shepard.spi.view.ViewRecipeRenderer}
 * — same shape as
 * {@link de.dlr.shepard.spi.payload.PayloadKind PayloadKind} (P-series
 * SPI prior art). They do NOT need to be CDI beans; the registry walks
 * {@link java.util.ServiceLoader} at startup, builds a
 * {@code Map<String, ViewRecipeRenderer>} keyed by the union of every
 * implementation's {@link de.dlr.shepard.spi.view.ViewRecipeRenderer#supportedShapeIris()},
 * and serves {@code resolve(shapeIri)} in O(1).
 *
 * <h2>Failure modes</h2>
 *
 * <ul>
 *   <li><b>Duplicate shape IRI across renderers</b> — fail-fast at
 *       startup with a clear error naming both registrants. Two
 *       plugins claiming the same shape IRI is a build-time error;
 *       refusing to start is the actionable signal.</li>
 *   <li><b>Renderer throws at runtime</b> — the dispatcher surfaces
 *       this as an HTTP 500 with a clear RFC 7807 body; the renderer
 *       contract is "throw {@link de.dlr.shepard.spi.view.RenderException}
 *       with a typed error code" but a plain {@link RuntimeException}
 *       is caught and logged.</li>
 *   <li><b>No renderer registered for the shape IRI</b> — the
 *       dispatcher falls back to the in-tree
 *       {@link de.dlr.shepard.v2.shapes.resources.ShapesRenderRest}
 *       {@code DECLARED}-projection behaviour. This preserves the
 *       TPL2b wire contract for templates whose plugin isn't
 *       installed.</li>
 * </ul>
 *
 * <h2>Cross-references</h2>
 *
 * <ul>
 *   <li>{@code aidocs/16} — VIS-S1 row (this SPI)</li>
 *   <li>{@code aidocs/semantics/98 §1.2} — {@code POST /v2/shapes/render}
 *       endpoint contract</li>
 *   <li>{@code aidocs/agent-findings/trace3d-spike.md §3} — the frame
 *       envelope shape that {@link de.dlr.shepard.spi.view.RenderResponse}
 *       carries</li>
 *   <li>{@code aidocs/34} — operator-facing change ledger row</li>
 *   <li>{@code aidocs/44} — fork-vs-upstream feature-matrix row</li>
 *   <li>{@link de.dlr.shepard.spi.payload.PayloadKind} — P-series SPI
 *       ServiceLoader prior art</li>
 * </ul>
 */
package de.dlr.shepard.spi.view;
