/**
 * MCP endpoint URL helpers.
 *
 * Both URLs sit at fixed paths under the same origin that serves the
 * frontend, so the only variable is the origin itself — we accept it as
 * an argument rather than reading {@code window.location} so callers
 * (e.g. unit tests, SSR) can pass in whatever origin they need.
 *
 * Phase 1 (aidocs/88) — these are the native Quarkus MCP routes; the
 * legacy Python sidecar still answers at {@code /mcp/sse} (no {@code /v2}).
 */

/** SSE endpoint (legacy MCP transport — used by Claude Desktop). */
export function mcpSseUrl(origin: string): string {
  return `${stripTrailingSlash(origin)}/v2/mcp/sse`;
}

/** Streamable HTTP endpoint (newer MCP transport — single POST, optional SSE response). */
export function mcpStreamableUrl(origin: string): string {
  return `${stripTrailingSlash(origin)}/v2/mcp`;
}

function stripTrailingSlash(s: string): string {
  return s.endsWith("/") ? s.slice(0, -1) : s;
}
