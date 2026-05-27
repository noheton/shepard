<script setup lang="ts">
/**
 * PROMPT-h2 — per-Collection PromptLog storage mode selector.
 *
 * A v-select with three options corresponding to PromptLogMode enum values.
 * Bound via defineModel<"promptLogMode"> for two-way v-model usage.
 *
 * Null value means "not yet set; effective default is HASH_ONLY".
 * The selector shows "Hash only (default)" as the placeholder in that case.
 *
 * See aidocs/semantics/99-promptlog-design.md §10-11.
 */

const promptLogMode = defineModel<string | null | undefined>("promptLogMode");

const items = [
  {
    title: "Hash only (default)",
    subtitle: "Store only a SHA-256 hash of AI conversation bodies. Safest for all environments.",
    value: "HASH_ONLY",
  },
  {
    title: "Redacted body",
    subtitle: "Store the body with PII/sensitive content redacted at ingest. Suitable for analytics.",
    value: "BODY_REDACTED",
  },
  {
    title: "Raw body (air-gapped)",
    subtitle: "Store the body verbatim. Only for air-gapped or EU AI Act Art. 53 GPAI documentation deployments.",
    value: "BODY_RAW",
  },
];

const localValue = computed({
  get: () => promptLogMode.value ?? null,
  set: (v: string | null) => {
    promptLogMode.value = v;
  },
});
</script>

<template>
  <v-select
    v-model="localValue"
    :items="items"
    label="AI prompt log mode"
    hint="Controls how AI conversation bodies are stored in the PromptLog substrate for this Collection."
    persistent-hint
    clearable
    item-title="title"
    item-value="value"
    prepend-inner-icon="mdi-brain"
    density="compact"
  >
    <template #item="{ props: itemProps, item }">
      <v-list-item v-bind="itemProps">
        <template #subtitle>
          {{ item.raw.subtitle }}
        </template>
      </v-list-item>
    </template>
  </v-select>
</template>
