package de.dlr.shepard.spi.semantic;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;
import java.lang.annotation.Annotation;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * SEMA-V6-009 — unit tests for {@link SemanticVocabularyRegistry}.
 *
 * <p>Verifies discovery, by-vocabularyUri lookup, by-predicateIri
 * lookup, duplicate-IRI behaviour, null/blank-id skipping, and
 * graceful degradation when no providers are present.
 */
class SemanticVocabularyRegistryTest {

  // ── fixtures ─────────────────────────────────────────────────────────────

  /** Minimal vocabulary provider. */
  static class FakeProvider implements SemanticVocabularyProvider {
    private final String uri;
    private final List<VocabularyPredicateDefinition> preds;

    FakeProvider(String uri, VocabularyPredicateDefinition... preds) {
      this.uri = uri;
      this.preds = List.of(preds);
    }

    @Override
    public String vocabularyUri() {
      return uri;
    }

    @Override
    public List<VocabularyPredicateDefinition> predicates() {
      return preds;
    }
  }

  /** Hand-rolled CDI Instance shim (mirrors AnalyticsRegistryTest). */
  static class FakeInstance<T> implements Instance<T> {
    final List<T> items;

    FakeInstance(List<T> items) {
      this.items = items;
    }

    @Override
    public Iterator<T> iterator() {
      return items.iterator();
    }

    @Override
    public T get() {
      return items.get(0);
    }

    @Override
    public Instance<T> select(Annotation... qualifiers) {
      return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <U extends T> Instance<U> select(Class<U> subtype, Annotation... qualifiers) {
      return (Instance<U>) this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <U extends T> Instance<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
      return (Instance<U>) this;
    }

    @Override
    public boolean isUnsatisfied() {
      return items.isEmpty();
    }

    @Override
    public boolean isAmbiguous() {
      return false;
    }

    @Override
    public boolean isResolvable() {
      return items.size() == 1;
    }

    @Override
    public void destroy(T instance) {
      // no-op
    }

    @Override
    public Handle<T> getHandle() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Iterable<? extends Handle<T>> handles() {
      return java.util.Collections.emptyList();
    }
  }

  // ── helper predicates ─────────────────────────────────────────────────────

  private static VocabularyPredicateDefinition pred(String iri, String label) {
    return new VocabularyPredicateDefinition(iri, label, null, "LITERAL");
  }

  // ── tests ────────────────────────────────────────────────────────────────

  @Test
  void registry_discovers_provider_and_indexes_by_vocabulary_uri() {
    var p = new FakeProvider(
      "http://example.org/vocab#",
      pred("http://example.org/vocab#foo", "Foo")
    );
    var registry = new SemanticVocabularyRegistry(new FakeInstance<>(List.of(p)));

    assertThat(registry.allProviders()).hasSize(1);
    assertThat(registry.getProvider("http://example.org/vocab#")).containsSame(p);
  }

  @Test
  void registry_indexes_predicates_by_iri() {
    var def = pred("http://example.org/vocab#foo", "Foo");
    var p = new FakeProvider("http://example.org/vocab#", def);
    var registry = new SemanticVocabularyRegistry(new FakeInstance<>(List.of(p)));

    assertThat(registry.allPredicates()).hasSize(1);
    assertThat(registry.getPredicate("http://example.org/vocab#foo")).contains(def);
  }

  @Test
  void getPredicate_returnsEmpty_whenIriNotRegistered() {
    var registry = new SemanticVocabularyRegistry(new FakeInstance<>(List.of()));

    assertThat(registry.getPredicate("http://example.org/unknown#foo")).isEmpty();
  }

  @Test
  void getProvider_returnsEmpty_whenUriNotRegistered() {
    var registry = new SemanticVocabularyRegistry(new FakeInstance<>(List.of()));

    assertThat(registry.getProvider("http://missing.org/")).isEmpty();
  }

  @Test
  void getProvider_returnsEmpty_for_null_and_blank() {
    var registry = new SemanticVocabularyRegistry(new FakeInstance<>(List.of()));

    assertThat(registry.getProvider(null)).isEmpty();
    assertThat(registry.getProvider("")).isEmpty();
    assertThat(registry.getProvider("   ")).isEmpty();
  }

  @Test
  void getPredicate_returnsEmpty_for_null_and_blank() {
    var registry = new SemanticVocabularyRegistry(new FakeInstance<>(List.of()));

    assertThat(registry.getPredicate(null)).isEmpty();
    assertThat(registry.getPredicate("")).isEmpty();
  }

  @Test
  void duplicate_predicate_iri_keeps_first_registration() {
    var def1 = new VocabularyPredicateDefinition("http://example.org/vocab#bar", "Bar-first", null, "IRI");
    var def2 = new VocabularyPredicateDefinition("http://example.org/vocab#bar", "Bar-second", null, "IRI");
    var p1 = new FakeProvider("http://example.org/vocab#", def1);
    var p2 = new FakeProvider("http://example.org/vocab2#", def2);
    var registry = new SemanticVocabularyRegistry(new FakeInstance<>(List.of(p1, p2)));

    assertThat(registry.allPredicates()).hasSize(1);
    assertThat(registry.getPredicate("http://example.org/vocab#bar"))
      .hasValueSatisfying(d -> assertThat(d.label()).isEqualTo("Bar-first"));
  }

  @Test
  void provider_with_blank_vocabulary_uri_is_skipped() {
    var bad = new FakeProvider("  ", pred("http://example.org/vocab#foo", "Foo"));
    var good = new FakeProvider("http://example.org/good#", pred("http://example.org/good#bar", "Bar"));
    var registry = new SemanticVocabularyRegistry(new FakeInstance<>(List.of(bad, good)));

    assertThat(registry.allProviders()).containsOnlyKeys("http://example.org/good#");
    assertThat(registry.allPredicates()).hasSize(1);
  }

  @Test
  void empty_providers_results_in_empty_registry() {
    var registry = new SemanticVocabularyRegistry(new FakeInstance<>(List.of()));

    assertThat(registry.allProviders()).isEmpty();
    assertThat(registry.allPredicates()).isEmpty();
  }

  @Test
  void multiple_providers_have_all_predicates_merged() {
    var p1 = new FakeProvider(
      "http://www.opengis.net/ont/geosparql#",
      pred("http://www.opengis.net/ont/geosparql#hasGeometry", "has Geometry"),
      pred("http://www.opengis.net/ont/geosparql#asWKT", "as WKT")
    );
    var p2 = new FakeProvider(
      "http://www.w3.org/2006/time#",
      pred("http://www.w3.org/2006/time#hasBeginning", "has Beginning"),
      pred("http://www.w3.org/2006/time#hasEnd", "has End")
    );
    var registry = new SemanticVocabularyRegistry(new FakeInstance<>(List.of(p1, p2)));

    assertThat(registry.allProviders()).hasSize(2);
    assertThat(registry.allPredicates()).hasSize(4);
    assertThat(registry.getPredicate("http://www.opengis.net/ont/geosparql#hasGeometry")).isPresent();
    assertThat(registry.getPredicate("http://www.w3.org/2006/time#hasEnd")).isPresent();
  }
}
