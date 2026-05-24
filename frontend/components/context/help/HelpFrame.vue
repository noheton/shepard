<script setup lang="ts">
import {
  DOC_SECTIONS,
  findDocPage,
  renderDocMarkdown,
  ALL_DOC_PAGES,
  type DocPage,
} from "~/utils/helpMarkdown";

// ── Route / query param ─────────────────────────────────────────────────────

const route = useRoute();
const router = useRouter();

const activePage = computed(() => {
  const p = route.query.page;
  return typeof p === "string" && p ? p : "index";
});

const FALLBACK_PAGE: DocPage = ALL_DOC_PAGES[0] ?? {
  page: "index",
  title: "Overview",
  fetchPath: "/docs/index.md",
};

const activeDocPage = computed<DocPage>(
  () => findDocPage(activePage.value) ?? FALLBACK_PAGE,
);

// ── Markdown content ─────────────────────────────────────────────────────────

const renderedHtml = ref<string>("");
const isLoading = ref(false);
const fetchError = ref<string | null>(null);
const contentRoot = ref<HTMLElement | null>(null);

async function loadPage(fetchPath: string) {
  isLoading.value = true;
  fetchError.value = null;
  renderedHtml.value = "";
  try {
    const res = await fetch(fetchPath);
    if (!res.ok) {
      throw new Error(`HTTP ${res.status}`);
    }
    const raw = await res.text();
    renderedHtml.value = renderDocMarkdown(raw);
    // Reset search filter visibility for the new page.
    await nextTick();
    applySearchFilter();
    scrollToHashIfPresent();
  } catch (err) {
    fetchError.value = String(err);
  } finally {
    isLoading.value = false;
  }
}

watch(
  () => activeDocPage.value.fetchPath,
  path => loadPage(path),
  { immediate: true },
);

// ── Drawer state ─────────────────────────────────────────────────────────────
// Layout shown/hidden is driven by CSS media queries (Vuetify d-* utilities) to
// avoid the SSR/hydration mismatch that bit BUG #139 in collection.vue. The
// drawer's open/close is still client state.

const drawerOpen = ref(false);

function navigate(page: string) {
  router.push({ path: "/help", query: { page } });
  drawerOpen.value = false;
}

// ── In-page search (UI-013) ─────────────────────────────────────────────────
// Filter is a DOM-level toggle: each <section class="doc-section"> carries a
// data-search-text attribute (precomputed during render). On query change we
// add/remove a `doc-section--hidden` class. No re-render = no jank.

const searchQuery = ref("");
const matchCount = ref(0);
const totalSections = ref(0);

function applySearchFilter() {
  const root = contentRoot.value;
  if (!root) return;
  const sections = root.querySelectorAll<HTMLElement>(".doc-section");
  totalSections.value = sections.length;
  const q = searchQuery.value.trim().toLowerCase();
  if (!q) {
    sections.forEach(s => s.classList.remove("doc-section--hidden"));
    matchCount.value = sections.length;
    return;
  }
  let matched = 0;
  sections.forEach(s => {
    const text = s.dataset.searchText || "";
    if (text.includes(q)) {
      s.classList.remove("doc-section--hidden");
      matched += 1;
    } else {
      s.classList.add("doc-section--hidden");
    }
  });
  matchCount.value = matched;
}

watch(searchQuery, () => applySearchFilter());

function clearSearch() {
  searchQuery.value = "";
}

// ── Anchor / hash scroll (UI-013) ───────────────────────────────────────────
// When the user clicks an in-page anchor (#slug) or arrives with a hash in
// the URL, scroll to the matching heading. Native browser scroll runs before
// async markdown loads, so we re-trigger after fetch completes.

function scrollToHashIfPresent() {
  if (!process.client) return;
  const hash = route.hash;
  if (!hash || hash.length < 2) return;
  const id = hash.slice(1);
  const el = document.getElementById(id);
  if (el) {
    el.scrollIntoView({ behavior: "smooth", block: "start" });
  }
}

// Intercept clicks on .doc-heading-anchor so we update the URL hash + scroll
// smoothly without a full route reload.
function onContentClick(ev: MouseEvent) {
  const target = ev.target as HTMLElement | null;
  if (!target) return;
  const anchor = target.closest<HTMLAnchorElement>(".doc-heading-anchor");
  if (!anchor) return;
  const href = anchor.getAttribute("href") || "";
  if (!href.startsWith("#")) return;
  ev.preventDefault();
  const id = href.slice(1);
  // Update URL hash without re-fetching the page.
  router.replace({
    path: "/help",
    query: route.query,
    hash: `#${id}`,
  });
  const el = document.getElementById(id);
  if (el) el.scrollIntoView({ behavior: "smooth", block: "start" });
}

// Watch hash changes (e.g. user pastes a URL or clicks browser back).
watch(() => route.hash, () => scrollToHashIfPresent());
</script>

<template>
  <v-container fluid class="pa-0 fill-height align-start">
    <!-- Mobile (< md): hamburger button. CSS-only visibility via d-md-none. -->
    <v-app-bar-nav-icon
      class="ma-2 d-md-none"
      @click="drawerOpen = !drawerOpen"
    />

    <v-row no-gutters class="fill-height flex-nowrap">
      <!-- Sidebar — visible on md+ only, via CSS media query. -->
      <v-col
        cols="12"
        md="3"
        lg="2"
        class="help-sidebar pa-4 d-none d-md-block"
      >
        <HelpSidebar
          :sections="DOC_SECTIONS"
          :active-page="activePage"
          @navigate="navigate"
        />
      </v-col>

      <!-- Mobile navigation drawer (always in markup; hidden via d-md-none). -->
      <v-navigation-drawer
        v-model="drawerOpen"
        temporary
        class="d-md-none"
      >
        <div class="pa-4">
          <HelpSidebar
            :sections="DOC_SECTIONS"
            :active-page="activePage"
            @navigate="navigate"
          />
        </div>
      </v-navigation-drawer>

      <!-- Content area — full width below md, 9 cols at md, 10 at lg+. -->
      <v-col
        cols="12"
        md="9"
        lg="10"
        class="pa-6"
        style="min-width: 0"
      >
        <div class="d-md-none d-flex align-center mb-4">
          <v-btn
            variant="text"
            prepend-icon="mdi-menu"
            size="small"
            @click="drawerOpen = true"
          >
            Docs menu
          </v-btn>
        </div>

        <!-- In-page search (UI-013) — filters the current page's sections. -->
        <div class="help-search-bar mb-4" data-testid="help-search-bar">
          <v-text-field
            v-model="searchQuery"
            data-testid="help-search-input"
            density="compact"
            variant="outlined"
            hide-details
            clearable
            prepend-inner-icon="mdi-magnify"
            :placeholder="`Search in this page (${totalSections} sections)`"
            @click:clear="clearSearch"
          />
          <div
            v-if="searchQuery"
            class="text-caption text-medium-emphasis mt-1"
            data-testid="help-search-status"
          >
            <template v-if="matchCount > 0">
              {{ matchCount }} of {{ totalSections }} sections match
            </template>
            <template v-else>
              No sections match — try a different term, or
              <a href="#" data-testid="help-search-clear" @click.prevent="clearSearch">clear search</a>.
            </template>
          </div>
        </div>

        <v-progress-linear v-if="isLoading" indeterminate color="primary" class="mb-4" />

        <v-alert
          v-if="fetchError"
          type="error"
          class="mb-4"
        >
          Could not load "{{ activeDocPage.title }}": {{ fetchError }}
        </v-alert>

        <!-- Rendered markdown -->
        <!-- eslint-disable-next-line vue/no-v-html -->
        <div
          ref="contentRoot"
          class="doc-content"
          data-testid="help-content"
          v-html="renderedHtml"
          @click="onContentClick"
        />
      </v-col>
    </v-row>
  </v-container>
</template>

<style lang="scss" scoped>
.help-sidebar {
  border-right: 1px solid rgba(var(--v-border-color), var(--v-border-opacity));
  min-height: 100%;
  background-color: rgb(var(--v-theme-treeview));
}

.help-search-bar {
  max-width: 860px;
}
</style>

<style lang="scss">
/* Unscoped — styles for v-html rendered markdown */
.doc-content {
  max-width: 860px;
  line-height: 1.7;

  h1 { font-size: 1.8rem; margin-bottom: 1rem; margin-top: 0; }
  h2 { font-size: 1.4rem; margin-top: 2rem; margin-bottom: 0.75rem; }
  h3 { font-size: 1.15rem; margin-top: 1.5rem; margin-bottom: 0.5rem; }
  h4 { font-size: 1rem; margin-top: 1.2rem; }

  p { margin-bottom: 1rem; }

  a {
    color: rgb(var(--v-theme-primary));
    text-decoration: underline;
    &:hover { opacity: 0.8; }
  }

  code {
    font-family: monospace;
    font-size: 0.88em;
    background: rgba(var(--v-border-color), 0.15);
    padding: 0.1em 0.35em;
    border-radius: 3px;
  }

  pre {
    background: rgba(var(--v-border-color), 0.12);
    padding: 1em 1.25em;
    border-radius: 4px;
    overflow-x: auto;
    margin-bottom: 1.25rem;

    code {
      background: none;
      padding: 0;
      font-size: 0.85em;
    }
  }

  table {
    border-collapse: collapse;
    width: 100%;
    margin-bottom: 1.25rem;

    th, td {
      border: 1px solid rgba(var(--v-border-color), var(--v-border-opacity));
      padding: 0.45em 0.75em;
      text-align: left;
    }

    th {
      background: rgba(var(--v-border-color), 0.1);
      font-weight: 600;
    }

    tr:nth-child(even) td {
      background: rgba(var(--v-border-color), 0.04);
    }
  }

  blockquote {
    border-left: 4px solid rgb(var(--v-theme-primary));
    margin: 0 0 1rem;
    padding: 0.5rem 1rem;
    opacity: 0.85;
  }

  ul, ol {
    padding-left: 1.5rem;
    margin-bottom: 1rem;
  }

  li { margin-bottom: 0.3rem; }

  hr {
    border: none;
    border-top: 1px solid rgba(var(--v-border-color), var(--v-border-opacity));
    margin: 1.5rem 0;
  }

  // ── Anchor links on headings (UI-013) ────────────────────────────────────
  .doc-heading {
    position: relative;
    scroll-margin-top: 80px; // offset for sticky app bar

    .doc-heading-anchor {
      position: absolute;
      left: -1.25em;
      top: 0;
      width: 1em;
      text-align: right;
      opacity: 0;
      transition: opacity 0.12s ease-in;
      text-decoration: none;
      color: rgba(var(--v-theme-on-surface), 0.4);
      font-weight: normal;
      &:hover {
        color: rgb(var(--v-theme-primary));
        opacity: 1;
      }
    }

    &:hover .doc-heading-anchor,
    &:focus-within .doc-heading-anchor {
      opacity: 1;
    }
  }

  // ── Section-collapse for search (UI-013) ─────────────────────────────────
  .doc-section--hidden {
    display: none;
  }
}
</style>
