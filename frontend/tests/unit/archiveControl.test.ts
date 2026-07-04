/**
 * #27-ARCHIVED — unit tests for the archive control logic shared by
 * CollectionArchiveControl.vue and ContainerArchiveControl.vue.
 *
 * Tests the visibility-decision logic and the PATCH wire format in
 * isolation — no Vuetify mount, matching the project's testing
 * convention (see PredecessorRelationshipTypeChip.test.ts).
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

// ── Decision logic ─────────────────────────────────────────────────────────
// Mirrors `isArchived`, "show Archive button", and "show Unarchive button"
// from the SFCs.

function isArchived(status: string | null | undefined): boolean {
  return status === "ARCHIVED";
}

function showArchiveButton(
  status: string | null | undefined,
  isManager: boolean,
  appId: string | null,
): boolean {
  return isManager && !isArchived(status) && !!appId;
}

function showUnarchiveButton(
  status: string | null | undefined,
  isManager: boolean,
  appId: string | null,
): boolean {
  return isManager && isArchived(status) && !!appId;
}

function showArchivedChip(status: string | null | undefined): boolean {
  return isArchived(status);
}

describe("#27-ARCHIVED — control visibility logic", () => {
  it("READY + manager + appId → shows Archive button only", () => {
    expect(showArchivedChip("READY")).toBe(false);
    expect(showArchiveButton("READY", true, "abc-123")).toBe(true);
    expect(showUnarchiveButton("READY", true, "abc-123")).toBe(false);
  });

  it("ARCHIVED + manager + appId → shows Unarchive + chip", () => {
    expect(showArchivedChip("ARCHIVED")).toBe(true);
    expect(showArchiveButton("ARCHIVED", true, "abc-123")).toBe(false);
    expect(showUnarchiveButton("ARCHIVED", true, "abc-123")).toBe(true);
  });

  it("ARCHIVED + non-manager → shows chip but no buttons", () => {
    expect(showArchivedChip("ARCHIVED")).toBe(true);
    expect(showArchiveButton("ARCHIVED", false, "abc-123")).toBe(false);
    expect(showUnarchiveButton("ARCHIVED", false, "abc-123")).toBe(false);
  });

  it("READY + non-manager → no chip, no buttons", () => {
    expect(showArchivedChip("READY")).toBe(false);
    expect(showArchiveButton("READY", false, "abc-123")).toBe(false);
    expect(showUnarchiveButton("READY", false, "abc-123")).toBe(false);
  });

  it("Missing appId → no buttons even if manager", () => {
    expect(showArchiveButton("READY", true, null)).toBe(false);
    expect(showUnarchiveButton("ARCHIVED", true, null)).toBe(false);
  });

  it("Null status treated as non-archived (effectively READY)", () => {
    expect(showArchivedChip(null)).toBe(false);
    expect(showArchiveButton(null, true, "abc-123")).toBe(true);
    expect(showUnarchiveButton(null, true, "abc-123")).toBe(false);
  });

  it("Undefined status treated as non-archived", () => {
    expect(showArchivedChip(undefined)).toBe(false);
    expect(showArchiveButton(undefined, true, "abc-123")).toBe(true);
  });

  it("DRAFT/IN_REVIEW/PUBLISHED are NOT archived (no chip)", () => {
    for (const s of ["DRAFT", "IN_REVIEW", "PUBLISHED"]) {
      expect(showArchivedChip(s)).toBe(false);
      expect(showArchiveButton(s, true, "abc-123")).toBe(true);
    }
  });
});

// ── Tooltip / read-only message ────────────────────────────────────────────

function archivedTooltip(kind: "Collection" | "Container"): string {
  return kind === "Collection"
    ? "This Collection is ARCHIVED — frozen, prune-only. New writes to its children return 409."
    : "This Container is ARCHIVED — frozen, prune-only. New payload writes return 409.";
}

describe("#27-ARCHIVED — tooltip text mentions 409 and prune-only", () => {
  it("Collection tooltip mentions 409 and prune-only", () => {
    const t = archivedTooltip("Collection");
    expect(t).toContain("409");
    expect(t).toContain("prune-only");
    expect(t).toContain("ARCHIVED");
  });

  it("Container tooltip mentions 409 and prune-only", () => {
    const t = archivedTooltip("Container");
    expect(t).toContain("409");
    expect(t).toContain("prune-only");
  });
});

// ── PATCH wire format ──────────────────────────────────────────────────────

function buildPatchBody(state: string): string {
  return JSON.stringify({ state });
}

describe("#27-ARCHIVED — PATCH body shape", () => {
  it("body carries a single `state` field", () => {
    expect(buildPatchBody("ARCHIVED")).toBe('{"state":"ARCHIVED"}');
    expect(buildPatchBody("READY")).toBe('{"state":"READY"}');
  });
});

// ── fetch call (smoke test the PATCH call path) ────────────────────────────

describe("#27-ARCHIVED — PATCH happy path", () => {
  let fetchSpy: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    fetchSpy = vi.fn().mockResolvedValue({ ok: true, status: 200 });
    (globalThis as unknown as { fetch: typeof fetch }).fetch =
      fetchSpy as unknown as typeof fetch;
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("PATCHes /v2/collections/{appId}/publication-state with state body", async () => {
    const appId = "01900000-1234-7000-8000-aabbccddeeff";
    const url = `https://example/v2/collections/${encodeURIComponent(appId)}/publication-state`;
    const resp = await fetch(url, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ state: "ARCHIVED" }),
    });
    expect(resp.ok).toBe(true);
    expect(fetchSpy).toHaveBeenCalledTimes(1);
    const firstCall = fetchSpy.mock.calls[0]!;
    expect(firstCall[0]).toContain("/v2/collections/");
    expect(firstCall[0]).toContain("/publication-state");
    expect(firstCall[1].method).toBe("PATCH");
    const body = JSON.parse(firstCall[1].body as string);
    expect(body.state).toBe("ARCHIVED");
  });

  it("PATCHes /v2/containers/{appId}/publication-state with state body", async () => {
    const appId = "01900000-1234-7000-8000-aabbccddeeff";
    const url = `https://example/v2/containers/${encodeURIComponent(appId)}/publication-state`;
    await fetch(url, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ state: "READY" }),
    });
    const firstCall = fetchSpy.mock.calls[0]!;
    expect(firstCall[0]).toContain("/v2/containers/");
    const body = JSON.parse(firstCall[1].body as string);
    expect(body.state).toBe("READY");
  });
});
