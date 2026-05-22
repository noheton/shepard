/**
 * SHACL changeover scope (non-TS, see task brief 2026-05-22) — the
 * v2 shapes surface.
 *
 * <p>This package hosts the validate-only contract:
 * <ul>
 *   <li>{@link de.dlr.shepard.v2.shapes.validator.JenaShaclValidator}
 *       — in-process Jena SHACL validator (Apache-2.0). Takes a
 *       candidate data graph + a shape graph as Jena
 *       {@link org.apache.jena.rdf.model.Model}s, returns a
 *       structured validation report. No Neo4j/n10s extraction lives
 *       here — that is the PR-2 substrate work (deferred).</li>
 *   <li>{@link de.dlr.shepard.v2.shapes.resources.ShapesValidateRest}
 *       — {@code POST /v2/shapes/validate} taking
 *       {@code {dataTurtle, shapeTurtle}} and returning a
 *       validation report. The MCP-tool entrypoint per the Digital
 *       Native review #4.</li>
 *   <li>{@link de.dlr.shepard.v2.shapes.io.ShapeValidationReportIO}
 *       — JSON wire shape of the validation report.</li>
 * </ul>
 *
 * <p><b>Scope boundary.</b> This package is the validation engine
 * only. The on-disk RDF store remains n10s in Neo4j (see
 * {@code aidocs/semantics/48}); reading individuals out of n10s as a
 * Jena Model is PR-2 substrate work and is intentionally NOT here
 * yet. The current contract is "client supplies the candidate
 * payload + the shape, we validate" — sufficient for pre-flight
 * validation on writes and for the MCP tool that lets an LLM
 * pre-validate a candidate JSON-LD payload before submitting.
 *
 * <p><b>Backward compatibility.</b> Nothing in this package touches
 * the {@code /shepard/api/...} surface — it's pure v2-shelf surface
 * per the L2d carve-out.
 *
 * @since 6.0.0
 * @see <a href="../../../../../../aidocs/semantics/98-mffd-process-shapes.md">aidocs/98</a>
 * @see <a href="../../../../../../aidocs/semantics/95-shacl-templates-and-individuals.md">aidocs/95</a>
 */
package de.dlr.shepard.v2.shapes;
