package de.dlr.shepard.spi.semantic;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SEMA-V6-009 — CDI registry for {@link SemanticVocabularyProvider} plugins.
 *
 * <p>Walks every {@link SemanticVocabularyProvider} CDI bean on the
 * classpath at startup and builds two indexes:
 * <ol>
 *   <li>Providers by {@link SemanticVocabularyProvider#vocabularyUri()}.
 *       (Two providers sharing the same URI are both kept; their
 *       predicate lists are unioned by the registry.)</li>
 *   <li>Predicate definitions by
 *       {@link VocabularyPredicateDefinition#iri()}.  First
 *       registration wins on collision; a WARN is logged.</li>
 * </ol>
 *
 * <p>Mirrors the established
 * {@link de.dlr.shepard.spi.analytics.AnalyticsRegistry} idiom:
 * an {@code @ApplicationScoped} bean, a {@code StartupEvent} observer
 * that resolves once, a package-private constructor for testing, and a
 * volatile immutable-snapshot pattern for thread safety.
 *
 * <h2>Wire-up note</h2>
 *
 * <p>No REST endpoint is wired in this PR.  The registry is available
 * as a CDI injection target for any future {@code GET
 * /v2/admin/semantic/vocabularies} endpoint once that lands (see
 * SEMA-V6-010 in {@code aidocs/16}).
 */
@ApplicationScoped
public class SemanticVocabularyRegistry {

  @Inject
  Instance<SemanticVocabularyProvider> providers;

  private volatile Map<String, SemanticVocabularyProvider> byVocabUri = Map.of();
  private volatile Map<String, VocabularyPredicateDefinition> predicateByIri = Map.of();

  /** Constructor for CDI. */
  public SemanticVocabularyRegistry() {}

  /** Visible for testing — calls {@link #resolve()} immediately. */
  SemanticVocabularyRegistry(Instance<SemanticVocabularyProvider> providers) {
    this.providers = providers;
    resolve();
  }

  /** Quarkus startup hook — indexes the discovered beans once. */
  void onStartup(@Observes StartupEvent ev) {
    resolve();
  }

  /** Idempotent resolve — rebuilds both indexes from the CDI instance. */
  void resolve() {
    Map<String, SemanticVocabularyProvider> vocabMap = new LinkedHashMap<>();
    Map<String, VocabularyPredicateDefinition> predMap = new LinkedHashMap<>();
    List<String> vocabIds = new ArrayList<>();

    if (providers != null) {
      for (SemanticVocabularyProvider p : providers) {
        if (p == null) continue;

        String uri = p.vocabularyUri();
        if (uri == null || uri.isBlank()) {
          Log.warnf(
            "SemanticVocabularyRegistry: skipping %s — vocabularyUri() returned null/blank",
            p.getClass().getName()
          );
          continue;
        }

        // Multiple providers may share a vocabularyUri — last write wins for the
        // provider index; both contribute their predicates.
        vocabMap.put(uri, p);
        vocabIds.add(uri);

        List<VocabularyPredicateDefinition> defs = p.predicates();
        if (defs == null) continue;

        for (VocabularyPredicateDefinition def : defs) {
          if (def == null) continue;
          String iri = def.iri();
          if (iri == null || iri.isBlank()) {
            Log.warnf(
              "SemanticVocabularyRegistry: skipping predicate from %s — iri is null/blank",
              p.getClass().getName()
            );
            continue;
          }
          VocabularyPredicateDefinition prior = predMap.putIfAbsent(iri, def);
          if (prior != null) {
            Log.warnf(
              "SemanticVocabularyRegistry: duplicate predicate IRI '%s' from %s — keeping first registration",
              iri,
              p.getClass().getName()
            );
          }
        }
      }
    }

    this.byVocabUri = Map.copyOf(vocabMap);
    this.predicateByIri = Map.copyOf(predMap);

    String available = vocabIds.isEmpty() ? "<none>" : String.join(", ", vocabIds);
    Log.infof(
      "SemanticVocabularyRegistry: discovered %d vocabulary provider(s): [%s]; %d total predicate(s)",
      byVocabUri.size(),
      available,
      predicateByIri.size()
    );
  }

  /**
   * @param vocabularyUri absolute vocabulary namespace IRI
   * @return the provider registered for that URI, or empty when none
   */
  public Optional<SemanticVocabularyProvider> getProvider(String vocabularyUri) {
    if (vocabularyUri == null || vocabularyUri.isBlank()) return Optional.empty();
    return Optional.ofNullable(byVocabUri.get(vocabularyUri));
  }

  /**
   * @param predicateIri absolute predicate IRI
   * @return the definition contributed for that IRI, or empty when none
   */
  public Optional<VocabularyPredicateDefinition> getPredicate(String predicateIri) {
    if (predicateIri == null || predicateIri.isBlank()) return Optional.empty();
    return Optional.ofNullable(predicateByIri.get(predicateIri));
  }

  /**
   * @return immutable view of all registered providers keyed by
   *         {@link SemanticVocabularyProvider#vocabularyUri()}.
   *         Surface for admin REST / diagnostics.
   */
  public Map<String, SemanticVocabularyProvider> allProviders() {
    return byVocabUri;
  }

  /**
   * @return immutable view of all contributed predicate definitions
   *         keyed by {@link VocabularyPredicateDefinition#iri()}.
   *         Surface for annotation pickers, SHACL generation,
   *         SPARQL autocompletion.
   */
  public Map<String, VocabularyPredicateDefinition> allPredicates() {
    return predicateByIri;
  }
}
