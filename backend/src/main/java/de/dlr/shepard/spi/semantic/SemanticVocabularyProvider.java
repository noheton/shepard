package de.dlr.shepard.spi.semantic;

import java.util.List;

/**
 * SEMA-V6-009 — SPI for pluggable vocabulary fragments.
 *
 * <p>An implementation contributes one or more
 * {@link VocabularyPredicateDefinition} records to the
 * {@link SemanticVocabularyRegistry} at startup.  The registry surfaces
 * these definitions via {@code GET /v2/admin/semantic/vocabularies}
 * (when that endpoint lands) and makes them available for annotation
 * pickers, SHACL shape generation, and SPARQL autocompletion.
 *
 * <h2>Discovery</h2>
 *
 * <p>Discovery is via CDI.  The {@link SemanticVocabularyRegistry} bean
 * collects every {@code @ApplicationScoped SemanticVocabularyProvider}
 * on the classpath at startup and indexes all contributed predicates by
 * {@link VocabularyPredicateDefinition#iri()}.  Plugins register their
 * implementation as a CDI bean (their JAR is on the backend's build
 * classpath via the {@code with-plugins} Maven profile — the
 * established shepard plugin pattern).
 *
 * <p><strong>Note:</strong> this SPI uses CDI discovery only — no
 * {@code META-INF/services} file is needed or expected.  (Contrast:
 * {@link de.dlr.shepard.spi.payload.PayloadKind} uses ServiceLoader
 * because it must fire before CDI is up inside
 * {@code NeoConnector.connect()}.  Vocabulary providers have no such
 * pre-CDI constraint.)
 *
 * <h2>Implementation contract</h2>
 *
 * <p>An implementation:
 * <ul>
 *   <li>Declares a stable {@link #vocabularyUri()} (absolute IRI of the
 *       vocabulary namespace, e.g.
 *       {@code "http://www.opengis.net/ont/geosparql#"}).</li>
 *   <li>Returns a non-null, non-empty list from
 *       {@link #predicates()}.  Each
 *       {@link VocabularyPredicateDefinition} carries an absolute IRI,
 *       a human label, an optional description, and an
 *       {@code expectedValueType} hint
 *       ({@code "LITERAL"}, {@code "IRI"}, or {@code "CUSTOM"}).</li>
 *   <li>Is annotated {@code @ApplicationScoped} so CDI discovers and
 *       manages exactly one instance per classpath.</li>
 * </ul>
 *
 * @see VocabularyPredicateDefinition
 * @see SemanticVocabularyRegistry
 */
public interface SemanticVocabularyProvider {

  /**
   * Absolute IRI of the vocabulary namespace this provider
   * contributes to.
   *
   * <p>Examples: {@code "http://www.opengis.net/ont/geosparql#"},
   * {@code "http://www.w3.org/2006/time#"}.
   *
   * <p>Used as the index key in {@link SemanticVocabularyRegistry}.
   * Two providers claiming the same vocabulary URI are both
   * registered; their predicate lists are merged (union).
   */
  String vocabularyUri();

  /**
   * Human-readable label for the vocabulary.  Used in admin UI
   * and logs.  Defaults to {@link #vocabularyUri()} when not
   * overridden.
   */
  default String vocabularyLabel() {
    return vocabularyUri();
  }

  /**
   * The predicate definitions this provider contributes.
   *
   * <p>Each entry must have a non-null, non-blank
   * {@link VocabularyPredicateDefinition#iri()}.  Predicates with
   * duplicate IRIs (across all providers) are de-duplicated by the
   * registry — the first registration wins.
   *
   * @return non-null list; may be empty but prefer non-empty
   */
  List<VocabularyPredicateDefinition> predicates();
}
