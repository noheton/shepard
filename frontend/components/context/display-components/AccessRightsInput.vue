<script setup lang="ts">
// LIC1: v-select for the controlled accessRights vocabulary. Strict — users
// pick one of OPEN | RESTRICTED | CLOSED | EMBARGOED, or clear the field
// (null). The server is permissive, but the UI is the enforcer.
import { ACCESS_RIGHTS_OPTIONS } from "~/utils/spdxLicenses";

const accessRights = defineModel<string | null | undefined>("accessRights");

const items = computed(() =>
  ACCESS_RIGHTS_OPTIONS.map(o => ({
    title: o.label,
    subtitle: o.description,
    value: o.value,
    color: o.color,
  })),
);

const localValue = computed({
  get: () => accessRights.value ?? null,
  set: (v: string | null) => {
    accessRights.value = v && v.length > 0 ? v : null;
  },
});
</script>

<template>
  <v-select
    v-model="localValue"
    :items="items"
    label="Access rights"
    hint="Who can access this entity. Open = public; Restricted = approval needed; Closed = metadata only; Embargoed = open after a future date. Leave blank if undeclared."
    persistent-hint
    clearable
    item-title="title"
    item-value="value"
    prepend-inner-icon="mdi-shield-lock-outline"
    density="compact"
  >
    <template #selection="{ item }">
      <v-chip
        :color="item.raw.color"
        size="small"
        variant="tonal"
        label
      >
        {{ item.raw.title }}
      </v-chip>
    </template>
    <template #item="{ props: itemProps, item }">
      <v-list-item v-bind="itemProps">
        <template #prepend>
          <v-icon :color="item.raw.color">mdi-shield-lock-outline</v-icon>
        </template>
        <template #subtitle>
          {{ item.raw.subtitle }}
        </template>
      </v-list-item>
    </template>
  </v-select>
</template>
