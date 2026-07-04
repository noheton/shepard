/**
 * V2CONV-B3 — {@code TransformExecutor} SPI (the {@code MAPPING_RECIPE}
 * transform direction).
 *
 * <p>Transform-direction sibling of the
 * {@link de.dlr.shepard.spi.view ViewRecipeRenderer} SPI. Where a
 * {@code VIEW_RECIPE} renderer projects a focus DataObject's channels into a
 * read-only view-model, a {@code MAPPING_RECIPE}
 * {@link de.dlr.shepard.spi.transform.TransformExecutor} binds existing
 * <em>input reference appIds</em> to a <em>derived output</em>: a new reference
 * appId, or a played/rendered view-model.
 *
 * <p>The {@code POST /v2/mappings/{templateAppId}/materialize} endpoint reads
 * the requested {@link de.dlr.shepard.template.entities.ShepardTemplate} (a
 * {@code MAPPING_RECIPE}), pulls its {@code mappingRecipeShape} IRI from the
 * body, and routes the request to a
 * {@link de.dlr.shepard.spi.transform.TransformExecutor} discovered via
 * {@link java.util.ServiceLoader} on the
 * {@link de.dlr.shepard.spi.transform.TransformExecutorRegistry}. When no
 * executor claims the shape IRI, the dispatcher returns a 404 problem-JSON
 * (fail-soft registry — never 500).
 *
 * <h2>The enabler role</h2>
 *
 * <p>This SPI is the generic machinery that later dissolves the bespoke
 * scene-graph and KRL namespaces (V2CONV-B4/B5) into {@code MAPPING_RECIPE}
 * shapes — the scene-graph play envelope becomes a
 * {@link de.dlr.shepard.spi.transform.TransformResult.Kind#VIEW VIEW} result;
 * the KRL joint trajectory becomes a
 * {@link de.dlr.shepard.spi.transform.TransformResult.Kind#REFERENCE REFERENCE}
 * result. Those consumers are NOT implemented in V2CONV-B3; this package ships
 * only the contract, the registry, and the built-in
 * {@link de.dlr.shepard.spi.transform.NoOpTransformExecutor identity default}.
 *
 * <h2>Failure modes</h2>
 *
 * <ul>
 *   <li><b>Duplicate shape IRI across executors</b> — fail-fast at startup
 *       (matches the renderer registry).</li>
 *   <li><b>Executor throws {@link de.dlr.shepard.spi.transform.TransformException}</b>
 *       — mapped to 4xx by the dispatcher per the typed code.</li>
 *   <li><b>No executor registered for the shape IRI</b> — fail-soft resolve;
 *       the dispatcher returns 404.</li>
 * </ul>
 *
 * <h2>Cross-references</h2>
 *
 * <ul>
 *   <li>{@code aidocs/platform/191 §4} — MAPPING_RECIPE design</li>
 *   <li>{@code aidocs/16} — V2CONV-B3 row</li>
 *   <li>{@link de.dlr.shepard.spi.view.ViewRecipeRenderer} — the view-direction
 *       sibling SPI</li>
 * </ul>
 */
package de.dlr.shepard.spi.transform;
