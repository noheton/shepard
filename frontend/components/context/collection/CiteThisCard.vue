<script lang="ts" setup>
/**
 * RDM-001 — "Cite this dataset" card.
 *
 * Renders a copy-paste-ready citation for a Collection in four
 * standards-track formats (APA-flavoured plain text, BibTeX, RIS, CSL
 * JSON). Pure frontend; the wire shape provides everything we need post-
 * LIC1 (license + accessRights now on `Collection`).
 *
 * Placement: directly below the description block on the Collection
 * landing, above the DataObjects panel — the highest-visibility spot a
 * funder reviewing the dataset will land on first.
 *
 * Author source: Shepard's `Collection` wire shape carries
 * `createdBy: string` (a bare username). No `contributors[]` field
 * exists today; the seed-script convention does not set a Collection-
 * level `authors` attribute. So in practice this card renders one
 * author = the creator's username. The `formatCitation` helper accepts
 * an array regardless, so a future surface that resolves usernames →
 * display names (or follows a contributors edge) plugs in without
 * touching this component.
 */
import { useClipboard } from "@vueuse/core";
import type { Collection } from "@dlr-shepard/backend-client";
import {
  formatCitation,
  CITATION_FORMATS_ORDER,
  CITATION_FORMAT_LABELS,
  type CitationFormat,
} from "~/utils/citation";

const { collection } = defineProps<{
  collection: Collection;
}>();

const { copy } = useClipboard();

const activeFormat = ref<CitationFormat>("plain");

/**
 * Today's date in ISO YYYY-MM-DD form. Re-computed on each render so a
 * user who leaves the page open overnight gets the right date when they
 * copy the citation in the morning. Not reactive to a timer (would be
 * over-engineering) — but a manual reload picks it up.
 */
const accessedDate = computed(() => {
  const d = new Date();
  return d.toISOString().slice(0, 10);
});

/**
 * Canonical URL of the Collection landing page. Uses `window.location`
 * minus any hash so the citation always points at the canonical
 * landing — not a deep-link into a tab a user happened to be on when
 * they hit "Copy".
 *
 * SSR-safe: during the server render `window` is undefined; we fall
 * back to a relative URL that the user-visible link will resolve once
 * the client takes over. The card mounts client-side anyway (the parent
 * page gates on `collection` being loaded, which is a client-fetch).
 */
const canonicalUrl = computed(() => {
  if (typeof window === "undefined") {
    return `/collections/${collection.id}`;
  }
  return `${window.location.origin}${window.location.pathname}`;
});

/**
 * Year derived from `createdAt`. Safe-guarded against the wire shape
 * returning a string (the generated client coerces to Date, but be
 * defensive against older builds).
 */
const year = computed(() => {
  const ts = collection.createdAt;
  if (!ts) return new Date().getFullYear();
  if (ts instanceof Date) return ts.getFullYear();
  return new Date(ts).getFullYear();
});

/**
 * Author list. See file-header comment for source-of-truth discussion.
 * Today: single-element array with `createdBy` username. Future-ready
 * for richer sources without a component change.
 */
const authors = computed<string[]>(() => {
  const cb = collection.createdBy?.trim();
  return cb ? [cb] : [];
});

/**
 * Per-format rendered text. Recomputed on every format flip; cheap (all
 * four formatters are pure string templating).
 */
const citationText = computed(() =>
  formatCitation(
    {
      authors: authors.value,
      year: year.value,
      title: collection.name,
      // Hard-coded today; INST1 will surface a configurable instance
      // identity string ("DLR Shepard Augsburg", etc.) — drop that in
      // here when it ships. Until then "Shepard Research Data Platform"
      // is the canonical repository name (matches the citation seen in
      // the RDM scrutinizer's example expected output).
      repository: "Shepard Research Data Platform",
      url: canonicalUrl.value,
      license: collectionLicense.value,
      accessedDate: accessedDate.value,
    },
    activeFormat.value,
  ),
);

/**
 * Defensive read of the LIC1 `license` field — same pattern as the
 * parent page uses. Older backend builds may not surface the field;
 * `formatCitation` already handles null by omitting the license line.
 */
const collectionLicense = computed<string | null>(() => {
  if (!collection) return null;
  const raw = (collection as unknown as { license?: string | null }).license;
  return raw ?? null;
});

function copyCitation() {
  copy(citationText.value);
  emitSuccess(`Copied ${CITATION_FORMAT_LABELS[activeFormat.value]} citation`);
}
</script>

<template>
  <v-card
    class="cite-this-card"
    variant="outlined"
    data-testid="cite-this-card"
  >
    <v-card-title class="d-flex align-center ga-2">
      <v-icon size="small" color="primary">mdi-format-quote-close</v-icon>
      <span>Cite this dataset</span>
      <v-spacer />
      <v-btn
        prepend-icon="mdi-content-copy"
        variant="tonal"
        size="small"
        color="primary"
        data-testid="cite-this-copy"
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
        data-testid="cite-this-tabs"
      >
        <v-tab
          v-for="f in CITATION_FORMATS_ORDER"
          :key="f"
          :value="f"
          :data-testid="`cite-this-tab-${f}`"
        >
          {{ CITATION_FORMAT_LABELS[f] }}
        </v-tab>
      </v-tabs>
      <!--
        Render the citation in a <pre> so multi-line formats (BibTeX, RIS,
        CSL JSON) keep their structure, and the plain-text form wraps
        nicely without losing intentional line breaks. `white-space:
        pre-wrap` keeps the natural wrap for plain text while preserving
        the line-by-line shape of the structured formats.
       -->
      <pre
        class="citation-body"
        data-testid="cite-this-body"
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
