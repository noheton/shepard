/**
 * Tests for the provenance-log empty-state helper. Closes
 * RDM-2026-05-24-004 (Bucket D copy fix). The two-branch contract
 * is the load-bearing API consumed by `DataObjectProvLog.vue`.
 */
import { describe, expect, it } from "vitest";
import {
  emptyStateHint,
  emptyStateLabel,
} from "../../utils/provenanceEmptyState";

describe("provenanceEmptyState", () => {
  it("explains capture scope when no rows came back", () => {
    expect(emptyStateLabel(0)).toBe("No provenance events recorded yet");
    const hint = emptyStateHint(0);
    expect(hint).toMatch(/write actions/i);
    expect(hint).toMatch(/create \/ update \/ delete/i);
    expect(hint).toMatch(/numeric-id/i);
    expect(hint).toMatch(/aidocs\/55/);
  });

  it("blames the in-page filter when rows exist but none match", () => {
    expect(emptyStateLabel(7)).toBe("No matching provenance events");
    expect(emptyStateHint(7)).toMatch(/filter/i);
    expect(emptyStateHint(7)).not.toMatch(/write actions/i);
  });

  it("treats one row as 'rows exist'", () => {
    // Boundary: a single row is enough to mean "the panel works,
    // the filter just hid every event".
    expect(emptyStateLabel(1)).toBe("No matching provenance events");
    expect(emptyStateHint(1)).toMatch(/filter/i);
  });
});
