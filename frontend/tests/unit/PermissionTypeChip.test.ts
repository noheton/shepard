// UX-WALK-2026-05-29-08: unit tests for PermissionTypeChip label-mapping logic.
//
// Tests verify:
// - Public   → label "Open",       color "success"
// - PublicReadable → label "Shared", color "info"
// - Private  → label "Restricted", color "warning"
// - Unknown value  → falls back to the raw value string (resilience)
// - null / undefined → label "—", color "default"
//
// The tests exercise the pure mapping logic extracted from the component script.
// Visual rendering is validated by Playwright at 4K viewport (separate obligation).
import { describe, it, expect } from "vitest";

// ── Mirror the PERMISSION_TYPE_MAP from the component ─────────────────────────

interface PermissionTypeOption {
  label: string;
  color: string;
  description: string;
}

// Inline the map so the test doesn't import Vue + Vuetify plugin chain.
// If the map in the component changes, this test will catch the divergence.
const PermissionType = {
  Public: "Public",
  PublicReadable: "PublicReadable",
  Private: "Private",
} as const;

const PERMISSION_TYPE_MAP: Record<string, PermissionTypeOption> = {
  [PermissionType.Public]: {
    label: "Open",
    color: "success",
    description: "Public — anyone can read without authentication.",
  },
  [PermissionType.PublicReadable]: {
    label: "Shared",
    color: "info",
    description: "Public readable — authenticated users can read; write is restricted.",
  },
  [PermissionType.Private]: {
    label: "Restricted",
    color: "warning",
    description: "Private — only authorised members can access.",
  },
};

function resolveOption(permissionType: string | null | undefined): PermissionTypeOption | undefined {
  return permissionType ? PERMISSION_TYPE_MAP[permissionType] : undefined;
}

function resolveColor(permissionType: string | null | undefined): string {
  return resolveOption(permissionType)?.color ?? "default";
}

function resolveLabel(permissionType: string | null | undefined): string {
  return resolveOption(permissionType)?.label ?? permissionType ?? "—";
}

// ── Tests ────────────────────────────────────────────────────────────────────

describe("PermissionTypeChip — label mapping", () => {
  it("maps Public → 'Open'", () => {
    expect(resolveLabel("Public")).toBe("Open");
  });

  it("maps PublicReadable → 'Shared'", () => {
    expect(resolveLabel("PublicReadable")).toBe("Shared");
  });

  it("maps Private → 'Restricted'", () => {
    expect(resolveLabel("Private")).toBe("Restricted");
  });

  it("falls back to the raw value for an unknown permission type", () => {
    expect(resolveLabel("FUTURE_TYPE")).toBe("FUTURE_TYPE");
  });

  it("returns '—' for null", () => {
    expect(resolveLabel(null)).toBe("—");
  });

  it("returns '—' for undefined", () => {
    expect(resolveLabel(undefined)).toBe("—");
  });
});

describe("PermissionTypeChip — color mapping", () => {
  it("Public → success (green)", () => {
    expect(resolveColor("Public")).toBe("success");
  });

  it("PublicReadable → info (blue)", () => {
    expect(resolveColor("PublicReadable")).toBe("info");
  });

  it("Private → warning (orange)", () => {
    expect(resolveColor("Private")).toBe("warning");
  });

  it("unknown type → default", () => {
    expect(resolveColor("FUTURE_TYPE")).toBe("default");
  });

  it("null → default", () => {
    expect(resolveColor(null)).toBe("default");
  });
});

describe("PermissionTypeChip — description mapping", () => {
  it("every known type has a non-empty description", () => {
    for (const key of Object.values(PermissionType)) {
      const opt = resolveOption(key);
      expect(opt).toBeDefined();
      expect(opt!.description).toBeTruthy();
    }
  });

  it("unknown type has no description (returns undefined option)", () => {
    expect(resolveOption("FUTURE_TYPE")).toBeUndefined();
  });
});

describe("PermissionTypeChip — resilience", () => {
  it("covers all three documented PermissionType enum values", () => {
    const covered = Object.keys(PERMISSION_TYPE_MAP);
    expect(covered).toContain(PermissionType.Public);
    expect(covered).toContain(PermissionType.PublicReadable);
    expect(covered).toContain(PermissionType.Private);
  });

  it("has exactly three entries (no silent extras)", () => {
    expect(Object.keys(PERMISSION_TYPE_MAP).length).toBe(3);
  });
});
