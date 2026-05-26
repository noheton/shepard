package de.dlr.shepard.spi.semantic;

/**
 * SEMA-V6-009 — immutable value object describing one predicate
 * contributed by a {@link SemanticVocabularyProvider}.
 *
 * <p>This is a pure in-memory SPI contribution, distinct from the
 * persisted {@code de.dlr.shepard.context.semantic.entities.Predicate}
 * Neo4j entity created by SEMA-V6-002.  A future reconciler may merge
 * plugin-contributed definitions into the persisted layer; today these
 * objects live only in the {@link SemanticVocabularyRegistry}.
 *
 * <h2>{@code expectedValueType} vs {@code Predicate.ExpectedObjectType}</h2>
 *
 * <p>The persisted {@code Predicate} entity uses an
 * {@code ExpectedObjectType} enum with values
 * {@code LITERAL, URI, DATAOBJECT_APPID, CONTAINER_APPID}.
 * This SPI record uses a narrower three-valued string set:
 * <ul>
 *   <li>{@code "LITERAL"} — the annotation object is an RDF literal
 *       (string, number, date, …).</li>
 *   <li>{@code "IRI"} — the annotation object is a URI / named
 *       resource.</li>
 *   <li>{@code "CUSTOM"} — the annotation object is domain-specific
 *       (e.g. a GeoJSON string, WKT, complex literal) that cannot
 *       be validated as a plain literal or IRI.</li>
 * </ul>
 *
 * <p>The intentional difference avoids binding the SPI wire shape to
 * the persistence-layer enum, which may evolve independently.
 *
 * @param iri               absolute predicate IRI; must be non-null,
 *                          non-blank.  Example:
 *                          {@code "http://www.opengis.net/ont/geosparql#hasGeometry"}
 * @param label             human-readable label; must be non-null.
 *                          Example: {@code "has Geometry"}
 * @param description       optional human-readable description; may be
 *                          {@code null}.
 * @param expectedValueType one of {@code "LITERAL"}, {@code "IRI"}, or
 *                          {@code "CUSTOM"}.  Callers that do not
 *                          recognise the value should treat it as
 *                          {@code "LITERAL"}.
 */
public record VocabularyPredicateDefinition(
  String iri,
  String label,
  String description,
  String expectedValueType
) {}
