import { describe, it, expect } from "vitest";
import { mcpSseUrl, mcpStreamableUrl } from "../../utils/mcpEndpoints";

describe("mcpEndpoints", () => {
  it("composes SSE URL from origin", () => {
    expect(mcpSseUrl("https://shepard.nuclide.systems")).toBe(
      "https://shepard.nuclide.systems/v2/mcp/sse",
    );
  });

  it("composes streamable HTTP URL from origin", () => {
    expect(mcpStreamableUrl("https://shepard.nuclide.systems")).toBe(
      "https://shepard.nuclide.systems/v2/mcp",
    );
  });

  it("tolerates trailing slash on origin", () => {
    expect(mcpSseUrl("http://localhost:3000/")).toBe("http://localhost:3000/v2/mcp/sse");
    expect(mcpStreamableUrl("http://localhost:3000/")).toBe("http://localhost:3000/v2/mcp");
  });

  it("works on a non-default port", () => {
    expect(mcpSseUrl("http://localhost:8080")).toBe("http://localhost:8080/v2/mcp/sse");
  });
});
