/**
 * Regression tests for toShortDateString (frontend/utils/helpers.ts).
 *
 * Root cause of the AFP-collection page crash (BUG-DATE-COERCE): the function
 * was typed `Date | null` and did `date?.toLocaleDateString(...)`, but JSON
 * carries dates as ISO strings. A string value is not null, so `?.` did not
 * guard it — `"2026-..."?.toLocaleDateString` is `undefined`, and calling it
 * threw "is not a function" during a component setup(), blanking the page.
 */

import { describe, it, expect } from "vitest";
import { toShortDateString } from "~/utils/helpers";

describe("toShortDateString", () => {
  it("formats a Date", () => {
    expect(toShortDateString(new Date("2026-06-26T10:00:00Z"))).toMatch(/2026/);
  });

  it("formats an ISO string without throwing (the crash case)", () => {
    expect(() => toShortDateString("2026-06-26T10:00:00Z")).not.toThrow();
    expect(toShortDateString("2026-06-26T10:00:00Z")).toMatch(/2026/);
  });

  it("formats an epoch-millis number", () => {
    expect(toShortDateString(Date.parse("2026-06-26T10:00:00Z"))).toMatch(/2026/);
  });

  it("returns undefined for null/undefined", () => {
    expect(toShortDateString(null)).toBeUndefined();
    expect(toShortDateString(undefined)).toBeUndefined();
  });

  it("returns undefined for an unparseable string instead of throwing", () => {
    expect(() => toShortDateString("not-a-date")).not.toThrow();
    expect(toShortDateString("not-a-date")).toBeUndefined();
  });
});
