/**
 * J1e-PR-06-AUTOFETCH-02 — unit tests for the "Open in Jupyter" URL construction
 * in DataObjectNotebooksPane.
 *
 * The component builds a `?file=<encodeURIComponent(downloadUrl)>` query param
 * so that the JupyterHub `pre_spawn_hook` (shipped in J1e-PR-06-AUTOFETCH-01)
 * can auto-fetch the notebook into the user's volume on spawn.
 *
 * Tests cover the pure helper logic extracted from the component without
 * mounting the full Nuxt / Vuetify tree.
 */

import { describe, it, expect } from "vitest";

// ── Inlined helpers from DataObjectNotebooksPane ──────────────────────────────

/**
 * Mirrors the component's `downloadUrl()` — builds the canonical Shepard
 * file content URL for a given appId.
 *
 * In production, `backendV2ApiUrl` or `backendApiUrl` comes from
 * useRuntimeConfig().public; in tests we pass the base directly.
 */
function downloadUrl(base: string, appId: string): string {
  return `${base}/v2/files/${encodeURIComponent(appId)}/content`;
}

/**
 * Mirrors the component's `openInJupyter(notebookAppId)` URL-building logic.
 * Returns the full URL that would be passed to `window.open`, or `null` when
 * `preferredJupyterUrl` is empty (the component shows the URL field instead).
 */
function buildJupyterOpenUrl(
  preferredJupyterUrl: string,
  notebookAppId: string,
  apiBase: string,
): string | null {
  if (!preferredJupyterUrl) return null;
  const base = preferredJupyterUrl.replace(/\/$/, "");
  const fileUrl = downloadUrl(apiBase, notebookAppId);
  return `${base}/?file=${encodeURIComponent(fileUrl)}`;
}

// ── Tests ─────────────────────────────────────────────────────────────────────

const HUB = "https://hub.example.org";
const API = "https://shepard.example.org";
const NB_APP_ID = "01926e2e-0000-7000-a000-aabbccddeeff";

describe("DataObjectNotebooksPane — openInJupyter URL construction", () => {
  it("builds the canonical ?file= URL for a known notebook appId", () => {
    const url = buildJupyterOpenUrl(HUB, NB_APP_ID, API);
    const expected = `${HUB}/?file=${encodeURIComponent(
      `${API}/v2/files/${encodeURIComponent(NB_APP_ID)}/content`,
    )}`;
    expect(url).toBe(expected);
  });

  it("strips a trailing slash from the JupyterHub base URL before appending /?file=", () => {
    const url = buildJupyterOpenUrl(`${HUB}/`, NB_APP_ID, API);
    // Must not produce double-slash: hub.example.org//?file=...
    expect(url).not.toContain("//?" );
    expect(url).toMatch(/^https:\/\/hub\.example\.org\/\?file=/);
  });

  it("percent-encodes the download URL so slashes and colons are safe in the query param", () => {
    const url = buildJupyterOpenUrl(HUB, NB_APP_ID, API);
    expect(url).toBeDefined();
    const qs = new URL(url!).searchParams;
    const fileParam = qs.get("file");
    // Decoded value must be a valid absolute URL
    expect(fileParam).not.toBeNull();
    const decoded = decodeURIComponent(fileParam!);
    expect(() => new URL(decoded)).not.toThrow();
    expect(decoded).toContain("/v2/files/");
    expect(decoded).toContain(NB_APP_ID);
  });

  it("encodes special characters in the appId correctly", () => {
    const specialId = "abc/def+ghi=xyz";
    const url = buildJupyterOpenUrl(HUB, specialId, API);
    const fileParam = new URL(url!).searchParams.get("file")!;
    // searchParams.get() already decodes the outer percent-encoding, so
    // fileParam is the download URL with the appId still encoded in the path.
    // Verify the encoded appId appears in the decoded file URL.
    expect(fileParam).toContain(encodeURIComponent(specialId));
  });

  it("returns null when preferredJupyterUrl is empty (no URL set yet)", () => {
    const url = buildJupyterOpenUrl("", NB_APP_ID, API);
    expect(url).toBeNull();
  });

  it("the decoded ?file= value starts with the API base URL", () => {
    const url = buildJupyterOpenUrl(HUB, NB_APP_ID, API);
    const fileParam = new URL(url!).searchParams.get("file")!;
    const decoded = decodeURIComponent(fileParam);
    expect(decoded).toMatch(new RegExp(`^${API}/v2/files/`));
  });

  it("the full URL contains the JupyterHub host", () => {
    const url = buildJupyterOpenUrl(HUB, NB_APP_ID, API);
    expect(url).toContain("hub.example.org");
  });
});
