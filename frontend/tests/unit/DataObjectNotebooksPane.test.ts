/**
 * task #240 (2026-05-30) — "Open in JupyterHub" affordance now reads from
 * the admin-configured `:JupyterConfig` singleton, NOT from the per-user
 * `editor.preferredJupyter` preference (removed in the same commit).
 *
 * These tests cover the pure gating + URL-build logic that lives inline in
 * `DataObjectNotebooksPane.vue`. We test the logic units rather than mounting
 * the component because the Vuetify + Nuxt rendering chain requires the full
 * app context — same convention as `PersonalDigest.test.ts`.
 */

import { describe, it, expect } from "vitest";
import type { JupyterConfigIO } from "~/composables/context/admin/useJupyterConfig";

// ── Logic units (mirror the inline functions in DataObjectNotebooksPane.vue) ──

/** Returns true when the admin has both enabled JupyterHub AND set a hubUrl. */
function jupyterAffordanceVisible(cfg: JupyterConfigIO | null): boolean {
  return (
    !!cfg &&
    cfg.enabled === true &&
    !!cfg.hubUrl &&
    cfg.hubUrl.length > 0
  );
}

/** Build the launch URL for a singleton FileReference appId. */
function jupyterLaunchUrl(
  cfg: JupyterConfigIO | null,
  appId: string,
  v2Base: string,
): string | null {
  if (!cfg || !cfg.enabled || !cfg.hubUrl) return null;
  const downloadUrl = `${v2Base}/v2/references/${encodeURIComponent(appId)}/content`;
  const hubBase = cfg.hubUrl.replace(/\/$/, "");
  return `${hubBase}/hub/spawn?file=${encodeURIComponent(downloadUrl)}`;
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe("DataObjectNotebooksPane — jupyterAffordanceVisible (task #240)", () => {
  it("hides the button when admin config is null (unloaded)", () => {
    expect(jupyterAffordanceVisible(null)).toBe(false);
  });

  it("hides the button when admin has disabled JupyterHub", () => {
    expect(
      jupyterAffordanceVisible({ enabled: false, hubUrl: "https://hub.example.org" }),
    ).toBe(false);
  });

  it("hides the button when admin has not set a hubUrl", () => {
    expect(jupyterAffordanceVisible({ enabled: true, hubUrl: null })).toBe(false);
    expect(jupyterAffordanceVisible({ enabled: true, hubUrl: "" })).toBe(false);
  });

  it("shows the button when admin has enabled JupyterHub AND set a hubUrl", () => {
    expect(
      jupyterAffordanceVisible({ enabled: true, hubUrl: "https://hub.example.org" }),
    ).toBe(true);
  });
});

describe("DataObjectNotebooksPane — jupyterLaunchUrl (task #240)", () => {
  const v2Base = "https://shepard-api.example.org";
  const enabledCfg: JupyterConfigIO = {
    enabled: true,
    hubUrl: "https://hub.example.org",
  };

  it("returns null when the admin gate is closed", () => {
    expect(jupyterLaunchUrl(null, "abc", v2Base)).toBeNull();
    expect(
      jupyterLaunchUrl({ enabled: false, hubUrl: "https://hub.example.org" }, "abc", v2Base),
    ).toBeNull();
    expect(jupyterLaunchUrl({ enabled: true, hubUrl: null }, "abc", v2Base)).toBeNull();
  });

  it("builds {hubUrl}/hub/spawn?file={downloadUrl} when gate is open", () => {
    const url = jupyterLaunchUrl(enabledCfg, "fr1b-app-id", v2Base);
    expect(url).toBe(
      "https://hub.example.org/hub/spawn?file=" +
        encodeURIComponent("https://shepard-api.example.org/v2/references/fr1b-app-id/content"),
    );
  });

  it("strips trailing slash from the hub URL before composing", () => {
    const url = jupyterLaunchUrl(
      { enabled: true, hubUrl: "https://hub.example.org/" },
      "abc",
      v2Base,
    );
    expect(url).toContain("https://hub.example.org/hub/spawn?file=");
    expect(url).not.toContain("https://hub.example.org//hub/spawn");
  });

  it("URL-encodes the appId in the download URL", () => {
    const url = jupyterLaunchUrl(enabledCfg, "id with spaces", v2Base);
    expect(url).toContain(encodeURIComponent("id%20with%20spaces"));
  });
});
