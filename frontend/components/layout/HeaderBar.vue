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
      <v-btn class="nav-item d-none d-md-inline-flex" to="/" exact>Home</v-btn>
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

    <!-- Global header search (QW1 / UI-002).
         Custom v-menu + v-list (rather than v-autocomplete) because the
         dropdown carries three sections (collections / dataobjects /
         containers) plus explicit loading / empty / error states and a
         footer link to the Advanced-Search page. The autocomplete's
         `hide-no-data` semantics fight all of that. -->
    <div class="header-search position-relative">
      <v-text-field
        ref="searchFieldRef"
        v-model="search.query.value"
        density="compact"
        variant="outlined"
        hide-details
        placeholder="Search shepard…"
        prepend-inner-icon="mdi-magnify"
        clearable
        autocomplete="off"
        :loading="search.isLoading.value"
        data-testid="header-search-input"
        @focus="onSearchFocus"
        @click:clear="onSearchClear"
        @keydown.down.prevent="focusFirstResult"
        @keydown.esc="closeDropdown"
        @keydown.enter="onEnterPressed"
      />
      <v-menu
        v-model="dropdownOpen"
        :close-on-content-click="false"
        :open-on-click="false"
        :activator="searchFieldRef?.$el"
        location="bottom"
        :offset="4"
        :min-width="320"
        :max-height="500"
        transition="fade-transition"
        data-testid="header-search-dropdown"
      >
        <v-list
          ref="resultListRef"
          density="compact"
          class="header-search-results"
          min-width="320"
        >
          <!-- Error state — takes priority over everything else. -->
          <template v-if="search.error.value">
            <v-list-item
              data-testid="header-search-error"
              prepend-icon="mdi-alert-circle-outline"
            >
              <v-list-item-title class="text-caption text-error">
                {{ search.error.value }}
              </v-list-item-title>
              <v-list-item-subtitle class="text-caption">
                Check the browser console for details.
              </v-list-item-subtitle>
            </v-list-item>
          </template>

          <template v-else>
            <!-- Collections section -->
            <template v-if="search.collections.value.length > 0">
              <v-list-subheader class="text-overline">
                <v-icon size="small" class="mr-1">mdi-folder-multiple-outline</v-icon>
                Collections
              </v-list-subheader>
              <v-list-item
                v-for="c in search.collections.value"
                :key="`c-${c.collectionId}`"
                density="compact"
                :title="c.collectionName"
                data-testid="header-search-result-collection"
                @click="onPick('collection', c.collectionId)"
              >
                <template #append>
                  <v-chip size="x-small" variant="tonal" color="primary">collection</v-chip>
                </template>
              </v-list-item>
            </template>

            <!-- DataObjects section -->
            <template v-if="search.dataObjects.value.length > 0">
              <v-divider v-if="search.collections.value.length > 0" class="my-1" />
              <v-list-subheader class="text-overline">
                <v-icon size="small" class="mr-1">mdi-file-document-outline</v-icon>
                Data objects
              </v-list-subheader>
              <v-list-item
                v-for="d in search.dataObjects.value"
                :key="`d-${d.dataObjectId}`"
                density="compact"
                :title="d.dataObjectName"
                :disabled="d.collectionId === undefined"
                data-testid="header-search-result-dataobject"
                @click="onPickDataObject(d)"
              >
                <template #append>
                  <v-chip size="x-small" variant="tonal" color="secondary">dataobject</v-chip>
                </template>
              </v-list-item>
            </template>

            <!-- Containers section -->
            <template v-if="search.containers.value.length > 0">
              <v-divider
                v-if="search.collections.value.length > 0 || search.dataObjects.value.length > 0"
                class="my-1"
              />
              <v-list-subheader class="text-overline">
                <v-icon size="small" class="mr-1">mdi-database-outline</v-icon>
                Containers
              </v-list-subheader>
              <v-list-item
                v-for="ct in search.containers.value"
                :key="`ct-${ct.containerId}`"
                density="compact"
                :title="ct.containerName"
                data-testid="header-search-result-container"
                @click="onPickContainer(ct)"
              >
                <template #append>
                  <v-chip size="x-small" variant="tonal" color="info">
                    {{ containerChipLabel(ct.containerType) }}
                  </v-chip>
                </template>
              </v-list-item>
            </template>

            <!-- Empty state. Only after at least one search completed. -->
            <v-list-item
              v-if="search.isEmpty.value"
              data-testid="header-search-empty"
              prepend-icon="mdi-magnify-close"
            >
              <v-list-item-title class="text-caption">
                No matches for "{{ search.query.value }}"
              </v-list-item-title>
            </v-list-item>

            <!-- Loading hint while waiting for the first response. -->
            <v-list-item
              v-if="search.isLoading.value && !search.hasSearched.value"
              data-testid="header-search-loading"
            >
              <template #prepend>
                <v-progress-circular indeterminate size="14" width="2" class="mr-2" />
              </template>
              <v-list-item-title class="text-caption text-medium-emphasis">
                Searching…
              </v-list-item-title>
            </v-list-item>
          </template>

          <!-- Footer link to Advanced Search. Always present once the
               dropdown is open and there is a query. -->
          <template v-if="search.query.value.trim() !== ''">
            <v-divider class="my-1" />
            <v-list-item
              density="compact"
              :to="{ path: '/search' }"
              data-testid="header-search-advanced"
              @click="closeDropdown"
            >
              <template #prepend>
                <v-icon size="small" class="mr-1">mdi-magnify-plus-outline</v-icon>
              </template>
              <v-list-item-title class="text-caption">
                Advanced search for "{{ search.query.value }}" →
              </v-list-item-title>
            </v-list-item>
          </template>
        </v-list>
      </v-menu>
    </div>

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
      <!-- NTF1a: notification bell with unread badge -->
      <v-btn
        icon
        @click="toggleNotificationPanel"
      >
        <v-badge
          :content="unreadCount > 0 ? String(unreadCount) : ''"
          :model-value="unreadCount > 0"
          color="error"
          floating
        >
          <v-icon>mdi-bell-outline</v-icon>
        </v-badge>
      </v-btn>
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

  <!-- NTF1a: notification panel -->
  <NotificationPanel
    v-model="notificationPanelOpen"
    :notifications="notifications"
    :unread-count="unreadCount"
    :is-loading="notificationsLoading"
    @mark-read="markRead"
    @dismiss="dismiss"
  />

  <!-- Mobile navigation drawer -->
  <v-navigation-drawer
    v-model="mobileDrawerOpen"
    class="d-md-none"
    location="left"
    temporary
  >
    <v-list nav>
      <v-list-item title="Home" to="/" prepend-icon="mdi-home-outline" @click="mobileDrawerOpen = false" />
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
import { useTheme } from "vuetify";
import type { ContainerType } from "@dlr-shepard/backend-client";
import { useGlobalSearch } from "~/composables/context/useGlobalSearch";
import type { DataObjectSearchResult } from "~/composables/context/useDataObjectSearch";
import type { MyContainerSearchResult } from "~/composables/context/useContainerSearch";
import { useInstanceIdentity } from "~/composables/context/useInstanceIdentity";
import { useInstanceCapabilities } from "~/composables/context/useInstanceCapabilities";
import { useFetchNotifications } from "~/composables/context/useFetchNotifications";

const { status, signOut, signIn, data } = useAuth();

const { identity: instanceIdentity, fetch: fetchIdentity } = useInstanceIdentity();
const { fetch: fetchCapabilities } = useInstanceCapabilities();
watch(
  () => data.value?.accessToken,
  (token) => {
    if (token) {
      void fetchIdentity(token);
      void fetchCapabilities(token);
    }
  },
  { immediate: true },
);
const { public: publicConfig } = useRuntimeConfig();
const router = useRouter();

// NTF1a — notification bell + panel
const {
  unreadCount,
  notifications,
  isLoading: notificationsLoading,
  load: loadNotifications,
  markRead,
  dismiss,
  startPolling,
  stopPolling,
} = useFetchNotifications();

const notificationPanelOpen = ref(false);

function toggleNotificationPanel() {
  notificationPanelOpen.value = !notificationPanelOpen.value;
  if (notificationPanelOpen.value) {
    void loadNotifications();
  }
}

// Start polling when authenticated; stop on sign-out
watch(
  () => data.value?.accessToken,
  (token) => {
    if (token) startPolling();
    else stopPolling();
  },
  { immediate: true },
);

onUnmounted(() => stopPolling());

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

// ── Global header search (QW1 / UI-002) ───────────────────────────────────────

const search = useGlobalSearch({ debounceMs: 300 });
const dropdownOpen = ref(false);
const searchFieldRef = ref<{ $el: HTMLElement } | null>(null);
const resultListRef = ref<{ $el: HTMLElement } | null>(null);

// Dropdown visibility rules:
//   - open when there's any query text (so loading/empty/results all show)
//   - close on Escape, on a result click, or on outside click (v-menu handles)
watch(
  () => search.query.value,
  q => {
    dropdownOpen.value = (q ?? "").trim() !== "";
  },
);

function onSearchFocus() {
  if (search.query.value.trim() !== "") dropdownOpen.value = true;
}

function onSearchClear() {
  search.reset();
  dropdownOpen.value = false;
}

function closeDropdown() {
  dropdownOpen.value = false;
}

function onPick(kind: "collection", id: number) {
  if (kind === "collection") {
    closeDropdown();
    void router.push(`/collections/${id}`);
  }
}

function onPickDataObject(d: DataObjectSearchResult) {
  if (d.collectionId === undefined) return;
  closeDropdown();
  void router.push(`/collections/${d.collectionId}/dataobjects/${d.dataObjectId}`);
}

function onPickContainer(c: MyContainerSearchResult) {
  closeDropdown();
  const route = containerRoute(c.containerType, c.containerId);
  if (route) void router.push(route);
}

function containerRoute(t: ContainerType, id: number): string | null {
  switch (t) {
    case "FILE":
      return `/containers/files/${id}`;
    case "TIMESERIES":
      return `/containers/timeseries/${id}`;
    case "STRUCTUREDDATA":
      return `/containers/structureddata/${id}`;
    case "SPATIALDATA":
      return `/containers/spatialdata/${id}`;
    default:
      return null;
  }
}

function containerChipLabel(t: ContainerType): string {
  switch (t) {
    case "FILE":
      return "file";
    case "TIMESERIES":
      return "timeseries";
    case "STRUCTUREDDATA":
      return "structured";
    case "SPATIALDATA":
      return "spatial";
    default:
      return "container";
  }
}

function focusFirstResult() {
  // Move focus from the input to the first v-list-item inside the dropdown.
  const list = resultListRef.value?.$el;
  if (!list) return;
  const first = list.querySelector<HTMLElement>(
    '.v-list-item:not([disabled]):not(.v-list-subheader)',
  );
  first?.focus();
}

function onEnterPressed() {
  // Enter on the input ⇒ jump to Advanced Search with the current text.
  // (We don't auto-pick the first result because that's surprising for
  // a multi-kind dropdown; the v-list arrow-key path is the navigator.)
  if (search.query.value.trim() === "") return;
  closeDropdown();
  void router.push({ path: "/search" });
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
  position: relative;
}

@media (max-width: 599px) {
  .header-search {
    min-width: 80px;
    max-width: 160px;
  }
}

.header-search-results {
  // Drop the default huge subheader padding so the section feels denser.
  :deep(.v-list-subheader) {
    min-height: 24px;
    padding-inline-start: 12px !important;
    padding-inline-end: 12px !important;
    font-size: 11px;
    opacity: 0.7;
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
