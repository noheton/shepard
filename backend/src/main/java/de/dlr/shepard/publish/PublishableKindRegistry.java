package de.dlr.shepard.publish;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * KIP1a — registry of {@link PublishableKind} values keyed by their
 * URL segment. The registry is the single point of truth for "which
 * {@code {kind}} URL segments does {@code POST /v2/{kind}/{appId}/publish}
 * accept?" — adding a new kind in a future KIP slice only touches
 * {@link PublishableKind}.
 *
 * <p>The lookup is case-sensitive on the kebab-cased segment per
 * {@code aidocs/66 §4.1} (e.g. {@code "data-objects"} matches,
 * {@code "DataObjects"} doesn't). An unknown segment yields
 * {@link Optional#empty()}; the REST layer maps that to an RFC 7807
 * {@code publish.kind.unsupported} 404.
 */
@ApplicationScoped
public class PublishableKindRegistry {

  private final Map<String, PublishableKind> bySegment;

  public PublishableKindRegistry() {
    Map<String, PublishableKind> m = new LinkedHashMap<>();
    for (PublishableKind k : PublishableKind.values()) {
      m.put(k.urlSegment(), k);
    }
    this.bySegment = Map.copyOf(m);
  }

  /**
   * Resolve a URL segment to its {@link PublishableKind}.
   *
   * @param urlSegment the kebab-cased segment from {@code /v2/{kind}/...}
   * @return the matched kind, or empty when unsupported.
   */
  public Optional<PublishableKind> bySegment(String urlSegment) {
    if (urlSegment == null) return Optional.empty();
    return Optional.ofNullable(bySegment.get(urlSegment));
  }

  /**
   * Resolve a Neo4j label back to its {@link PublishableKind} — used
   * by the KIP resolver to reconstruct the {@code landingPage} URL
   * from a stored {@link de.dlr.shepard.publish.entities.Publication}
   * row.
   */
  public Optional<PublishableKind> byNeo4jLabel(String label) {
    if (label == null) return Optional.empty();
    return Arrays.stream(PublishableKind.values()).filter(k -> k.neo4jLabel().equals(label)).findFirst();
  }

  /** @return all URL segments registered, in declaration order. */
  public Set<String> supportedSegments() {
    return bySegment.keySet();
  }
}
