<script setup lang="ts">
// PlaceholderPageHeader — title, subtitle, and (optional) design-doc link
// for the no-UI-gap placeholder routes shipped 2026-05-24. See
// aidocs/agent-findings/no-ui-gap-survey-2026-05-24.md.

defineProps<{
  title: string;
  subtitle: string;
  designDocHref?: string;
  designDocLabel?: string;
}>();
</script>

<template>
  <div class="d-flex flex-column ga-2 mb-4">
    <h4 class="text-h4">{{ title }}</h4>
    <p class="text-body-1 text-medium-emphasis">{{ subtitle }}</p>
    <div v-if="designDocHref">
      <a
        :href="designDocHref"
        target="_blank"
        rel="noopener"
        class="text-decoration-none"
      >
        <v-icon size="small" class="mr-1">mdi-book-open-page-variant-outline</v-icon>
        {{ designDocLabel ?? "Design document" }}
      </a>
    </div>
  </div>
</template>
