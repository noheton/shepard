<script setup lang="ts">
/**
 * #36 — Gallery card tile for the Collections index grid view.
 *
 * Displays: name, 2-line description snippet, hero image (or gradient
 * placeholder), metadata-completeness chip, DataObject count, creator,
 * and a "Browse" button that navigates to the Collection detail page.
 *
 * The completeness chip reuses the same `computeMetadataCompleteness`
 * helper as `MetadataCompletenessCard.vue` — pure function, no I/O.
 * The card intentionally omits the async fetches (annotation count,
 * lab-journal count, creator ORCID) that the full card does, so every
 * tile renders immediately from the search-result payload. The score
 * will be conservatively low for those three checks; that is correct
 * and intentional — the card is a discovery surface, not an audit form.
 */
import type { Collection } from "@dlr-shepard/backend-client";
import { computeMetadataCompleteness } from "~/utils/metadataCompleteness";
import { descriptionPreview } from "~/utils/helpers";

const props = defineProps<{
  collection: Collection;
}>();

const router = useRouter();

// ── Description ──────────────────────────────────────────────────────
const DESCRIPTION_PREVIEW_CHARS = 100;

const preview = computed(() =>
  descriptionPreview(props.collection.description, DESCRIPTION_PREVIEW_CHARS),
);

// ── Hero image / gradient placeholder ────────────────────────────────
const heroUrl = computed<string | null>(
  () => props.collection.heroImageUrl ?? null,
);

/** First character of the collection name, upper-cased, for the placeholder. */
const initial = computed<string>(() =>
  (props.collection.name ?? "?").charAt(0).toUpperCase(),
);

/**
 * A deterministic pastel gradient derived from the collection id so
 * each placeholder looks different while remaining stable across renders.
 */
const placeholderGradient = computed<string>(() => {
  const id = props.collection.id ?? 0;
  const hue1 = (id * 83) % 360;
  const hue2 = (hue1 + 40) % 360;
  return `linear-gradient(135deg, hsl(${hue1},60%,60%), hsl(${hue2},55%,50%))`;
});

// ── Metadata completeness (fast, no-IO path) ─────────────────────────
const completeness = computed(() =>
  computeMetadataCompleteness({
    collection: props.collection,
    semanticAnnotationCount: null,
    labJournalCount: null,
    creatorOrcid: null,
  }),
);

const completenessColor = computed<"error" | "warning" | "success">(
  () => completeness.value.band,
);

// ── DataObject count ─────────────────────────────────────────────────
const doCount = computed<number>(
  () => (props.collection.dataObjectIds ?? []).length,
);

// ── Navigation ───────────────────────────────────────────────────────
function browse() {
  void router.push(collectionsPath + props.collection.id);
}
</script>

<template>
  <v-card
    class="collection-gallery-card d-flex flex-column"
    variant="outlined"
    :data-testid="`collection-gallery-card-${collection.id}`"
    @click="browse"
  >
    <!-- Hero image / placeholder banner -->
    <div class="gallery-card-hero">
      <img
        v-if="heroUrl"
        :src="heroUrl"
        :alt="collection.name"
        class="gallery-card-hero-img"
      />
      <div
        v-else
        class="gallery-card-hero-placeholder"
        :style="{ background: placeholderGradient }"
        aria-hidden="true"
      >
        <span class="gallery-card-initial text-white">{{ initial }}</span>
      </div>
    </div>

    <!-- Card body -->
    <v-card-title
      class="text-subtitle-1 font-weight-medium text-textbody1 pt-3 pb-0 px-4"
      data-testid="gallery-card-name"
    >
      {{ collection.name }}
    </v-card-title>

    <v-card-text class="py-1 px-4 flex-grow-1">
      <p
        v-if="preview"
        class="text-body-2 text-textbody2 gallery-card-description mb-2"
        :title="collection.description ?? undefined"
        data-testid="gallery-card-description"
      >
        {{ preview }}
      </p>
      <p
        v-else
        class="text-body-2 text-disabled mb-2"
        aria-hidden="true"
      >
        No description
      </p>

      <!-- Metadata + stats row -->
      <div class="d-flex align-center flex-wrap ga-2 mt-1">
        <v-chip
          :color="completenessColor"
          size="x-small"
          variant="tonal"
          :title="`Metadata completeness: ${completeness.score}/100`"
          data-testid="gallery-card-completeness-chip"
        >
          <v-icon start size="x-small">mdi-clipboard-check-outline</v-icon>
          {{ completeness.score }}/100
        </v-chip>

        <v-chip
          size="x-small"
          variant="tonal"
          color="secondary"
          :title="`${doCount} Data Object${doCount === 1 ? '' : 's'}`"
          data-testid="gallery-card-do-count"
        >
          <v-icon start size="x-small">mdi-cube-outline</v-icon>
          {{ doCount }} DOs
        </v-chip>

        <span
          v-if="collection.createdBy"
          class="text-caption text-textbody2 ml-auto"
          :title="`Created by ${collection.createdBy}`"
          data-testid="gallery-card-creator"
        >
          {{ collection.createdBy }}
        </span>
      </div>
    </v-card-text>

    <v-card-actions class="px-4 pb-3 pt-1">
      <v-btn
        variant="tonal"
        color="primary"
        size="small"
        density="comfortable"
        data-testid="gallery-card-browse-btn"
        @click.stop="browse"
      >
        Browse
        <v-icon end size="small">mdi-arrow-right</v-icon>
      </v-btn>
    </v-card-actions>
  </v-card>
</template>

<style lang="scss" scoped>
.collection-gallery-card {
  cursor: pointer;
  transition: box-shadow 0.15s ease, transform 0.12s ease;

  &:hover {
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.12);
    transform: translateY(-2px);
  }
}

.gallery-card-hero {
  height: 120px;
  overflow: hidden;
  border-radius: 4px 4px 0 0;
  flex-shrink: 0;
}

.gallery-card-hero-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.gallery-card-hero-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.gallery-card-initial {
  font-size: 2.5rem;
  font-weight: 700;
  opacity: 0.85;
  user-select: none;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.25);
}

.gallery-card-description {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
  min-height: 2.6em;
}
</style>
