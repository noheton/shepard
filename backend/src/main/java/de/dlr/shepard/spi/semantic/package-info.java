/**
 * SEMA-V6-009 SPI seam — the in-tree contract every vocabulary plugin
 * compiles against, designed in {@code aidocs/semantics/100}.
 *
 * <p>The package follows the same "SPI in core, adapters in plugins"
 * pattern established by
 * {@link de.dlr.shepard.spi.payload.PayloadKind} (P-series),
 * {@link de.dlr.shepard.publish.minter.Minter} (KIP1a), and
 * {@link de.dlr.shepard.spi.analytics.TimeseriesAnalytics} (AT1):
 * every type here is a stability contract for consumers and a vendor
 * extension point for vocabulary contributors.
 *
 * <h2>Two types</h2>
 *
 * <ul>
 *   <li><b>{@link de.dlr.shepard.spi.semantic.SemanticVocabularyProvider}</b>
 *       — the <i>plugin-facing</i> SPI.  A CDI bean that contributes a
 *       list of {@link de.dlr.shepard.spi.semantic.VocabularyPredicateDefinition}
 *       records.  The first shipped reference implementation is
 *       {@code GeoTimeVocabularyProvider} in
 *       {@code shepard-plugin-spatial}, contributing a GeoSPARQL +
 *       OWL-Time predicate fragment.</li>
 *   <li><b>{@link de.dlr.shepard.spi.semantic.VocabularyPredicateDefinition}</b>
 *       — immutable value object (Java record) carrying one predicate's
 *       IRI, label, optional description, and expected-value-type hint.
 *       Intentionally separate from the persisted
 *       {@code de.dlr.shepard.context.semantic.entities.Predicate}
 *       Neo4j entity — the SPI shape and the storage shape may evolve
 *       independently.</li>
 * </ul>
 *
 * <h2>Discovery</h2>
 *
 * <p>Discovery is via CDI only (no {@code META-INF/services} file).
 * {@link de.dlr.shepard.spi.semantic.SemanticVocabularyRegistry}
 * collects every {@code @ApplicationScoped SemanticVocabularyProvider}
 * on the classpath via {@code @Inject Instance<SemanticVocabularyProvider>}
 * and indexes them at {@code StartupEvent}.
 *
 * <p>Contrast with {@link de.dlr.shepard.spi.payload.PayloadKind},
 * which uses {@code java.util.ServiceLoader} because it must fire
 * inside {@code NeoConnector.connect()} before CDI is up.  Vocabulary
 * providers have no such pre-CDI constraint.
 *
 * @see de.dlr.shepard.spi.analytics.TimeseriesAnalytics AT1 SPI prior art
 * @see de.dlr.shepard.spi.payload.PayloadKind P-series ServiceLoader prior art
 */
package de.dlr.shepard.spi.semantic;
