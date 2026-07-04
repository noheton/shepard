/**
 * PLACEHOLDER-form-preview slice 1 — unit tests for groupDescriptorFields.
 */
import { describe, it, expect } from "vitest";

import { groupDescriptorFields } from "../../utils/formPreview";
import type { FormDescriptor } from "../../composables/useTemplateForm";

function makeDescriptor(
  groups: FormDescriptor["groups"],
  fields: FormDescriptor["fields"],
): FormDescriptor {
  return {
    templateAppId: "test-app-id",
    templateKind: "DATAOBJECT_RECIPE",
    title: "Test Form",
    groups,
    fields,
    submit: { method: "POST", href: "/v2/data-objects" },
  };
}

describe("groupDescriptorFields", () => {
  it("empty descriptor — no groups, no fields → single _ungrouped group with empty fields", () => {
    const result = groupDescriptorFields(makeDescriptor([], []));
    expect(result).toHaveLength(1);
    expect(result[0]?.id).toBe("_ungrouped");
    expect(result[0]?.label).toBe("Fields");
    expect(result[0]?.fields).toHaveLength(0);
  });

  it("fields with no group property fall into _ungrouped", () => {
    const result = groupDescriptorFields(
      makeDescriptor([], [
        { path: "ex:name", label: "Name", editor: "sh:TextFieldEditor" },
        { path: "ex:desc", label: "Description", editor: "sh:TextAreaEditor" },
      ]),
    );
    expect(result).toHaveLength(1);
    expect(result[0]?.id).toBe("_ungrouped");
    expect(result[0]?.fields).toHaveLength(2);
  });

  it("fields sorted by order within their group", () => {
    const result = groupDescriptorFields(
      makeDescriptor(
        [{ id: "g1", label: "Group 1", order: 0 }],
        [
          { path: "ex:b", label: "B", editor: "sh:TextFieldEditor", group: "g1", order: 2 },
          { path: "ex:a", label: "A", editor: "sh:TextFieldEditor", group: "g1", order: 1 },
          { path: "ex:c", label: "C", editor: "sh:TextFieldEditor", group: "g1", order: 3 },
        ],
      ),
    );
    expect(result[0]?.fields.map((f) => f.path)).toEqual(["ex:a", "ex:b", "ex:c"]);
  });

  it("groups sorted by order", () => {
    const result = groupDescriptorFields(
      makeDescriptor(
        [
          { id: "g2", label: "Second", order: 2 },
          { id: "g1", label: "First", order: 1 },
          { id: "g3", label: "Third", order: 3 },
        ],
        [],
      ),
    );
    expect(result.map((g) => g.id)).toEqual(["g1", "g2", "g3"]);
  });

  it("multiple groups each receive their own fields, others excluded", () => {
    const result = groupDescriptorFields(
      makeDescriptor(
        [
          { id: "alpha", label: "Alpha", order: 1 },
          { id: "beta", label: "Beta", order: 2 },
        ],
        [
          { path: "ex:a1", label: "A1", editor: "sh:TextFieldEditor", group: "alpha", order: 1 },
          { path: "ex:b1", label: "B1", editor: "sh:TextFieldEditor", group: "beta", order: 1 },
          { path: "ex:a2", label: "A2", editor: "sh:TextFieldEditor", group: "alpha", order: 2 },
        ],
      ),
    );
    expect(result).toHaveLength(2);
    expect(result[0]?.id).toBe("alpha");
    expect(result[0]?.fields.map((f) => f.path)).toEqual(["ex:a1", "ex:a2"]);
    expect(result[1]?.id).toBe("beta");
    expect(result[1]?.fields.map((f) => f.path)).toEqual(["ex:b1"]);
  });

  it("null group on field treated as _ungrouped when no explicit groups defined", () => {
    const result = groupDescriptorFields(
      makeDescriptor([], [
        { path: "ex:x", label: "X", editor: "sh:TextFieldEditor", group: null },
      ]),
    );
    expect(result[0]?.id).toBe("_ungrouped");
    expect(result[0]?.fields).toHaveLength(1);
  });
});
