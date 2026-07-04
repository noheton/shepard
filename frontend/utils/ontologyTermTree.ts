/**
 * L4 — pure helpers for the ontology search-as-you-type + tree/graph surface.
 *
 * The semantic term-search endpoint `GET /v2/semantic/terms/search` returns a
 * flat list of `{ uri, label, description }` suggestions with no parent/child
 * edges (the n10s `:Resource` nodes are queried by label, not by hierarchy).
 * To show a researcher *where a matched term sits in the ontology* without a
 * backend change, we derive a two-level hierarchy from the term's IRI
 * namespace — the standard ontology-browser fallback when an explicit
 * broader/narrower axis is unavailable (cf. BioPortal usage study,
 * arxiv 1610.09160; FAIR vocabulary URI-design guidance, arxiv 2003.13084).
 *
 * `namespaceOf` splits an IRI into (namespace, localName) at the last `#` or
 * `/`. `buildTermTree` groups suggestions by namespace and produces a
 * deterministic, alpha-sorted tree of namespace → term nodes that the page
 * renders directly (and that the ECharts/dagre graph view consumes too).
 *
 * Pulled out as a framework-free module so the grouping logic can be
 * unit-tested without mounting the Nuxt page (same pattern as
 * `vocabBrowserUrl.ts` / `snapshotDiffPage.test.ts`).
 */

export interface TermLike {
  uri: string;
  label: string;
  description?: string | null;
}

/** A single matched ontology term, leaf of the tree. */
export interface TermTreeLeaf {
  /** Stable key for v-list / graph node id — the full IRI. */
  id: string;
  uri: string;
  label: string;
  localName: string;
  description?: string | null;
}

/** A namespace grouping node (the ontology the term belongs to). */
export interface TermTreeNamespace {
  /** Stable key — the namespace IRI prefix. */
  id: string;
  namespace: string;
  /** Short human-facing prefix derived from the namespace (e.g. `dcterms`). */
  shortPrefix: string;
  children: TermTreeLeaf[];
}

/**
 * Split an IRI into its namespace prefix and local name.
 *
 * Splits at the last `#` (hash namespaces, e.g. OWL/RDFS) or, failing that,
 * the last `/` (slash namespaces, e.g. dcterms). The namespace retains its
 * trailing delimiter so `namespace + localName === uri`. A URI with neither
 * delimiter is returned whole as its own namespace with an empty local name.
 */
export function namespaceOf(uri: string): { namespace: string; localName: string } {
  if (!uri) return { namespace: "", localName: "" };
  const hash = uri.lastIndexOf("#");
  if (hash >= 0 && hash < uri.length - 1) {
    return { namespace: uri.slice(0, hash + 1), localName: uri.slice(hash + 1) };
  }
  const slash = uri.lastIndexOf("/");
  if (slash >= 0 && slash < uri.length - 1) {
    return { namespace: uri.slice(0, slash + 1), localName: uri.slice(slash + 1) };
  }
  return { namespace: uri, localName: "" };
}

/**
 * Derive a short, human-facing prefix label from a namespace IRI.
 *
 * Takes the last meaningful path segment (or hostname for bare-host
 * namespaces). Falls back to the namespace itself when nothing usable can be
 * extracted. Purely cosmetic — used as the tree group header.
 */
export function shortPrefixOf(namespace: string): string {
  if (!namespace) return "(no namespace)";
  // Strip the trailing delimiter, then take the last non-empty segment.
  const trimmed = namespace.replace(/[#/]+$/, "");
  const segments = trimmed.split(/[#/]/).filter((s) => s.length > 0);
  if (segments.length === 0) return namespace;
  const last = segments[segments.length - 1] ?? namespace;
  // A version-y or numeric tail (e.g. ".../2.0") isn't a useful label; back up.
  if (/^[0-9.]+$/.test(last) && segments.length >= 2) {
    return segments[segments.length - 2] ?? last;
  }
  return last;
}

/**
 * Build a deterministic namespace → term tree from a flat suggestion list.
 *
 * - Groups by IRI namespace (`namespaceOf`).
 * - De-duplicates terms by full URI (the backend can return the same IRI under
 *   multiple label matches).
 * - Sorts namespaces alphabetically by short prefix (then namespace IRI), and
 *   leaves alphabetically by label (then local name) — stable output so the
 *   tree never reshuffles between identical queries.
 */
export function buildTermTree(terms: TermLike[]): TermTreeNamespace[] {
  const byNamespace = new Map<string, TermTreeNamespace>();
  const seenUris = new Set<string>();

  for (const t of terms) {
    if (!t || !t.uri || seenUris.has(t.uri)) continue;
    seenUris.add(t.uri);

    const { namespace, localName } = namespaceOf(t.uri);
    let group = byNamespace.get(namespace);
    if (!group) {
      group = {
        id: namespace,
        namespace,
        shortPrefix: shortPrefixOf(namespace),
        children: [],
      };
      byNamespace.set(namespace, group);
    }
    group.children.push({
      id: t.uri,
      uri: t.uri,
      label: t.label && t.label.trim().length > 0 ? t.label : localName || t.uri,
      localName,
      description: t.description ?? null,
    });
  }

  const groups = Array.from(byNamespace.values());
  for (const g of groups) {
    g.children.sort((a, b) => {
      const byLabel = a.label.localeCompare(b.label);
      return byLabel !== 0 ? byLabel : a.localName.localeCompare(b.localName);
    });
  }
  groups.sort((a, b) => {
    const byPrefix = a.shortPrefix.localeCompare(b.shortPrefix);
    return byPrefix !== 0 ? byPrefix : a.namespace.localeCompare(b.namespace);
  });
  return groups;
}

/** Total number of (de-duplicated) leaf terms across all namespaces. */
export function countTerms(tree: TermTreeNamespace[]): number {
  return tree.reduce((acc, g) => acc + g.children.length, 0);
}

// ─── graph projection ─────────────────────────────────────────────────────────

export interface TermGraphNode {
  id: string;
  name: string;
  /** 0 = namespace (root grouping), 1 = term leaf. */
  category: number;
  symbolSize: number;
  uri: string;
}

export interface TermGraphEdge {
  source: string;
  target: string;
}

/**
 * Project the namespace tree into ECharts graph nodes + edges (one edge per
 * namespace → term). Mirrors the node/edge shape consumed by the existing
 * lineage/prov graphs so we can reuse `baseGraphSeriesConfig` + dagre layout.
 */
export function buildTermGraph(tree: TermTreeNamespace[]): {
  nodes: TermGraphNode[];
  edges: TermGraphEdge[];
} {
  const nodes: TermGraphNode[] = [];
  const edges: TermGraphEdge[] = [];
  for (const g of tree) {
    nodes.push({
      id: g.id,
      name: g.shortPrefix,
      category: 0,
      symbolSize: 34,
      uri: g.namespace,
    });
    for (const leaf of g.children) {
      nodes.push({
        id: leaf.id,
        name: leaf.label,
        category: 1,
        symbolSize: 20,
        uri: leaf.uri,
      });
      edges.push({ source: g.id, target: leaf.id });
    }
  }
  return { nodes, edges };
}
