/**
 * TEMPLATE-ICONS-2-FE — unit tests for the icon-resolution composable
 * per aidocs/integrations/122 §6.3.
 *
 * useTemplateIcon is a pure function so no harness setup is needed —
 * the tests just verify the resolution order is template.iconKey →
 * per-kind default → generic fallback.
 */
import { describe, it, expect } from "vitest";
import {
  useTemplateIcon,
  defaultIconForKind,
} from "../../composables/useTemplateIcon";
import type { ShepardTemplateIO } from "@dlr-shepard/backend-client";

function template(iconKey: string | null | undefined = undefined): ShepardTemplateIO {
  return {
    appId: "tpl-001",
    name: "MFFD AFP Layup",
    templateKind: "DATAOBJECT_RECIPE",
    version: 1,
    body: "{}",
    retired: false,
    iconKey: iconKey ?? null,
  };
}

describe("useTemplateIcon — template wins when iconKey is set", () => {
  it("returns the template's iconKey when present", () => {
    expect(useTemplateIcon(template("mdi-layers"), "DataObject")).toBe("mdi-layers");
  });

  it("returns the template's iconKey regardless of kindHint", () => {
    expect(useTemplateIcon(template("mdi-factory"), "Collection")).toBe("mdi-factory");
    expect(useTemplateIcon(template("mdi-radar"), undefined)).toBe("mdi-radar");
  });

  it("ignores empty-string iconKey (treats as missing)", () => {
    // Empty string in the wire response means "cleared" → per-kind default.
    expect(useTemplateIcon(template(""), "DataObject")).toBe("mdi-circle-medium");
  });
});

describe("useTemplateIcon — falls back to per-kind default when no iconKey", () => {
  it("uses the Collection default for Collection kind", () => {
    expect(useTemplateIcon(template(null), "Collection")).toBe("mdi-folder-multiple");
  });

  it("uses the Project default for Project kind", () => {
    expect(useTemplateIcon(template(null), "Project")).toBe("mdi-flag");
  });

  it("uses the DataObject default for DataObject kind", () => {
    expect(useTemplateIcon(template(null), "DataObject")).toBe("mdi-circle-medium");
  });

  it("uses reference-kind defaults", () => {
    expect(useTemplateIcon(null, "FileReference")).toBe("mdi-file-outline");
    expect(useTemplateIcon(null, "FileBundleReference")).toBe("mdi-folder-zip-outline");
    expect(useTemplateIcon(null, "TimeseriesReference")).toBe("mdi-chart-line");
    expect(useTemplateIcon(null, "SpatialDataReference")).toBe("mdi-cube-outline");
    expect(useTemplateIcon(null, "SceneGraph")).toBe("mdi-graph-outline");
    expect(useTemplateIcon(null, "LabJournalEntry")).toBe("mdi-notebook-outline");
  });

  it("handles null/undefined template the same as no iconKey", () => {
    expect(useTemplateIcon(null, "DataObject")).toBe("mdi-circle-medium");
    expect(useTemplateIcon(undefined, "DataObject")).toBe("mdi-circle-medium");
  });
});

describe("useTemplateIcon — falls back to generic when kindHint missing", () => {
  it("returns the generic default when both template and kindHint are missing", () => {
    expect(useTemplateIcon(null)).toBe("mdi-circle-medium");
    expect(useTemplateIcon(undefined)).toBe("mdi-circle-medium");
  });

  it("returns the generic default for an unknown kindHint", () => {
    expect(useTemplateIcon(null, "MysteryKind")).toBe("mdi-circle-medium");
  });
});

describe("defaultIconForKind — exported helper for stub render points", () => {
  it("returns the per-kind default with no template context", () => {
    expect(defaultIconForKind("Collection")).toBe("mdi-folder-multiple");
    expect(defaultIconForKind("DataObject")).toBe("mdi-circle-medium");
  });

  it("returns the generic fallback for null / undefined / unknown", () => {
    expect(defaultIconForKind(null)).toBe("mdi-circle-medium");
    expect(defaultIconForKind(undefined)).toBe("mdi-circle-medium");
    expect(defaultIconForKind("")).toBe("mdi-circle-medium");
    expect(defaultIconForKind("not-a-kind")).toBe("mdi-circle-medium");
  });
});
