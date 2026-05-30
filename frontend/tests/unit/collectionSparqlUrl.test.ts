import { describe, it, expect } from "vitest";
import { buildCollectionSparqlUrl } from "~/utils/collectionSparqlUrl";

describe("buildCollectionSparqlUrl", () => {
  it("returns a path starting with /semantic/sparql", () => {
    const url = buildCollectionSparqlUrl("abc-123");
    expect(url.startsWith("/semantic/sparql")).toBe(true);
  });

  it("includes the collectionAppId in the encoded query", () => {
    const appId = "01938fbc-dead-beef-0000-000000000001";
    const url = buildCollectionSparqlUrl(appId);
    const decoded = decodeURIComponent(url.split("?query=")[1] ?? "");
    expect(decoded).toContain(appId);
  });

  it("URL-encodes the query parameter", () => {
    const url = buildCollectionSparqlUrl("test-id");
    expect(url).toContain("query=");
    expect(url).not.toContain(" ");
  });

  it("the decoded query is a valid SPARQL SELECT", () => {
    const url = buildCollectionSparqlUrl("some-id");
    const decoded = decodeURIComponent(url.split("?query=")[1] ?? "");
    expect(decoded).toMatch(/^\s*SELECT/i);
    expect(decoded).toMatch(/WHERE/i);
    expect(decoded).toMatch(/LIMIT/i);
  });

  it("uses the repoAppId=internal default via the /semantic/sparql path", () => {
    const url = buildCollectionSparqlUrl("any-id");
    expect(url).toMatch(/^\/semantic\/sparql/);
  });
});
