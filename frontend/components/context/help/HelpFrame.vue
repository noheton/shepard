<script setup lang="ts">
import {
  DOC_SECTIONS,
  findDocPage,
  renderDocMarkdown,
  ALL_DOC_PAGES,
} from "~/utils/helpMarkdown";

// ── Route / query param ─────────────────────────────────────────────────────

const route = useRoute();
const router = useRouter();

const activePage = computed(() => {
  const p = route.query.page;
  return typeof p === "string" && p ? p : "index";
});

const activeDocPage = computed(
  () => findDocPage(activePage.value) ?? ALL_DOC_PAGES[0],
);

// ── Markdown content ─────────────────────────────────────────────────────────

const renderedHtml = ref<string>("");
const isLoading = ref(false);
const fetchError = ref<string | null>(null);

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
  } catch (err) {
    fetchError.value = String(err);
  } finally {
    isLoading.value = false;
  }
}

// Initial load happens in onMounted so fetch('/docs/...') only runs
// on the client — an immediate watch fires during SSR where a
// relative URL has no base and the fetch would throw.
onMounted(() => loadPage(activeDocPage.value.fetchPath));
watch(() => activeDocPage.value.fetchPath, path => loadPage(path));

// ── Mobile drawer ────────────────────────────────────────────────────────────

const { mobile } = useDisplay();
const drawerOpen = ref(false);

watch(mobile, m => {
  if (!m) drawerOpen.value = false;
});

function navigate(page: string) {
  router.push({ path: "/help", query: { page } });
  if (mobile.value) drawerOpen.value = false;
}
</script>

<template>
  <v-container fluid class="pa-0 fill-height align-start">
    <v-row no-gutters class="fill-height">
      <!-- Sidebar — permanent on md+, drawer on mobile -->
      <v-col v-if="!mobile" cols="12" md="3" lg="2" class="help-sidebar pa-4">
        <HelpSidebar
          :sections="DOC_SECTIONS"
          :active-page="activePage"
          @navigate="navigate"
        />
      </v-col>

      <!-- Mobile navigation drawer -->
      <v-navigation-drawer v-if="mobile" v-model="drawerOpen" temporary>
        <div class="pa-4">
          <HelpSidebar
            :sections="DOC_SECTIONS"
            :active-page="activePage"
            @navigate="navigate"
          />
        </div>
      </v-navigation-drawer>

      <!-- Content area -->
      <v-col cols="12" :md="mobile ? 12 : 9" :lg="mobile ? 12 : 10" class="pa-6">
        <div v-if="mobile" class="d-flex align-center mb-4">
          <v-btn
            variant="text"
            prepend-icon="mdi-menu"
            size="small"
            @click="drawerOpen = true"
          >
            Docs menu
          </v-btn>
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
        <div class="doc-content" v-html="renderedHtml" />
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
}
</style>
