<template>
  <v-app-bar class="border-b-sm bg-canvas">
    <v-app-bar-title>
      <v-btn to="/" style="border-bottom: unset">
        <v-img src="../../assets/shepard_logo.svg" height="29" width="153" />
      </v-btn>
      <v-btn class="nav-item" to="/collections">Collections</v-btn>
      <v-btn class="nav-item" to="/containers">Containers</v-btn>
      <v-btn
        class="nav-item"
        :to="{ path: '/configuration', hash: '#semanticrepositories' }"
      >
        Configuration
      </v-btn>
      <v-btn
        v-if="isInstanceAdmin"
        class="nav-item"
        :to="{ path: '/admin', hash: '#feature-toggles' }"
      >
        Admin
      </v-btn>
    </v-app-bar-title>

    <!-- Global type-ahead search bar (QW1) -->
    <v-autocomplete
      v-model="selectedItem"
      :items="searchItems"
      :loading="isLoading"
      :search="searchInput"
      :hide-no-data="hideNoData"
      no-filter
      density="compact"
      variant="outlined"
      hide-details
      placeholder="Search shepard…"
      prepend-inner-icon="mdi-magnify"
      no-data-text="No results"
      return-object
      clearable
      class="header-search"
      @update:search="onSearchUpdate"
      @update:model-value="onItemSelected"
    >
      <template #append-item>
        <v-divider class="mt-1 mb-1" />
        <v-list-item
          density="compact"
          :to="{ path: '/search' }"
          @click="clearSearch"
        >
          <template #prepend>
            <v-icon size="small" class="mr-1">mdi-magnify-plus-outline</v-icon>
          </template>
          <v-list-item-title class="text-caption">Advanced search →</v-list-item-title>
        </v-list-item>
      </template>
    </v-autocomplete>

    <template #append>
      <v-btn :to="{ path: '/about', hash: '#version' }" class="nav-item">
        About
      </v-btn>
      <v-btn
        :href="apiDocsUrl"
        target="_blank"
        class="nav-item"
      >
        API Docs
      </v-btn>
      <v-btn
        icon="mdi-account-outline"
        :to="{ path: '/user', hash: '#profile' }"
      />
      <v-btn icon="mdi-theme-light-dark" @click="toggleTheme" />
      <v-btn :prepend-icon="authIcon" @click="handleAuth()">
        {{ isSignedIn ? "Sign Out" : "Sign In" }}
      </v-btn>
    </template>
  </v-app-bar>
</template>

<script lang="ts" setup>
import { useTimeoutFn } from "@vueuse/core";
import { useTheme } from "vuetify";
import {
  useCollectionSearch,
  type MyCollectionSearchResult,
} from "~/composables/context/useCollectionSearch";

const { status, signOut, signIn, data } = useAuth();
const { public: publicConfig } = useRuntimeConfig();
const router = useRouter();

const apiDocsUrl = computed(() => {
  const base = (publicConfig.backendApiUrl as string) || "";
  return base ? `${base.replace(/\/$/, "")}/q/swagger-ui` : "/shepard/api/q/swagger-ui";
});
const theme = useTheme();

const isSignedIn = computed(
  () => status.value === "authenticated" && !data.value?.error,
).value;
const authIcon = isSignedIn ? "mdi-logout" : "mdi-account";

const isInstanceAdmin = computed(() =>
  hasInstanceAdminRole(data.value?.accessToken),
);

const handleAuth = () => {
  if (isSignedIn) {
    const signInCookie = useCookie(signInRedirectCookie);
    signInCookie.value = undefined;
    signOut({ callbackUrl: "/" });
    return;
  }
  signIn(undefined);
};
const toggleTheme = () => {
  theme.global.name.value = theme.global.current.value.dark ? "light" : "dark";
  localStorage.setItem("colorScheme", theme.global.name.value);
};

// ── Search bar state (QW1) ────────────────────────────────────────────────────

interface SearchItem {
  title: string;
  value: MyCollectionSearchResult;
}

const searchInput = ref<string>("");
const selectedItem = ref<SearchItem | null>(null);
const hideNoData = ref<boolean>(true);

const { collectionSearchResults, startSearch, isLoading, resetResultList } =
  useCollectionSearch(searchInput, () => {
    hideNoData.value = false;
  });

const searchItems = computed<SearchItem[]>(() =>
  collectionSearchResults.value.map(r => ({
    title: r.collectionName,
    value: r,
  })),
);

// Debounce: reuse the same useTimeoutFn pattern as CollectionAutocomplete.vue
const { isPending, start: scheduleSearch } = useTimeoutFn(() => {
  const trimmed = searchInput.value.trim();
  if (trimmed === "") {
    hideNoData.value = true;
    resetResultList();
    return;
  }
  // Reset before each new search so stale results don't accumulate
  resetResultList();
  startSearch();
}, 250);

function onSearchUpdate(val: string) {
  searchInput.value = val ?? "";
  if (!isPending.value) {
    scheduleSearch();
  }
}

function onItemSelected(item: SearchItem | null) {
  if (!item) return;
  const id = item.value.collectionId;
  clearSearch();
  router.push(`/collections/${id}`);
}

function clearSearch() {
  searchInput.value = "";
  selectedItem.value = null;
  hideNoData.value = true;
  resetResultList();
}
</script>

<style lang="scss" scoped>
:deep(.v-btn--icon) {
  aspect-ratio: 1/1;
  width: fit-content;
}

.v-btn {
  height: 64px; // toolbar height
  border-bottom: 3px solid transparent;
}

.v-btn--active {
  border-bottom: 3px solid rgb(var(--v-theme-primary));
  border-radius: 0px;

  :deep(.v-btn__overlay) {
    opacity: 0;
  }
}
.nav-item {
  font-size: 16px;
  font-weight: 400;
  font-style: normal;
  line-height: 26px;
  text-transform: none;
}

.header-search {
  max-width: 300px;
  min-width: 180px;
  align-self: center;
  margin: 0 8px;
}
</style>
