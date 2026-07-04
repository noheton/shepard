/**
 * L4 — unit tests for the ontology-term tree/graph pure helpers.
 *
 * Covers `namespaceOf`, `shortPrefixOf`, `buildTermTree`, `countTerms`, and
 * `buildTermGraph` — the framework-free logic behind the `/semantic/search`
 * surface. Pattern: pure-function tests, no Vue mount (mirrors
 * vocabBrowserUrl.test.ts / snapshotDiffPage.test.ts).
 */
import { describe, it, expect } from "vitest";
import {
  namespaceOf,
  shortPrefixOf,
  buildTermTree,
  countTerms,
  buildTermGraph,
  type TermLike,
} from "../../utils/ontologyTermTree";

describe("namespaceOf", () => {
  it("splits a hash namespace at the last #", () => {
    expect(namespaceOf("http://www.w3.org/2000/01/rdf-schema#label")).toEqual({
      namespace: "http://www.w3.org/2000/01/rdf-schema#",
      localName: "label",
    });
  });

  it("splits a slash namespace at the last /", () => {
    expect(namespaceOf("http://purl.org/dc/terms/creator")).toEqual({
      namespace: "http://purl.org/dc/terms/",
      localName: "creator",
    });
  });

  it("prefers the hash over an earlier slash", () => {
    const { namespace, localName } = namespaceOf("http://example.org/onto#Anomaly");
    expect(namespace).toBe("http://example.org/onto#");
    expect(localName).toBe("Anomaly");
  });

  it("guarantees namespace + localName reconstructs the uri", () => {
    const uri = "http://purl.org/dc/terms/creator";
    const { namespace, localName } = namespaceOf(uri);
    expect(namespace + localName).toBe(uri);
  });

  it("returns the whole uri as namespace when no delimiter is usable", () => {
    expect(namespaceOf("urn:shepard:spatial")).toEqual({
      namespace: "urn:shepard:spatial",
      localName: "",
    });
  });

  it("handles empty input", () => {
    expect(namespaceOf("")).toEqual({ namespace: "", localName: "" });
  });

  it("does not split on a trailing # (localName would be empty); falls back to slash", () => {
    // The trailing # is not a usable split point, so the last / wins instead.
    expect(namespaceOf("http://example.org/onto#")).toEqual({
      namespace: "http://example.org/",
      localName: "onto#",
    });
  });
});

describe("shortPrefixOf", () => {
  it("takes the last path segment of a slash namespace", () => {
    expect(shortPrefixOf("http://purl.org/dc/terms/")).toBe("terms");
  });

  it("takes the last segment of a hash namespace", () => {
    expect(shortPrefixOf("http://www.w3.org/2000/01/rdf-schema#")).toBe("rdf-schema");
  });

  it("backs up past a purely numeric/version tail", () => {
    expect(shortPrefixOf("http://example.org/onto/2.0/")).toBe("onto");
  });

  it("falls back gracefully on an empty namespace", () => {
    expect(shortPrefixOf("")).toBe("(no namespace)");
  });
});

describe("buildTermTree", () => {
  const terms: TermLike[] = [
    { uri: "http://purl.org/dc/terms/creator", label: "Creator", description: "An entity responsible" },
    { uri: "http://purl.org/dc/terms/title", label: "Title", description: null },
    { uri: "http://www.w3.org/2000/01/rdf-schema#label", label: "label" },
  ];

  it("groups terms by namespace", () => {
    const tree = buildTermTree(terms);
    expect(tree).toHaveLength(2);
    const dcterms = tree.find((g) => g.namespace === "http://purl.org/dc/terms/");
    expect(dcterms?.children).toHaveLength(2);
  });

  it("sorts namespaces by short prefix and leaves by label", () => {
    const tree = buildTermTree(terms);
    // rdf-schema sorts before terms
    expect(tree[0]?.shortPrefix).toBe("rdf-schema");
    expect(tree[1]?.shortPrefix).toBe("terms");
    // Creator before Title within dcterms
    expect(tree[1]?.children.map((c) => c.label)).toEqual(["Creator", "Title"]);
  });

  it("de-duplicates terms with the same uri", () => {
    const dupes: TermLike[] = [
      { uri: "http://purl.org/dc/terms/creator", label: "Creator" },
      { uri: "http://purl.org/dc/terms/creator", label: "creator (alt)" },
    ];
    const tree = buildTermTree(dupes);
    expect(countTerms(tree)).toBe(1);
    expect(tree[0]?.children[0]?.label).toBe("Creator");
  });

  it("falls back to local name when label is blank", () => {
    const tree = buildTermTree([{ uri: "http://purl.org/dc/terms/subject", label: "  " }]);
    expect(tree[0]?.children[0]?.label).toBe("subject");
  });

  it("skips null/empty-uri rows defensively", () => {
    const tree = buildTermTree([
      { uri: "", label: "x" },
      { uri: "http://purl.org/dc/terms/creator", label: "Creator" },
    ] as TermLike[]);
    expect(countTerms(tree)).toBe(1);
  });

  it("returns an empty tree for an empty input", () => {
    expect(buildTermTree([])).toEqual([]);
    expect(countTerms([])).toBe(0);
  });

  it("produces deterministic output across repeated calls", () => {
    expect(buildTermTree(terms)).toEqual(buildTermTree(terms));
  });
});

describe("buildTermGraph", () => {
  const tree = buildTermTree([
    { uri: "http://purl.org/dc/terms/creator", label: "Creator" },
    { uri: "http://purl.org/dc/terms/title", label: "Title" },
  ]);

  it("emits one namespace node + one node per term", () => {
    const { nodes } = buildTermGraph(tree);
    // 1 namespace + 2 terms
    expect(nodes).toHaveLength(3);
    expect(nodes.filter((n) => n.category === 0)).toHaveLength(1);
    expect(nodes.filter((n) => n.category === 1)).toHaveLength(2);
  });

  it("connects each term to its namespace with an edge", () => {
    const { nodes, edges } = buildTermGraph(tree);
    const nsId = nodes.find((n) => n.category === 0)!.id;
    expect(edges).toHaveLength(2);
    expect(edges.every((e) => e.source === nsId)).toBe(true);
  });

  it("uses the full IRI as the node id (no numeric ids)", () => {
    const { nodes } = buildTermGraph(tree);
    const leaf = nodes.find((n) => n.category === 1)!;
    expect(leaf.id).toBe(leaf.uri);
    expect(leaf.id).toContain("http://");
  });

  it("returns empty arrays for an empty tree", () => {
    expect(buildTermGraph([])).toEqual({ nodes: [], edges: [] });
  });
});
