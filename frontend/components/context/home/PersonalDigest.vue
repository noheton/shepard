<script setup lang="ts">
import { useFetchRecentCollections } from "~/composables/context/useFetchRecentCollections";
import { useFetchUserProfile } from "~/composables/context/useFetchUserProfile";

const router = useRouter();

// User info
const { user, isLoading: userLoading } = useFetchUserProfile();

const greeting = computed(() => {
  const hour = new Date().getHours();
  if (hour < 12) return "Good morning";
  if (hour < 18) return "Good afternoon";
  return "Good evening";
});

const displayName = computed(
  () => user.value?.effectiveDisplayName ?? user.value?.firstName ?? user.value?.username ?? "",
);

// Avatar
const avatarError = ref(false);
const { public: publicConfig } = useRuntimeConfig();

const userAppId = computed<string | undefined>(() => {
  const raw = (user.value as unknown as { appId?: string | null })?.appId;
  return raw ?? undefined;
});

function v2BaseUrl(): string {
  const explicit = publicConfig.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit;
  return (publicConfig.backendApiUrl as string).replace(/\/shepard\/api\/?$/, "");
}

const avatarUrl = computed(() =>
  userAppId.value ? `${v2BaseUrl()}/v2/users/${userAppId.value}/avatar` : "",
);

// Collections
const { collections, loading, error, refetch } = useFetchRecentCollections();

const isEmpty = computed(() => !loading.value && collections.value.length === 0 && !error.value);

// Collection dialog
const showCreateDialog = ref(false);

function onCollectionCreated(id: number) {
  showCreateDialog.value = false;
  router.push(collectionsPath + id);
}

// Relative timestamp helper — plain function, safe to call in templates.
function relativeTime(date: Date | null | undefined): string {
  if (!date) return "—";
  const now = Date.now();
  const diffMs = now - date.getTime();
  const diffSec = Math.floor(diffMs / 1000);
  if (diffSec < 60) return "just now";
  const diffMin = Math.floor(diffSec / 60);
  if (diffMin < 60) return `${diffMin}m ago`;
  const diffHr = Math.floor(diffMin / 60);
  if (diffHr < 24) return `${diffHr}h ago`;
  const diffDay = Math.floor(diffHr / 24);
  if (diffDay < 30) return `${diffDay}d ago`;
  const diffMo = Math.floor(diffDay / 30);
  if (diffMo < 12) return `${diffMo}mo ago`;
  return `${Math.floor(diffMo / 12)}y ago`;
}
</script>

<template>
  <v-container style="max-width: 1200px; margin: auto" fluid>
    <!-- Greeting card -->
    <v-card
      class="mb-6 pa-4"
      elevation="0"
      variant="outlined"
      rounded="lg"
    >
      <div class="d-flex align-center justify-space-between flex-wrap ga-4">
        <div class="d-flex align-center ga-4">
          <!-- User avatar -->
          <v-avatar size="56" color="primary">
            <v-img
              v-if="!avatarError && avatarUrl"
              :src="avatarUrl"
              cover
              @error="avatarError = true"
            />
            <span v-else class="text-h5 font-weight-medium">
              {{ displayName.charAt(0).toUpperCase() || "?" }}
            </span>
          </v-avatar>
          <div>
            <div class="text-h5 font-weight-medium">
              <span v-if="!userLoading">{{ greeting }}, {{ displayName }}.</span>
              <v-skeleton-loader v-else type="text" width="200" />
            </div>
            <div class="text-body-2 text-medium-emphasis mt-1">
              <span v-if="!loading">
                {{ collections.length === 0
                  ? "No collections yet — create your first one below."
                  : `Showing your ${collections.length} most recently updated collection${collections.length === 1 ? "" : "s"}.` }}
              </span>
              <v-skeleton-loader v-else type="text" width="280" />
            </div>
          </div>
        </div>

        <!-- Quick actions -->
        <div class="d-flex ga-2 flex-wrap">
          <v-btn
            variant="outlined"
            prepend-icon="mdi-folder-multiple-outline"
            to="/collections"
          >
            Browse all
          </v-btn>
          <v-btn
            class="bg-primary text-canvas"
            variant="flat"
            prepend-icon="mdi-plus-circle"
            @click="showCreateDialog = true"
          >
            New collection
          </v-btn>
        </div>
      </div>
    </v-card>

    <!-- Error state -->
    <v-alert
      v-if="error"
      type="error"
      variant="tonal"
      class="mb-6"
    >
      {{ error }}
      <template #append>
        <v-btn variant="text" size="small" @click="refetch">Retry</v-btn>
      </template>
    </v-alert>

    <!-- Empty state (authenticated but no collections) -->
    <div v-else-if="isEmpty" class="d-flex flex-column align-center py-16">
      <v-icon icon="mdi-folder-open-outline" size="72" color="textbody2" class="mb-4" />
      <div class="text-h5 font-weight-medium mb-2">No collections yet</div>
      <div class="text-body-1 text-medium-emphasis mb-6">
        Create your first collection to start organising your research data.
      </div>
      <v-btn
        class="bg-primary text-canvas"
        variant="flat"
        size="large"
        prepend-icon="mdi-plus-circle"
        @click="showCreateDialog = true"
      >
        Create collection
      </v-btn>
    </div>

    <!-- Collection cards grid -->
    <template v-else>
      <div class="text-h6 font-weight-medium mb-4">Recent collections</div>
      <v-row>
        <!-- Skeleton loaders while fetching -->
        <template v-if="loading">
          <v-col
            v-for="n in 6"
            :key="n"
            cols="12"
            sm="6"
            md="4"
          >
            <v-skeleton-loader type="card" />
          </v-col>
        </template>

        <!-- Collection cards -->
        <template v-else>
          <v-col
            v-for="collection in collections"
            :key="collection.id"
            cols="12"
            sm="6"
            md="4"
          >
            <v-card
              :to="`/collections/${collection.id}`"
              height="100%"
              elevation="0"
              variant="outlined"
              rounded="lg"
              class="collection-digest-card d-flex flex-column"
            >
              <v-card-title class="text-body-1 font-weight-medium pt-4 px-4 pb-0">
                <div class="collection-name-clamp">{{ collection.name }}</div>
              </v-card-title>

              <v-card-text class="flex-grow-1 px-4 pt-2 pb-2">
                <div
                  v-if="collection.description"
                  class="text-body-2 text-medium-emphasis description-clamp"
                >
                  {{ collection.description }}
                </div>
                <div v-else class="text-body-2 text-disabled font-italic">
                  No description
                </div>
              </v-card-text>

              <!-- Footer chips -->
              <v-card-actions class="px-4 pb-3 pt-0 d-flex flex-wrap ga-1">
                <!-- DataObject count chip -->
                <v-chip
                  size="x-small"
                  variant="tonal"
                  prepend-icon="mdi-cube-outline"
                >
                  {{ collection.dataObjectIds.length }}
                  {{ collection.dataObjectIds.length === 1 ? "object" : "objects" }}
                </v-chip>

                <!-- Status chip (if set) -->
                <v-chip
                  v-if="collection.status"
                  size="x-small"
                  variant="tonal"
                >
                  {{ collection.status }}
                </v-chip>

                <v-spacer />

                <!-- Last updated relative timestamp -->
                <v-chip
                  size="x-small"
                  variant="text"
                  class="text-medium-emphasis"
                  prepend-icon="mdi-clock-outline"
                >
                  {{ relativeTime(collection.updatedAt ?? collection.createdAt) }}
                </v-chip>
              </v-card-actions>
            </v-card>
          </v-col>
        </template>
      </v-row>

      <!-- "Browse all" footer link -->
      <div class="d-flex justify-center mt-6">
        <v-btn
          variant="text"
          color="primary"
          to="/collections"
          append-icon="mdi-arrow-right"
        >
          Browse all collections
        </v-btn>
      </div>
    </template>

    <!-- TODO: "Shared with me" section (v2) — split collections where
         collection.createdBy !== currentUser.appId into a separate
         flat v-list below the "My collections" grid. -->

    <!-- Collection creation dialog -->
    <CreateCollectionDialog
      v-if="showCreateDialog"
      v-model:show-dialog="showCreateDialog"
      @collection-created="onCollectionCreated"
    />
  </v-container>
</template>

<style scoped>
.collection-name-clamp {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.4;
  word-break: break-word;
}

.description-clamp {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.5;
}

.collection-digest-card {
  cursor: pointer;
  transition: box-shadow 0.15s ease, transform 0.15s ease;
}

.collection-digest-card:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1) !important;
  transform: translateY(-1px);
}
</style>
