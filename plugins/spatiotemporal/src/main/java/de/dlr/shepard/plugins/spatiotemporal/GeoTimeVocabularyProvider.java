package de.dlr.shepard.plugins.spatiotemporal;

import de.dlr.shepard.spi.semantic.SemanticVocabularyProvider;
import de.dlr.shepard.spi.semantic.VocabularyPredicateDefinition;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

/**
 * SEMA-V6-009 — reference implementation of
 * {@link SemanticVocabularyProvider} shipped in the spatiotemporal plugin.
 *
 * <p>Contributes a combined GeoSPARQL + OWL-Time predicate fragment so
 * researchers annotating spatial {@code SpatialDataContainer} /
 * {@code SpatialDataPoint} objects (or any other shepard entity) can
 * pick well-known geo and temporal predicates from the annotation
 * picker without having to type full IRIs by hand.
 *
 * <h2>Vocabulary namespace</h2>
 *
 * <p>The provider declares {@link #vocabularyUri()} as the GeoSPARQL
 * namespace ({@code http://www.opengis.net/ont/geosparql#}), which is
 * already seeded as a {@code :Vocabulary} node by the V72 bootstrap migration
 * (SEMA-V6-002).  The OWL-Time predicates are companion terms whose
 * IRI base is {@code http://www.w3.org/2006/time#}; they are included
 * here because geo-temporal annotations are typically co-authored on
 * the same DataObject.
 *
 * <h2>Predicates contributed</h2>
 *
 * <ul>
 *   <li>{@code geosparql:hasGeometry} (IRI) — links a feature to its
 *       geometry representation.</li>
 *   <li>{@code geosparql:asWKT} (LITERAL) — WKT serialisation of a
 *       geometry; literal value.</li>
 *   <li>{@code geosparql:sfWithin} (IRI) — Simple-Features topological
 *       containment relation.</li>
 *   <li>{@code time:hasBeginning} (IRI) — OWL-Time start instant for a
 *       temporal entity (e.g. a test run interval).</li>
 *   <li>{@code time:hasEnd} (IRI) — OWL-Time end instant.</li>
 * </ul>
 *
 * <p>This list is intentionally minimal — a starting point for the
 * spatial use-case.  Additional geo or temporal predicates can be
 * contributed via a second {@code SemanticVocabularyProvider} CDI bean
 * without modifying this class.
 *
 * @see SemanticVocabularyProvider
 * @see de.dlr.shepard.spi.semantic.SemanticVocabularyRegistry
 */
@ApplicationScoped
public class GeoTimeVocabularyProvider implements SemanticVocabularyProvider {

  private static final String GEOSPARQL_NS = "http://www.opengis.net/ont/geosparql#";
  private static final String TIME_NS = "http://www.w3.org/2006/time#";

  private static final List<VocabularyPredicateDefinition> PREDICATES = List.of(
    new VocabularyPredicateDefinition(
      GEOSPARQL_NS + "hasGeometry",
      "has Geometry",
      "Relates a spatial feature to its geometry representation.",
      "IRI"
    ),
    new VocabularyPredicateDefinition(
      GEOSPARQL_NS + "asWKT",
      "as WKT",
      "WKT (Well-Known Text) serialisation of a geometry. Value is a typed literal.",
      "LITERAL"
    ),
    new VocabularyPredicateDefinition(
      GEOSPARQL_NS + "sfWithin",
      "Simple-Features Within",
      "Topological containment: the subject geometry is within the object geometry (Simple Features DE-9IM).",
      "IRI"
    ),
    new VocabularyPredicateDefinition(
      TIME_NS + "hasBeginning",
      "has Beginning",
      "OWL-Time: the beginning of a temporal entity (test run, observation interval, etc.).",
      "IRI"
    ),
    new VocabularyPredicateDefinition(
      TIME_NS + "hasEnd",
      "has End",
      "OWL-Time: the end of a temporal entity (test run, observation interval, etc.).",
      "IRI"
    )
  );

  @Override
  public String vocabularyUri() {
    return GEOSPARQL_NS;
  }

  @Override
  public String vocabularyLabel() {
    return "GeoSPARQL + OWL-Time (spatiotemporal plugin)";
  }

  @Override
  public List<VocabularyPredicateDefinition> predicates() {
    return PREDICATES;
  }
}
