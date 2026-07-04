<script lang="ts" setup>
/**
 * II2 (ui-scrutinizer-2026-05-30) — entity-agnostic "Cite this" card.
 *
 * Factored out of `components/context/collection/CiteThisCard.vue`
 * (RDM-001, 2026-05-29) so the affordance is reusable on the DataObject
 * detail page (and any future citable entity — Snapshot, ImportPlan,
 * publication record). The wrapper holds the entity-specific lookups
 * (createdBy, license, URL, year); this component holds the format
 * tabbed view + the copy-to-clipboard glue.
 *
 * The Collection variant becomes a thin wrapper around this component
 * — no visual change.
 */
import { useClipboard } from "@vueuse/core";
import {
  formatCitation,
  CITATION_FORMATS_ORDER,
  CITATION_FORMAT_LABELS,
  type CitationFormat,
  type CitationInput,
} from "~/utils/citation";

const { input, label, testidPrefix } = withDefaults(
  defineProps<{
    /**
     * Fully-resolved citation shape. The wrapper handles the entity →
     * field mapping; this component just renders. Keeping the contract
     * to a pure `CitationInput` means the same component serves
     * Collection + DataObject + future entities without coupling to any
     * wire shape.
     */
    input: CitationInput;
    /**
     * Card title — e.g. "Cite this dataset" (Collection) or "Cite this
     * DataObject" (DataObject). Defaults to "Cite this dataset" so the
     * Collection wrapper stays a zero-prop call.
     */
    label?: string;
    /**
     * Optional prefix for test IDs to keep selectors stable per entity
     * surface. Defaults match the Collection variant for backward
     * compatibility with existing Playwright suites.
     */
    testidPrefix?: string;
  }>(),
  {
    label: "Cite this dataset",
    testidPrefix: "cite-this",
  },
);

const { copy } = useClipboard();

const activeFormat = ref<CitationFormat>("plain");

const cardLabel = computed(() => label);
const tp = computed(() => testidPrefix);

const citationText = computed(() =>
  formatCitation(input, activeFormat.value),
);

function copyCitation(): void {
  copy(citationText.value);
  emitSuccess(`Copied ${CITATION_FORMAT_LABELS[activeFormat.value]} citation`);
}
</script>

<template>
  <v-card
    class="cite-this-card"
    variant="outlined"
    :data-testid="`${tp}-card`"
  >
    <v-card-title class="d-flex align-center ga-2">
      <v-icon size="small" color="primary">mdi-format-quote-close</v-icon>
      <span>{{ cardLabel }}</span>
      <v-spacer />
      <v-btn
        prepend-icon="mdi-content-copy"
        variant="tonal"
        size="small"
        color="primary"
        :data-testid="`${tp}-copy`"
        @click="copyCitation"
      >
        Copy
      </v-btn>
    </v-card-title>
    <v-card-text>
      <v-tabs
        v-model="activeFormat"
        density="compact"
        color="primary"
        class="mb-3"
        :data-testid="`${tp}-tabs`"
      >
        <v-tab
          v-for="f in CITATION_FORMATS_ORDER"
          :key="f"
          :value="f"
          :data-testid="`${tp}-tab-${f}`"
        >
          {{ CITATION_FORMAT_LABELS[f] }}
        </v-tab>
      </v-tabs>
      <pre
        class="citation-body"
        :data-testid="`${tp}-body`"
      >{{ citationText }}</pre>
    </v-card-text>
  </v-card>
</template>

<style lang="scss" scoped>
.cite-this-card {
  margin-bottom: 24px;
}
.citation-body {
  font-family: ui-monospace, "SF Mono", Menlo, Consolas, monospace;
  font-size: 0.875rem;
  line-height: 1.45;
  white-space: pre-wrap;
  word-break: break-word;
  background: rgba(var(--v-theme-on-surface), 0.04);
  padding: 12px;
  border-radius: 4px;
  margin: 0;
  max-height: 400px;
  overflow: auto;
}
</style>
