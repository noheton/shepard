<script setup lang="ts">
// PlaceholderFragmentPane — pane shape used for fragment-routed admin /
// profile placeholders. Wraps the three reusable placeholder components
// behind a single :id="slug" anchor so MenuList's fragment navigation
// works unchanged.
//
// Sourced from the placeholderRegistry; pass the slug and the pane
// looks up its own metadata.

import {
  findPlaceholder,
  type PlaceholderEntry,
} from "./placeholderRegistry";
import PlaceholderPageHeader from "./PlaceholderPageHeader.vue";
import PlaceholderRestDump from "./PlaceholderRestDump.vue";
import PlaceholderImplStatus from "./PlaceholderImplStatus.vue";

const props = defineProps<{ slug: string }>();

const entry = computed<PlaceholderEntry | undefined>(() =>
  findPlaceholder(props.slug),
);

const designDocHref = computed(() => {
  const designDoc = entry.value?.designDoc;
  if (!designDoc) return undefined;
  return `https://github.com/noheton/shepard/blob/main/${designDoc}`;
});
</script>

<template>
  <div v-if="entry" :id="slug" class="d-flex flex-column ga-4">
    <PlaceholderPageHeader
      :title="entry.title"
      :subtitle="entry.subtitle"
      :design-doc-href="designDocHref"
    />
    <PlaceholderImplStatus
      :backend="entry.backend"
      :backlog-row="entry.backlogRow"
      :design-doc="entry.designDoc"
      :endpoint="entry.endpoint"
      notes="This is a placeholder route — the full UI is queued. Until then, advanced-mode users can see the raw admin REST data below."
    />
    <PlaceholderRestDump
      :endpoint="entry.endpoint"
      hint="Raw REST response is shown in advanced mode for power users until the full UI lands."
    />
  </div>
  <div v-else class="text-error">Unknown placeholder slug: {{ slug }}</div>
</template>
