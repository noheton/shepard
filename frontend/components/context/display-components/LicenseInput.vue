<script setup lang="ts">
// LIC1: v-autocomplete fed by the curated SPDX list. The field is free-text:
// users can type any custom SPDX expression and the bound value is whatever
// they typed. The dropdown items are suggestions, not a hard constraint.
//
// Two-way bound via defineModel<"license"> — matches the pattern of the
// sibling NameInput / DescriptionInput components in the create dialogs.
import { SPDX_LICENSES } from "~/utils/spdxLicenses";

const license = defineModel<string | null | undefined>("license");

// Map SPDX entries into v-autocomplete items: title shown in the dropdown,
// value is the SPDX id (the wire form).
const items = computed(() =>
  SPDX_LICENSES.map(l => ({
    title: l.id,
    subtitle: l.title,
    value: l.id,
  })),
);

// v-autocomplete returns the typed string when free-text input is allowed;
// keep the field bound as a string. Empty string -> normalise to null so the
// API receives null and the @JsonInclude(NON_NULL) drops the field.
const localValue = computed({
  get: () => license.value ?? null,
  set: (v: string | null) => {
    license.value = v && v.trim().length > 0 ? v.trim() : null;
  },
});
</script>

<template>
  <v-autocomplete
    v-model="localValue"
    :items="items"
    label="License"
    placeholder="e.g. CC-BY-4.0, MIT, PROPRIETARY"
    hint="SPDX identifier. Pick one from the list or type a custom expression. Leave blank if undeclared."
    persistent-hint
    clearable
    item-title="title"
    item-value="value"
    prepend-inner-icon="mdi-license"
    density="compact"
    auto-select-first="exact"
  >
    <template #item="{ props: itemProps, item }">
      <v-list-item v-bind="itemProps">
        <template #subtitle>
          {{ item.raw.subtitle }}
        </template>
      </v-list-item>
    </template>
  </v-autocomplete>
</template>
