<script setup lang="ts">
import type { Collection } from "@dlr-shepard/backend-client";
import {
  useFetchRecentCollections,
} from "~/composables/context/useFetchRecentCollections";
import { useFetchUserProfile } from "~/composables/context/useFetchUserProfile";
import { useWatchedCollections } from "~/composables/context/useWatchedCollections";
import { readCollectionAppId } from "~/utils/appId";
import { usePinnedChannels } from "~/composables/container/usePinnedChannels";

const router = useRouter();

// MISSING-V2-APPID-IN-REFLISTS slice 4: appId-first key/watch handle for
// Collections. Post-reset Collections carry a UUID v7 appId but no numeric
// id — using collection.id for v-for keys and isWatched() calls silently
// breaks for them (all keys collapse to null; isWatched(null) never matches
// an entry stored under the appId). Prefers the UUID appId; falls back to
// the numeric id for legacy entries.
function collectionHandle(collection: Collection): string | number {
  return readCollectionAppId(collection) ?? collection.id ?? 0;
}

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

// Display name of the current user — matches the `createdBy` field that the
// backend serialises via DisplayNameResolver.effectiveDisplayName().
// Used to split "my collections" from "shared with me" without an extra fetch.
const userDisplayName = computed<string | undefined>(() =>
  user.value?.effectiveDisplayName ?? user.value?.username ?? undefined,
);

// Split collections into "mine" (I created them) and "shared" (someone else
// did, but I can access them). Only split after both the user profile and the
// collection list have loaded — during the loading window, every collection
// would appear in the "shared" bucket since the display name is not yet known.
const myCollections = computed(() => {
  if (!userDisplayName.value || loading.value) return collections.value;
  return collections.value.filter(c => c.createdBy === userDisplayName.value);
});

const sharedCollections = computed(() => {
  if (!userDisplayName.value || loading.value) return [];
  return collections.value.filter(c => c.createdBy !== userDisplayName.value);
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
const {
  collections,
  allCollections,
  hasClosedCollections,
  showClosed,
  loading,
  error,
  refetch,
} = useFetchRecentCollections();

// Watched collections
const { watched, watchedLoading, isWatched, toggle: toggleWatched } =
  useWatchedCollections();

// UX-PIN1 — Pinned channel tiles (localStorage-backed singleton)
const { pinnedChannels, unpin } = usePinnedChannels();

const isEmpty = computed(() => !loading.value && allCollections.value.length === 0 && !error.value);

// Collection dialog
const showCreateDialog = ref(false);

function onCollectionCreated(appIdOrId: string) {
  showCreateDialog.value = false;
  router.push(collectionsPath + appIdOrId);
}

// Deterministic avatar color from username (one of 6 muted Vuetify colours) —
// used by the "Shared with me" list section.
const AVATAR_COLORS = ["blue-grey", "teal", "indigo", "deep-orange", "purple", "brown"];
function avatarColor(username: string): string {
  let h = 0;
  for (let i = 0; i < username.length; i++) h = (h * 31 + username.charCodeAt(i)) & 0xffff;
  return AVATAR_COLORS[h % AVATAR_COLORS.length]!;
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
  <!-- LAYOUT-4K-CENTERED-EMPTY-001 / L1: bumped from 1200px so the hub
       grid uses the 4K canvas without going full-bleed-unreadable. -->
  <v-container style="max-width: 2400px; margin: auto" fluid>
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
              alt=""
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
                {{ allCollections.length === 0
                  ? "No collections yet — create your first one below."
                  : `${collections.length} collection${collections.length === 1 ? "" : "s"}, sorted by last update.` }}
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
    <div v-if="!error && isEmpty" class="d-flex flex-column align-center py-16">
      <v-icon icon="mdi-folder-heart-outline" size="72" color="primary" class="mb-4" />
      <div class="text-h5 font-weight-medium mb-2">Welcome to Shepard</div>
      <div class="text-body-1 text-medium-emphasis mb-6 text-center" style="max-width: 420px">
        Create your first collection to get started — group related datasets,
        annotate them, and build a traceable research record.
      </div>
      <v-btn
        class="bg-primary text-canvas"
        variant="flat"
        size="large"
        prepend-icon="mdi-plus-circle"
        @click="showCreateDialog = true"
      >
        Create your first collection
      </v-btn>
    </div>

    <!-- UX-PIN1: Pinned channel tiles — shop-floor "just show me the values" entry point.
         Only rendered when at least one channel has been pinned from a TimeseriesContainer
         page.  The section header is suppressed while the list is empty so the home page
         stays uncluttered for users who have never pinned anything. -->
    <template v-if="pinnedChannels.length > 0">
      <div class="d-flex align-center mb-4 ga-2">
        <v-icon icon="mdi-pin" color="primary" size="20" />
        <div class="text-h6 font-weight-medium">Pinned channels</div>
        <v-chip size="x-small" variant="tonal" class="ms-1">
          {{ pinnedChannels.length }}
        </v-chip>
      </div>
      <v-row class="mb-6">
        <v-col
          v-for="ch in pinnedChannels"
          :key="ch.shepardId"
          cols="12"
          sm="6"
          md="4"
          lg="3"
        >
          <PinnedChannelTile
            :channel="ch"
            @unpin="unpin($event)"
          />
        </v-col>
      </v-row>
    </template>

    <!-- Watched collections section — always shown so the feature is discoverable -->
    <div class="d-flex align-center mb-4 ga-2">
      <v-icon icon="mdi-binoculars" color="primary" size="20" />
      <div class="text-h6 font-weight-medium">Watched</div>
    </div>
    <v-row class="mb-6">
      <template v-if="watchedLoading">
        <v-col v-for="n in 3" :key="n" cols="12" sm="6" md="4">
          <v-skeleton-loader type="card" />
        </v-col>
      </template>
      <template v-else-if="watched.length === 0">
        <v-col cols="12">
          <v-card
            variant="outlined"
            rounded="lg"
            class="pa-8 text-center"
            data-testid="watched-empty-state"
          >
            <v-icon icon="mdi-binoculars-outline" size="48" color="medium-emphasis" class="mb-3" />
            <div class="text-body-1 font-weight-medium mb-1">No watched collections yet</div>
            <div class="text-body-2 text-medium-emphasis">
              Open any collection and click the
              <v-icon icon="mdi-binoculars-outline" size="14" />
              binoculars icon to watch it.
            </div>
          </v-card>
        </v-col>
      </template>
      <template v-else>
        <v-col
          v-for="collection in watched"
          :key="collectionHandle(collection)"
          cols="12"
          sm="6"
          md="4"
        >
          <CollectionGalleryCard
            :collection="collection"
            :watchable="true"
            :watched="true"
            @toggle-watch="toggleWatched(collection)"
          />
        </v-col>
      </template>
    </v-row>

    <!-- Collection cards grid -->
    <template v-if="!isEmpty">
      <div class="d-flex align-center justify-space-between mb-4 flex-wrap ga-2">
        <div class="text-h6 font-weight-medium">Recent collections</div>
        <v-btn
          v-if="hasClosedCollections"
          :variant="showClosed ? 'tonal' : 'outlined'"
          size="small"
          :prepend-icon="showClosed ? 'mdi-eye' : 'mdi-eye-off-outline'"
          @click="showClosed = !showClosed"
        >
          {{ showClosed ? "Hide closed" : "Show closed" }}
        </v-btn>
      </div>
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

        <!-- Collection cards — uses the same CollectionGalleryCard as the
             /collections gallery so both pages share one consistent design. -->
        <template v-else>
          <v-col
            v-for="collection in myCollections"
            :key="collectionHandle(collection)"
            cols="12"
            sm="6"
            md="4"
          >
            <CollectionGalleryCard
              :collection="collection"
              :watchable="true"
              :watched="isWatched(collectionHandle(collection))"
              @toggle-watch="toggleWatched(collection)"
            />
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

    <!-- "Shared with me" section — collections created by other users that
         the current user can access. Only rendered once data is available
         and at least one shared collection exists. Visible in both basic
         and advanced mode (useful to everyone). -->
    <template v-if="!loading && sharedCollections.length > 0">
      <div
        class="d-flex align-center mb-4 mt-6 ga-2"
        data-testid="shared-collections-section"
      >
        <v-icon icon="mdi-account-multiple-outline" color="primary" size="20" />
        <div class="text-h6 font-weight-medium">Shared with me</div>
        <v-chip size="x-small" variant="tonal" class="ms-1">
          {{ sharedCollections.length }}
        </v-chip>
      </div>
      <v-list
        lines="two"
        variant="outlined"
        rounded="lg"
        class="mb-6 pa-0"
      >
        <v-list-item
          v-for="(collection, idx) in sharedCollections"
          :key="collectionHandle(collection)"
          :to="readCollectionAppId(collection) ? `/collections/${readCollectionAppId(collection)}` : undefined"
          :data-testid="`shared-collection-row-${idx}`"
          :divider="idx < sharedCollections.length - 1"
        >
          <template #prepend>
            <v-avatar
              :color="avatarColor(collection.createdBy)"
              size="32"
              :title="collection.createdBy"
            >
              <span style="font-size:12px; color:white; font-weight:500">
                {{ collection.createdBy.charAt(0).toUpperCase() }}
              </span>
            </v-avatar>
          </template>

          <v-list-item-title class="text-body-2 font-weight-medium">
            {{ collection.name }}
          </v-list-item-title>
          <v-list-item-subtitle class="text-caption text-medium-emphasis">
            {{ collection.createdBy }}
            <span v-if="collection.updatedAt ?? collection.createdAt" class="ms-1">
              · {{ relativeTime(collection.updatedAt ?? collection.createdAt) }}
            </span>
          </v-list-item-subtitle>

          <template #append>
            <v-chip
              size="x-small"
              variant="text"
              color="primary"
              append-icon="mdi-arrow-right"
            >
              View
            </v-chip>
          </template>
        </v-list-item>
      </v-list>
    </template>

    <!-- Collection creation dialog -->
    <CreateCollectionDialog
      v-if="showCreateDialog"
      v-model:show-dialog="showCreateDialog"
      @collection-created="onCollectionCreated"
    />
  </v-container>
</template>

