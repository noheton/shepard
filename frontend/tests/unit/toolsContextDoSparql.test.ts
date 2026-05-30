import { describe, it, expect } from "vitest";
import { buildDataObjectSparqlUrl } from "../../utils/dataObjectSparqlLink";

function decodeQuery(url: string): string {
  const raw = url.split("?query=").at(1) ?? "";
  return decodeURIComponent(raw);
}

describe("buildDataObjectSparqlUrl (TOOLS-CONTEXT-DO-SPARQL)", () => {
  it("routes to /semantic/sparql", () => {
    const url = buildDataObjectSparqlUrl("test-app-id-123");
    expect(url.startsWith("/semantic/sparql?query=")).toBe(true);
  });

  it("embeds the appId in the query subject", () => {
    const decoded = decodeQuery(buildDataObjectSparqlUrl("abc-def-456"));
    expect(decoded).toContain("<urn:shepard:abc-def-456>");
  });

  it("produces a SELECT query", () => {
    const decoded = decodeQuery(buildDataObjectSparqlUrl("abc"));
    expect(decoded.trimStart().startsWith("SELECT")).toBe(true);
  });

  it("percent-encodes the query param", () => {
    const url = buildDataObjectSparqlUrl("my-id");
    expect(url).not.toContain(" ");
    expect(url).not.toContain("{");
    expect(url).not.toContain("}");
  });

  it("includes a LIMIT clause", () => {
    const decoded = decodeQuery(buildDataObjectSparqlUrl("my-id"));
    expect(decoded).toContain("LIMIT 50");
  });
});
