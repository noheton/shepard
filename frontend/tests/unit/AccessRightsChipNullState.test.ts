/**
 * UX-WALK-2026-05-29-08 — AccessRightsChip null / undefined / unset render path.
 *
 * The AccessRightsChip component now accepts `accessRights: string | null |
 * undefined`. When the prop is absent (pre-LIC1 collections), it must render
 * a "Not set" chip rather than a bare dash. This file tests the computed
 * properties logic mirrored from the component.
 */
import { describe, it, expect } from "vitest";
import { getAccessRightsOption } from "../../utils/spdxLicenses";

// ── Logic mirror ──────────────────────────────────────────────────────────────
// These functions replicate exactly the computed logic inside AccessRightsChip.vue
// so we can unit-test them without the full Vuetify rendering chain.

function resolveChip(accessRights: string | null | undefined) {
  const isUnset = !accessRights;
  const option = accessRights ? getAccessRightsOption(accessRights) : undefined;
  return {
    isUnset,
    color: isUnset ? "default" : (option?.color ?? "default"),
    label: isUnset ? "Not set" : (option?.label ?? accessRights),
    icon: isUnset ? "mdi-help-circle-outline" : "mdi-shield-lock-outline",
    description: isUnset ? "" : (option?.description ?? ""),
  };
}

// ─────────────────────────────────────────────────────────────────────────────
describe("AccessRightsChip — null / undefined / unset path (UX-WALK-2026-05-29-08)", () => {
  it("renders 'Not set' chip for null accessRights", () => {
    const chip = resolveChip(null);
    expect(chip.isUnset).toBe(true);
    expect(chip.label).toBe("Not set");
    expect(chip.color).toBe("default");
    expect(chip.icon).toBe("mdi-help-circle-outline");
    expect(chip.description).toBe("");
  });

  it("renders 'Not set' chip for undefined accessRights", () => {
    const chip = resolveChip(undefined);
    expect(chip.isUnset).toBe(true);
    expect(chip.label).toBe("Not set");
    expect(chip.color).toBe("default");
    expect(chip.icon).toBe("mdi-help-circle-outline");
  });

  it("renders 'Not set' chip for empty string accessRights", () => {
    const chip = resolveChip("");
    expect(chip.isUnset).toBe(true);
    expect(chip.label).toBe("Not set");
  });
});

describe("AccessRightsChip — known controlled-vocabulary values", () => {
  it("OPEN renders with success color and shield icon", () => {
    const chip = resolveChip("OPEN");
    expect(chip.isUnset).toBe(false);
    expect(chip.color).toBe("success");
    expect(chip.label).toBe("Open");
    expect(chip.icon).toBe("mdi-shield-lock-outline");
    expect(chip.description).toBeTruthy();
  });

  it("RESTRICTED renders with warning color", () => {
    const chip = resolveChip("RESTRICTED");
    expect(chip.color).toBe("warning");
    expect(chip.label).toBe("Restricted");
  });

  it("CLOSED renders with error color", () => {
    const chip = resolveChip("CLOSED");
    expect(chip.color).toBe("error");
    expect(chip.label).toBe("Closed");
  });

  it("EMBARGOED renders with info color", () => {
    const chip = resolveChip("EMBARGOED");
    expect(chip.color).toBe("info");
    expect(chip.label).toBe("Embargoed");
  });
});

describe("AccessRightsChip — unknown server-side value fallback", () => {
  it("falls back to raw string label and default color for an unrecognised value", () => {
    const chip = resolveChip("SOME_FUTURE_VALUE");
    expect(chip.isUnset).toBe(false);
    expect(chip.color).toBe("default");
    expect(chip.label).toBe("SOME_FUTURE_VALUE");
    expect(chip.icon).toBe("mdi-shield-lock-outline");
  });
});
