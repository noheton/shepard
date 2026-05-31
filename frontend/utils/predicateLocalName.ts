/**
 * II1 (ui-scrutinizer-2026-05-30) — local-name extraction for an RDF IRI.
 *
 * The `/semantic/predicates/{iri}` detail page (shipped 2026-05-31 in
 * `ffb6dcb48`) currently renders the bare IRI as the page heading. Most
 * predicates have a human-readable local name reachable by splitting on
 * `/` and `#` — the same shape `AnnotationChip` uses as its fallback for
 * `propertyName`-less rows.
 *
 * Why local-name extraction rather than a single-term lookup endpoint:
 *
 *   - No `GET /v2/semantic/terms/{iri}` endpoint exists today. The
 *     search endpoint (`GET /v2/semantic/terms/search?q=…`) is built for
 *     prefix-match autocomplete (caps at `MIN_QUERY_LENGTH=2`, tokenises
 *     on `$q + "*"`) and won't reliably find a single term when the
 *     "query" is a full IRI carrying slashes and colons.
 *
 *   - `usePredicateStats` already gives us the `topValues` and
 *     `sampleEntities` rows — adding a network round-trip just to get a
 *     display label would inflate the page TTFB for a single string we
 *     can derive client-side in zero ms.
 *
 *   - Local-name extraction is the de-facto convention everywhere else in
 *     the codebase (see `AnnotationChip.test.ts` line 47).
 *
 * A proper single-term lookup endpoint that returns `rdfs:label` /
 * `skos:prefLabel` from the n10s store is tracked as `SEMANTIC-TERM-GET-1`
 * in `aidocs/16`. When it ships, this helper stays as the fallback path
 * (offline-safe, zero-latency) and a new composable wraps the network
 * call on top.
 */

/**
 * Split an IRI on `#` (most specific — fragment identifier) or, failing
 * that, on the last `/`. Returns the trailing segment, or the input
 * unchanged when neither separator is present (URN-style IRIs like
 * `urn:shepard:appId:…` already end in the meaningful token).
 *
 * Examples:
 *   `http://schema.org/material` → `material`
 *   `http://www.w3.org/ns/prov#wasInformedBy` → `wasInformedBy`
 *   `urn:shepard:domain:role` → `urn:shepard:domain:role` (unchanged)
 *
 * The result is **not** humanised further — `wasInformedBy` stays
 * camel-case rather than becoming `was informed by`. CamelCase is the
 * conventional rendering for RDF predicate display.
 */
export function predicateLocalName(iri: string): string {
  if (!iri || iri.length === 0) return iri;
  // Prefer fragment over path — `#name` is the most specific local
  // identifier when the IRI follows the RDF/OWL convention.
  const hashIdx = iri.lastIndexOf("#");
  if (hashIdx >= 0 && hashIdx < iri.length - 1) {
    return iri.slice(hashIdx + 1);
  }
  const slashIdx = iri.lastIndexOf("/");
  if (slashIdx >= 0 && slashIdx < iri.length - 1) {
    return iri.slice(slashIdx + 1);
  }
  return iri;
}

/**
 * True when the result of `predicateLocalName(iri)` would actually be
 * shorter / more informative than the IRI itself. URN-style IRIs that
 * carry no separator return false — in that case the page should fall
 * back to rendering just the IRI without the "<label> (<iri>)" shape.
 */
export function hasUsableLocalName(iri: string): boolean {
  if (!iri || iri.length === 0) return false;
  const local = predicateLocalName(iri);
  return local !== iri && local.length > 0;
}
