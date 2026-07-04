/**
 * PLACEHOLDER-form-preview slice 1 — grouping logic extracted for testability.
 *
 * groupDescriptorFields takes a compiled FormDescriptor and returns the groups
 * sorted by order, each carrying their sorted fields. Groups with no id match
 * fall into the synthetic `_ungrouped` bucket.
 */

import type { FormDescriptor, FormDescriptorField } from "~/composables/useTemplateForm";

export interface GroupWithFields {
  id: string;
  label?: string | null;
  order?: number | null;
  fields: FormDescriptorField[];
}

/**
 * Partition and sort descriptor fields into their groups.
 *
 * - If the descriptor carries no groups, a single synthetic `_ungrouped` group
 *   is created so callers always iterate a non-empty list.
 * - Fields whose `group` property is null/undefined also fall into `_ungrouped`.
 * - Both group list and per-group field list are sorted ascending by `order`
 *   (null/undefined treated as 0).
 */
export function groupDescriptorFields(descriptor: FormDescriptor): GroupWithFields[] {
  const groups = descriptor.groups.length
    ? descriptor.groups
    : [{ id: "_ungrouped", label: "Fields", order: 0 }];

  return groups
    .slice()
    .sort((a, b) => (a.order ?? 0) - (b.order ?? 0))
    .map((g) => ({
      ...g,
      fields: descriptor.fields
        .filter((f) => (f.group ?? "_ungrouped") === g.id)
        .sort((a, b) => (a.order ?? 0) - (b.order ?? 0)),
    }));
}
