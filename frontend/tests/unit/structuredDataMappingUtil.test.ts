/**
 * UI-SDREF-NO-CONTENT-001 — unit tests for the SD mapping util.
 * Confirms the per-row Download action is wired with a sensible
 * filename and only enabled when payload is fetchable.
 */
import { describe, it, expect } from "vitest";
import {
  buildStructuredDataFilename,
  mapStructuredDataToDataTableItem,
} from "~/components/context/display-components/structured-data-references/structuredDataMappingUtil";
import type { StructuredDataMeta } from "~/components/context/display-components/structured-data-references/structuredDataReferenceTypes";

describe("buildStructuredDataFilename", () => {
  it("uses the structured-data name with .json suffix", () => {
    expect(buildStructuredDataFilename("tr-001-config", "abc")).toBe(
      "tr-001-config.json",
    );
  });

  it("does not double-append .json when name already ends in it", () => {
    expect(buildStructuredDataFilename("settings.json", "abc")).toBe(
      "settings.json",
    );
  });

  it("falls back to the oid when name is empty", () => {
    expect(buildStructuredDataFilename("", "65abc123")).toBe("65abc123.json");
  });

  it("falls back to a generic name when both are missing", () => {
    expect(buildStructuredDataFilename(null, null)).toBe(
      "structured-data.json",
    );
  });

  it("trims surrounding whitespace from the name", () => {
    expect(buildStructuredDataFilename("  spaces  ", "x")).toBe("spaces.json");
  });
});

describe("mapStructuredDataToDataTableItem", () => {
  const baseMeta: StructuredDataMeta = {
    name: "tr-001-config",
    oid: "65abc123",
    payload: '{"propellant":"LOX/LH2"}',
    availability: "available",
    createdAt: new Date("2026-05-30T12:00:00Z"),
  };

  it("populates the download action with payload + filename when available", () => {
    const item = mapStructuredDataToDataTableItem(baseMeta);
    expect(item.actions.download.enabled).toBe(true);
    expect(item.actions.download.filename).toBe("tr-001-config.json");
    expect(item.actions.download.payload).toBe('{"propellant":"LOX/LH2"}');
  });

  it("disables download when the structured data is not available", () => {
    const item = mapStructuredDataToDataTableItem({
      ...baseMeta,
      availability: "deleted",
    });
    expect(item.actions.download.enabled).toBe(false);
    expect(item.actions.showPayload.enabled).toBe(false);
  });

  it("disables download when the oid is missing", () => {
    const item = mapStructuredDataToDataTableItem({
      ...baseMeta,
      oid: "",
    });
    expect(item.actions.download.enabled).toBe(false);
  });

  it("preserves the existing showPayload contract for the View dialog", () => {
    const item = mapStructuredDataToDataTableItem(baseMeta);
    expect(item.actions.showPayload.enabled).toBe(true);
    expect(item.actions.showPayload.payload).toBe('{"propellant":"LOX/LH2"}');
  });
});
