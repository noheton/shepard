<script setup lang="ts">
import { useSearchCollections } from "./useSearchCollections";
import { useCollectionListQueryParams } from "./useCollectionListQueryParams";
import { useAdvancedMode } from "~/composables/context/useAdvancedMode";

const router = useRouter();

const itemsPerPage = 20;
const { serverItems, pageCount, loading, searchResultHint } =
  useSearchCollections(itemsPerPage);
const { queryParams } = useCollectionListQueryParams();

const showCreateDialog = ref(false);

const isEmpty = computed(
  () => !loading.value && serverItems.value.length === 0 && !searchResultHint.value,
);
const isSearchEmpty = computed(
  () => !loading.value && serverItems.value.length === 0 && !!searchResultHint.value,
);

// ── #36 — View-mode toggle (list ↔ gallery) ───────────────────────────
// Persisted to localStorage so the user's preference survives page reloads.
const VIEW_MODE_KEY = "shepard.collections.viewMode";
type ViewMode = "list" | "gallery";

function readStoredViewMode(): ViewMode {
  if (typeof localStorage === "undefined") return "list";
  const stored = localStorage.getItem(VIEW_MODE_KEY);
  return stored === "gallery" ? "gallery" : "list";
}

const viewMode = ref<ViewMode>(readStoredViewMode());

function setViewMode(mode: ViewMode) {
  viewMode.value = mode;
  if (typeof localStorage !== "undefined") {
    localStorage.setItem(VIEW_MODE_KEY, mode);
  }
}

// UI-011a: per-page advanced-mode toggle so users can flip the mode
// directly from the collections header without opening their profile.
const { advancedMode, isSaving, setAdvancedMode } = useAdvancedMode();

// ── UX-WALK-2026-05-29-05 — Density toggle (compact ↔ default) ───────────
// Persisted to localStorage so the preference survives page reloads.
const DENSE_MODE_KEY = "shepard.collections.denseMode";

function readStoredDenseMode(): boolean {
  if (typeof localStorage === "undefined") return false;
  return localStorage.getItem(DENSE_MODE_KEY) === "true";
}

const dense = ref<boolean>(readStoredDenseMode());

function toggleDense() {
  dense.value = !dense.value;
  if (typeof localStorage !== "undefined") {
    localStorage.setItem(DENSE_MODE_KEY, String(dense.value));
  }
}

// Show a "Create another collection" CTA when the list is short (< 5 items)
// and the user is not actively searching — fills 4K whitespace with intent.
const showFewCollectionsCta = computed(
  () =>
    !loading.value &&
    !searchResultHint.value &&
    serverItems.value.length > 0 &&
    serverItems.value.length < 5 &&
    pageCount.value <= 1,
);
</script>

<template>
  <PageShell>
    <v-container width="100%" fluid>
      <v-row>
        <v-col cols="12" class="py-14">
          <div class="d-flex align-baseline">
            <h1 class="text-h1 text-textbody1 pr-4">Collections</h1>
          </div>
        </v-col>

        <template v-if="isEmpty">
          <v-col cols="12" class="d-flex flex-column align-center py-16">
            <v-icon icon="mdi-folder-open-outline" size="72" color="textbody2" class="mb-4" />
            <div class="text-h4 text-semibold mb-2">No collections yet</div>
            <div class="text-body-1 text-textbody2 mb-6">
              Create your first collection to start organising your research data.
            </div>
            <v-btn
              class="bg-primary text-canvas"
              variant="flat"
              size="large"
              @click="showCreateDialog = true"
            >
              <template #prepend>
                <v-icon icon="mdi-plus-circle" color="canvas" />
              </template>
              Create collection
            </v-btn>
          </v-col>
        </template>

        <template v-else-if="isSearchEmpty">
          <v-col cols="12" sm="auto" class="pb-4">
            <CollectionSearchField :search-result-hint="searchResultHint" />
          </v-col>
          <v-spacer />
          <v-col cols="12" sm="auto" class="pb-4">
            <v-btn
              class="bg-primary text-canvas"
              variant="flat"
              :style="{ marginTop: '3px' }"
              @click="showCreateDialog = true"
            >
              <template #prepend>
                <v-icon icon="mdi-plus-circle" color="canvas" />
              </template>
              Create new collection
            </v-btn>
          </v-col>
          <v-col cols="12" class="d-flex flex-column align-center py-12">
            <v-icon icon="mdi-magnify" size="64" color="textbody2" class="mb-4" />
            <div class="text-h5 text-textbody1 mb-2">No collections found</div>
            <div class="text-body-1 text-textbody2">{{ searchResultHint }}</div>
          </v-col>
        </template>

        <template v-else>
          <v-col cols="12" sm="auto" class="pb-4">
            <CollectionSearchField :search-result-hint="searchResultHint" />
          </v-col>
          <v-spacer />
          <!-- UI-011a: per-page advanced-mode toggle chip -->
          <v-col cols="auto" class="pb-4 d-flex align-center">
            <v-chip
              :prepend-icon="advancedMode ? 'mdi-tune' : 'mdi-tune-variant'"
              :color="advancedMode ? 'primary' : undefined"
              :disabled="isSaving"
              :loading="isSaving"
              variant="tonal"
              size="small"
              class="mr-2"
              data-testid="collection-advanced-mode-chip"
              :title="advancedMode ? 'Advanced mode on — click to disable' : 'Advanced mode off — click to enable'"
              @click="setAdvancedMode(!advancedMode)"
            >
              {{ advancedMode ? "Advanced" : "Basic" }}
            </v-chip>
          </v-col>
          <!-- UX-WALK-2026-05-29-05: density toggle — compact ↔ default (list view only) -->
          <v-col v-if="viewMode === 'list'" cols="auto" class="pb-4 d-flex align-center">
            <v-btn
              icon
              variant="text"
              density="compact"
              size="small"
              class="mr-1"
              :color="dense ? 'primary' : undefined"
              :title="dense ? 'Compact rows — click for spacious' : 'Spacious rows — click for compact'"
              data-testid="collection-dense-toggle"
              @click="toggleDense"
            >
              <v-icon>{{ dense ? 'mdi-table-row' : 'mdi-table-large' }}</v-icon>
            </v-btn>
          </v-col>
          <!-- #36: view-mode toggle — list ↔ gallery -->
          <v-col cols="auto" class="pb-4 d-flex align-center">
            <v-btn-toggle
              :model-value="viewMode"
              mandatory
              density="compact"
              variant="outlined"
              divided
              data-testid="collection-view-mode-toggle"
              @update:model-value="setViewMode"
            >
              <v-btn
                value="list"
                icon
                size="small"
                :title="'List view'"
                data-testid="view-mode-list-btn"
              >
                <v-icon>mdi-view-list</v-icon>
              </v-btn>
              <v-btn
                value="gallery"
                icon
                size="small"
                :title="'Card/gallery view'"
                data-testid="view-mode-gallery-btn"
              >
                <v-icon>mdi-view-grid</v-icon>
              </v-btn>
            </v-btn-toggle>
          </v-col>
          <v-col cols="12" sm="auto" class="pb-4">
            <v-btn
              class="bg-primary text-canvas"
              variant="flat"
              :style="{ marginTop: '3px' }"
              @click="showCreateDialog = true"
            >
              <template #prepend>
                <v-icon icon="mdi-plus-circle" color="canvas" />
              </template>
              Create new collection
            </v-btn>
          </v-col>
          <v-col cols="12" class="pt-4">
            <!-- List view -->
            <CollectionList
              v-if="viewMode === 'list'"
              :items-per-page="itemsPerPage"
              :server-items="serverItems"
              :loading="loading"
              :page-count="pageCount"
              :dense="dense"
            />
            <!-- Gallery / card view (#36) -->
            <template v-else>
              <v-row>
                <v-col
                  v-for="collection in serverItems"
                  :key="collection.id"
                  cols="12"
                  sm="6"
                  md="4"
                >
                  <CollectionGalleryCard :collection="collection" />
                </v-col>
              </v-row>
              <v-pagination
                v-if="pageCount > 1"
                :model-value="queryParams.page ?? 1"
                :length="pageCount"
                :total-visible="6"
                class="mt-4"
                @update:model-value="
                  (page) =>
                    router.push({
                      path: collectionsPath,
                      query: { ...router.currentRoute.value.query, page },
                    })
                "
              />
            </template>

            <!-- UX-WALK-2026-05-29-05: "Create another collection" CTA fills
                 4K whitespace when there are only a few rows in the table. -->
            <v-card
              v-if="showFewCollectionsCta"
              variant="tonal"
              class="mt-8 mx-auto"
              max-width="480"
              data-testid="few-collections-cta"
            >
              <v-card-text class="text-center pa-8">
                <v-icon size="48" class="mb-4" color="primary">mdi-folder-plus-outline</v-icon>
                <div class="text-h6 mb-2">Ready for more?</div>
                <div class="text-body-2 text-medium-emphasis mb-4">
                  Group related data objects into a new collection.
                </div>
                <v-btn
                  color="primary"
                  prepend-icon="mdi-plus"
                  data-testid="few-collections-cta-btn"
                  @click="showCreateDialog = true"
                >
                  Create collection
                </v-btn>
              </v-card-text>
            </v-card>
          </v-col>
        </template>
      </v-row>
    </v-container>
    <CreateCollectionDialog
      v-if="showCreateDialog"
      v-model:show-dialog="showCreateDialog"
      @collection-created="(appIdOrId: string) => router.push(collectionsPath + appIdOrId)"
    />
  </PageShell>
</template>
