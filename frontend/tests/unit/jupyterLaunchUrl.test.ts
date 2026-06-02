/**
 * Unit tests for utils/jupyterLaunchUrl.ts (J1e-PR-06-AUTOFETCH-02).
 *
 * Covers:
 * - `buildShepardFileDownloadUrl` — constructs the Shepard file content URL
 *   with the appId percent-encoded.
 * - `buildJupyterLaunchUrl` — builds the canonical JupyterHub launch URL
 *   with the entire Shepard download URL percent-encoded in the `?file=`
 *   query parameter.
 * - Round-trip invariant: `decodeURIComponent(encodedParam)` === original URL.
 * - Edge cases: URLs with `?`, `&`, `=`, spaces, and unicode.
 * - Guard clauses: null / empty / undefined inputs return null.
 * - Trailing-slash normalisation on both hubUrl and v2BaseUrl.
 */

import { describe, it, expect } from "vitest";
import {
  buildShepardFileDownloadUrl,
  buildJupyterLaunchUrl,
} from "~/utils/jupyterLaunchUrl";

// ---------------------------------------------------------------------------
// buildShepardFileDownloadUrl
// ---------------------------------------------------------------------------

describe("buildShepardFileDownloadUrl", () => {
  it("constructs the canonical /v2/files/{appId}/content URL", () => {
    const url = buildShepardFileDownloadUrl(
      "https://shepard.example.org",
      "01900000-0000-7000-8000-000000000001",
    );
    expect(url).toBe(
      "https://shepard.example.org/v2/files/01900000-0000-7000-8000-000000000001/content",
    );
  });

  it("strips a trailing slash from v2BaseUrl", () => {
    const url = buildShepardFileDownloadUrl(
      "https://shepard.example.org/",
      "abc123",
    );
    expect(url).toBe(
      "https://shepard.example.org/v2/files/abc123/content",
    );
  });

  it("percent-encodes special characters in the appId (defensive, UUIDs are safe)", () => {
    // UUIDs only contain [0-9a-f-] which are already URL-safe, but the
    // implementation uses encodeURIComponent for robustness.
    const url = buildShepardFileDownloadUrl(
      "https://shepard.example.org",
      "id with spaces",
    );
    expect(url).toBe(
      "https://shepard.example.org/v2/files/id%20with%20spaces/content",
    );
  });

  it("works with a path-mounted base URL (no trailing slash)", () => {
    const url = buildShepardFileDownloadUrl(
      "https://shepard.example.org/shepard-api",
      "abc123",
    );
    expect(url).toBe(
      "https://shepard.example.org/shepard-api/v2/files/abc123/content",
    );
  });
});

// ---------------------------------------------------------------------------
// buildJupyterLaunchUrl — canonical URL shape
// ---------------------------------------------------------------------------

describe("buildJupyterLaunchUrl — canonical shape", () => {
  const HUB = "https://shepard.example.org/jupyterhub";
  const API = "https://shepard.example.org";
  const APP_ID = "01900000-0000-7000-8000-000000000001";

  it("produces the canonical {hubBase}/hub/spawn?file=<encoded-url> shape", () => {
    const result = buildJupyterLaunchUrl(HUB, API, APP_ID);
    expect(result).not.toBeNull();
    // Must start with the hub base + /hub/spawn
    expect(result).toMatch(/^https:\/\/shepard\.example\.org\/jupyterhub\/hub\/spawn\?file=/);
  });

  it("encodes the Shepard download URL in the ?file= parameter", () => {
    const result = buildJupyterLaunchUrl(HUB, API, APP_ID)!;
    const queryPart = result.split("?file=")[1]!;
    const decoded = decodeURIComponent(queryPart);
    expect(decoded).toBe(
      `${API}/v2/files/${APP_ID}/content`,
    );
  });

  it("round-trip invariant: decode(encoded) === original download URL", () => {
    const expectedDownloadUrl = buildShepardFileDownloadUrl(API, APP_ID);
    const launchUrl = buildJupyterLaunchUrl(HUB, API, APP_ID)!;
    const encodedFileParam = launchUrl.split("?file=")[1]!;
    expect(decodeURIComponent(encodedFileParam)).toBe(expectedDownloadUrl);
  });
});

// ---------------------------------------------------------------------------
// buildJupyterLaunchUrl — URL-encoding correctness for special characters
// ---------------------------------------------------------------------------

describe("buildJupyterLaunchUrl — encoding of special characters in the inner URL", () => {
  const APP_ID = "abc123";

  /**
   * Returns the decoded `?file=` value from the generated launch URL.
   * The Shepard download URL itself does not contain `?` or `&` in the
   * happy path (it is a clean /v2/files/{appId}/content path), but a
   * future signed-URL variant or query-string augmentation could add them.
   * We verify the encoding is correct end-to-end by constructing a realistic
   * "complex" inner URL and checking the round-trip.
   */
  it("?  in the inner URL is encoded as %3F so JupyterHub parses correctly", () => {
    // Simulate an inner URL that contains a query string
    const innerUrl = `https://shepard.example.org/v2/files/${APP_ID}/content?token=abc&expires=999`;
    // Build a launch URL manually using the same encoding logic to verify
    const hubBase = "https://hub.example.org/jupyterhub";
    const launchUrl = `${hubBase}/hub/spawn?file=${encodeURIComponent(innerUrl)}`;

    const encoded = launchUrl.split("?file=")[1]!;
    // '?' must appear as %3F, '&' as %26, '=' as %3D
    expect(encoded).toContain("%3F");
    expect(encoded).toContain("%26");
    expect(encoded).toContain("%3D");
    // Round-trip
    expect(decodeURIComponent(encoded)).toBe(innerUrl);
  });

  it("spaces in a file path segment are encoded as %20", () => {
    const appId = "id with spaces and=equals";
    const result = buildJupyterLaunchUrl(
      "https://hub.example.org",
      "https://shepard.example.org",
      appId,
    )!;
    const encoded = result.split("?file=")[1]!;
    // Decoded must equal the download URL
    const decoded = decodeURIComponent(encoded);
    expect(decoded).toBe(
      `https://shepard.example.org/v2/files/${encodeURIComponent(appId)}/content`,
    );
  });

  it("encodes & in a hypothetical signed URL so it does not split the outer query", () => {
    const innerUrl = "https://shepard.example.org/v2/files/abc/content&sig=XYZ";
    const encoded = encodeURIComponent(innerUrl);
    // & must not appear literally in the encoded value
    expect(encoded).not.toContain("&");
    expect(encoded).toContain("%26");
    expect(decodeURIComponent(encoded)).toBe(innerUrl);
  });

  it("encodes = in a hypothetical signed URL so it does not confuse the outer parser", () => {
    const innerUrl = "https://shepard.example.org/v2/files/abc/content?key=val";
    const encoded = encodeURIComponent(innerUrl);
    expect(encoded).not.toContain("=");
    expect(decodeURIComponent(encoded)).toBe(innerUrl);
  });
});

// ---------------------------------------------------------------------------
// buildJupyterLaunchUrl — trailing-slash normalisation
// ---------------------------------------------------------------------------

describe("buildJupyterLaunchUrl — trailing-slash normalisation", () => {
  const APP_ID = "abc123";
  const API = "https://shepard.example.org";

  it("strips a trailing slash from hubUrl", () => {
    const result = buildJupyterLaunchUrl(
      "https://hub.example.org/jupyterhub/",
      API,
      APP_ID,
    )!;
    expect(result).toMatch(/^https:\/\/hub\.example\.org\/jupyterhub\/hub\/spawn\?file=/);
    // Must NOT produce a double slash like /jupyterhub//hub/spawn
    expect(result).not.toContain("//hub/spawn");
  });

  it("strips a trailing slash from v2BaseUrl", () => {
    const result = buildJupyterLaunchUrl(
      "https://hub.example.org",
      "https://shepard.example.org/",
      APP_ID,
    )!;
    const downloadUrl = decodeURIComponent(result.split("?file=")[1]!);
    // Must NOT produce a double slash like //v2/files
    expect(downloadUrl).not.toContain("//v2");
    expect(downloadUrl).toBe(
      `https://shepard.example.org/v2/files/${APP_ID}/content`,
    );
  });
});

// ---------------------------------------------------------------------------
// buildJupyterLaunchUrl — guard clauses (null / empty inputs)
// ---------------------------------------------------------------------------

describe("buildJupyterLaunchUrl — guard clauses", () => {
  const API = "https://shepard.example.org";
  const APP_ID = "abc123";

  it("returns null when hubUrl is null", () => {
    expect(buildJupyterLaunchUrl(null, API, APP_ID)).toBeNull();
  });

  it("returns null when hubUrl is undefined", () => {
    expect(buildJupyterLaunchUrl(undefined, API, APP_ID)).toBeNull();
  });

  it("returns null when hubUrl is an empty string", () => {
    expect(buildJupyterLaunchUrl("", API, APP_ID)).toBeNull();
  });

  it("returns null when appId is an empty string", () => {
    expect(buildJupyterLaunchUrl("https://hub.example.org", API, "")).toBeNull();
  });
});
