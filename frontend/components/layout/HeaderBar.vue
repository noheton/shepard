<template>
  <v-app-bar class="border-b-sm bg-canvas">
    <!-- Mobile: hamburger menu icon -->
    <v-app-bar-nav-icon
      class="d-md-none"
      @click="mobileDrawerOpen = !mobileDrawerOpen"
    />

    <v-app-bar-title>
      <v-btn to="/" style="border-bottom: unset">
        <v-img src="../../assets/shepard_logo.svg" height="29" width="153" />
      </v-btn>
      <!-- Instance organisation name from ROR config (INST1 / task #23).
           Shown only when the instance-admin has set a rorId + orgName.
           Falls back gracefully when unconfigured. -->
      <v-chip
        v-if="instanceIdentity?.organizationName"
        size="x-small"
        variant="tonal"
        color="primary"
        class="d-none d-md-inline-flex ml-1"
        :href="instanceIdentity.rorUrl ?? undefined"
        :target="instanceIdentity.rorUrl ? '_blank' : undefined"
      >{{ instanceIdentity.organizationName }}</v-chip>
      <!-- Desktop: inline nav links -->
      <v-btn class="nav-item d-none d-md-inline-flex" to="/collections">Collections</v-btn>
      <v-btn class="nav-item d-none d-md-inline-flex" to="/containers">Containers</v-btn>
      <v-btn
        v-if="isInstanceAdmin"
        class="nav-item d-none d-md-inline-flex"
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
      <!-- Desktop: secondary nav behind overflow menu -->
      <v-menu location="bottom end">
        <template #activator="{ props: menuProps }">
          <v-btn icon="mdi-dots-vertical" class="d-none d-md-inline-flex" v-bind="menuProps" />
        </template>
        <v-list density="compact" nav>
          <v-list-item to="/help" title="Help" prepend-icon="mdi-help-circle-outline" />
          <v-list-item :to="{ path: '/about', hash: '#version' }" title="About" prepend-icon="mdi-information-outline" />
          <v-list-item :href="apiDocsUrl" target="_blank" title="API Docs" prepend-icon="mdi-api" />
        </v-list>
      </v-menu>
      <v-btn
        icon="mdi-account-outline"
        :to="{ path: '/me', hash: '#profile' }"
      />
      <v-btn class="d-none d-md-inline-flex" icon="mdi-theme-light-dark" @click="toggleTheme" />
      <v-btn :prepend-icon="authIcon" class="d-none d-md-inline-flex" @click="handleAuth()">
        {{ isSignedIn ? "Sign Out" : "Sign In" }}
      </v-btn>
      <!-- Mobile: sign out/in icon only -->
      <v-btn class="d-md-none" :icon="authIcon" @click="handleAuth()" />
      <!-- DLR institutional mark, subordinate to the shepard wordmark.
           Matches the docs-site utility-bar treatment. -->
      <a
        href="https://www.dlr.de"
        target="_blank"
        rel="external noopener"
        title="shepard is developed by DLR"
        class="dlr-mark d-none d-md-inline-flex"
      >
        <v-img
          :src="dlrLogoSrc"
          height="22"
          width="74"
          alt="Deutsches Zentrum für Luft- und Raumfahrt"
        />
      </a>
    </template>
  </v-app-bar>

  <!-- Mobile navigation drawer -->
  <v-navigation-drawer
    v-model="mobileDrawerOpen"
    class="d-md-none"
    location="left"
    temporary
  >
    <v-list nav>
      <v-list-item title="Collections" to="/collections" prepend-icon="mdi-folder-multiple-outline" @click="mobileDrawerOpen = false" />
      <v-list-item title="Containers" to="/containers" prepend-icon="mdi-database-outline" @click="mobileDrawerOpen = false" />
      <v-list-item
        v-if="isInstanceAdmin"
        title="Admin"
        :to="{ path: '/admin', hash: '#feature-toggles' }"
        prepend-icon="mdi-cog-outline"
        @click="mobileDrawerOpen = false"
      />
      <v-divider class="my-2" />
      <v-list-item
        to="/help"
        title="Help"
        prepend-icon="mdi-help-circle-outline"
        @click="mobileDrawerOpen = false"
      />
      <v-list-item
        :to="{ path: '/about', hash: '#version' }"
        title="About"
        prepend-icon="mdi-information-outline"
        @click="mobileDrawerOpen = false"
      />
      <v-list-item
        :href="apiDocsUrl"
        target="_blank"
        title="API Docs"
        prepend-icon="mdi-api"
      />
      <v-divider class="my-2" />
      <v-list-item
        :prepend-icon="'mdi-theme-light-dark'"
        title="Toggle theme"
        @click="toggleTheme"
      />
    </v-list>
  </v-navigation-drawer>
</template>

<script lang="ts" setup>
import { useTimeoutFn } from "@vueuse/core";
import { useTheme } from "vuetify";
import {
  useCollectionSearch,
  type MyCollectionSearchResult,
} from "~/composables/context/useCollectionSearch";
import { useInstanceIdentity } from "~/composables/context/useInstanceIdentity";

const { status, signOut, signIn, data } = useAuth();

const { identity: instanceIdentity, fetch: fetchIdentity } = useInstanceIdentity();
watch(
  () => data.value?.accessToken,
  (token) => { if (token) void fetchIdentity(token); },
  { immediate: true },
);
const { public: publicConfig } = useRuntimeConfig();
const router = useRouter();

// Mobile navigation drawer state
const mobileDrawerOpen = ref(false);

const apiDocsUrl = computed(() => {
  // Quarkus serves the Swagger UI under quarkus.http.non-application-root-path
  // (configured to /shepard/doc — see application.properties). The trailing
  // slash is required: /shepard/doc/swagger-ui without it 302-redirects, which
  // some browsers won't follow when opening a new tab.
  const base = (publicConfig.backendApiUrl as string) || "";
  if (!base) return "/shepard/doc/swagger-ui/";
  // backendApiUrl typically ends in /shepard/api — swap the suffix.
  return base.replace(/\/shepard\/api\/?$/, "") + "/shepard/doc/swagger-ui/";
});

// DLR institutional mark — pick the dark-mode variant on dark themes.
const themeForLogo = useTheme();
const dlrLogoSrc = computed(() =>
  themeForLogo.global.current.value.dark
    ? new URL("../../assets/dlr_logo_white.svg", import.meta.url).href
    : new URL("../../assets/dlr_logo.svg", import.meta.url).href,
);
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
}

.header-search {
  max-width: 300px;
  min-width: 120px;
  align-self: center;
  margin: 0 8px;
}

@media (max-width: 599px) {
  .header-search {
    min-width: 80px;
    max-width: 160px;
  }
}

// DLR institutional mark — subordinate to the shepard wordmark, with a
// hairline separator on its left so it reads as institutional attribution.
.dlr-mark {
  display: inline-flex;
  align-items: center;
  padding-left: 16px;
  margin-left: 8px;
  border-left: 1px solid rgba(var(--v-border-color), var(--v-border-opacity));
  text-decoration: none;
  opacity: 0.85;
  transition: opacity 0.15s ease;

  &:hover {
    opacity: 1;
  }
}
</style>
