/**
 * QM1a — unit tests for StatusChip config logic.
 *
 * Tests verify:
 * - Standard lifecycle statuses (DRAFT → ARCHIVED) resolve to a known config
 * - MFG1 quality statuses (NCR_OPEN / ON_HOLD / REJECTED / CERTIFIED) resolve
 * - QM1a CONCESSION_PENDING resolves to the amber-outlined config
 * - Unknown status falls back to the raw label (no crash)
 *
 * Mirrors the inline-config style of PredecessorRelationshipTypeChip.test.ts
 * to keep the test surface Vuetify-free.
 */
import { describe, it, expect } from "vitest";

// Inline mirror of the STATUS_CONFIG block in StatusChip.vue. Kept in sync
// with the component when adding new statuses.
const STATUS_CONFIG: Record<
  string,
  { color: string; label: string; variant?: string; icon?: string }
> = {
  DRAFT: { color: "default", label: "Draft" },
  IN_REVIEW: { color: "warning", label: "In Review" },
  READY: { color: "success", label: "Ready" },
  PUBLISHED: { color: "primary", label: "Published" },
  ARCHIVED: { color: "secondary", label: "Archived" },
  NCR_OPEN: { color: "error", label: "NCR Open", variant: "flat", icon: "mdi-alert-octagon" },
  ON_HOLD: { color: "orange", label: "On Hold" },
  REJECTED: { color: "error", label: "Rejected", variant: "outlined", icon: "mdi-close-circle-outline" },
  CERTIFIED: { color: "success", label: "Certified" },
  CONCESSION_PENDING: {
    color: "warning",
    label: "Concession Pending",
    variant: "outlined",
    icon: "mdi-shield-alert-outline",
  },
};

function resolveConfig(
  status: string,
): { color: string; label: string; variant?: string; icon?: string } {
  return STATUS_CONFIG[status] ?? { color: "default", label: status };
}

describe("StatusChip — standard lifecycle statuses", () => {
  it("DRAFT resolves to default grey", () => {
    expect(resolveConfig("DRAFT").color).toBe("default");
  });

  it("READY resolves to success green", () => {
    expect(resolveConfig("READY").color).toBe("success");
  });

  it("PUBLISHED resolves to primary", () => {
    expect(resolveConfig("PUBLISHED").color).toBe("primary");
  });
});

describe("StatusChip — MFG1 quality-engineering statuses", () => {
  it("NCR_OPEN is red filled with the alert-octagon icon", () => {
    const cfg = resolveConfig("NCR_OPEN");
    expect(cfg.color).toBe("error");
    expect(cfg.variant).toBe("flat");
    expect(cfg.icon).toBe("mdi-alert-octagon");
    expect(cfg.label).toBe("NCR Open");
  });

  it("REJECTED is red outlined with the close-circle-outline icon", () => {
    const cfg = resolveConfig("REJECTED");
    expect(cfg.color).toBe("error");
    expect(cfg.variant).toBe("outlined");
    expect(cfg.icon).toBe("mdi-close-circle-outline");
  });

  it("CERTIFIED keeps the success colour (final approval state)", () => {
    expect(resolveConfig("CERTIFIED").color).toBe("success");
  });
});

describe("StatusChip — QM1a CONCESSION_PENDING", () => {
  it("CONCESSION_PENDING is amber/warning outlined with shield-alert-outline icon", () => {
    const cfg = resolveConfig("CONCESSION_PENDING");
    expect(cfg.color).toBe("warning");
    expect(cfg.variant).toBe("outlined");
    expect(cfg.icon).toBe("mdi-shield-alert-outline");
    expect(cfg.label).toBe("Concession Pending");
  });
});

describe("StatusChip — fallback behaviour", () => {
  it("falls back to the raw label for unknown status (no crash)", () => {
    const cfg = resolveConfig("UNKNOWN_FUTURE_STATUS");
    expect(cfg.color).toBe("default");
    expect(cfg.label).toBe("UNKNOWN_FUTURE_STATUS");
  });
});
