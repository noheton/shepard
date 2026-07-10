<!--
PROJ-NAV-1 — top-nav route /projects.

Cross-reference: aidocs/integrations/121-project-and-subcollections.md §4.2.

Lists every Collection that carries urn:shepard:project = "true". Each row
clicks through to the Project's Collection-detail page (which renders the
CollectionSubCollectionsPanel from §4.1).

Side filter on programme value is client-side at first; a backend filter
becomes a follow-up when the project count grows.
-->
<script setup lang="ts">
import { useProjectList, type ProjectIO } from "~/composables/context/useProject";
import { naturalSort } from "~/utils/naturalSort";

useHead({ title: "Projects | shepard" });

const router = useRouter();
const { projectAppIds, isLoading: isListLoading, error: listError } = useProjectList();

// Hydrate per-Project envelopes from /v2/projects/{appId} so the list page can
// render programmes + counts inline. Each fetch runs in parallel and shows a
// per-row skeleton until it settles. Backend will batch this when the Project
// count grows; for now N≤20 in practice.
const projectsById = ref<Record<string, ProjectIO | null>>({});

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

async function loadOne(appId: string) {
  try {
    const { data: session } = useAuth();
    const accessToken = session.value?.accessToken;
    const url = `${v2BaseUrl()}/v2/projects/${appId}`;
    const response = await fetch(url, {
      headers: {
        Authorization: `Bearer ${accessToken}`,
        Accept: "application/json",
      },
    });
    if (!response.ok) {
      projectsById.value[appId] = null;
      return;
    }
    projectsById.value[appId] = await response.json();
  } catch (e) {
    projectsById.value[appId] = null;
    handleError(e, `fetching Project ${appId}`);
  }
}

watch(projectAppIds, async (ids) => {
  // hydrate in parallel
  await Promise.all((ids ?? []).map(loadOne));
}, { immediate: true });

// ── Programme side-filter ─────────────────────────────────────────────────
const programmeFilter = ref<string | null>(null);

const allProgrammes = computed<string[]>(() => {
  const set = new Set<string>();
  for (const p of Object.values(projectsById.value)) {
    if (!p) continue;
    for (const pg of p.programmes ?? []) set.add(pg);
  }
  // UIRULE-DROPDOWN-SEARCH-SORT: natural (numeric-aware) order over programme names.
  return naturalSort(Array.from(set));
});

const filteredProjects = computed<ProjectIO[]>(() => {
  const projects: ProjectIO[] = [];
  for (const id of projectAppIds.value) {
    const p = projectsById.value[id];
    if (!p) continue;
    if (programmeFilter.value && !(p.programmes ?? []).includes(programmeFilter.value)) continue;
    projects.push(p);
  }
  return projects;
});

function lastActivityLabel(millis: number | null | undefined): string {
  if (!millis || millis <= 0) return "";
  return new Date(millis).toLocaleString();
}

function openProject(appId: string) {
  void router.push(`/collections/${appId}`);
}
</script>

<template>
  <PageShell>
    <v-container fluid>
      <v-row>
        <v-col cols="12" class="py-14">
          <h1 class="text-h1 text-textbody1">Projects</h1>
          <p class="text-body-1 text-textbody2 mt-2">
            Top-level multi-step research efforts — bundles of one or more
            Collections (e.g. MFFD Upper Shell, PLUTO, LUMEN). Click a row
            to open the Project's Collection-detail page.
          </p>
        </v-col>

        <!-- programme side-filter -->
        <v-col v-if="allProgrammes.length > 0" cols="12" md="auto" class="pb-4">
          <v-autocomplete
            v-model="programmeFilter"
            :items="[{ title: 'All programmes', value: null }, ...allProgrammes.map((p) => ({ title: p, value: p }))]"
            auto-select-first
            label="Filter by programme"
            density="compact"
            variant="outlined"
            clearable
            hide-details
            style="min-width: 280px"
            data-testid="project-programme-filter"
          />
        </v-col>

        <v-col cols="12">
          <v-card variant="outlined">
            <v-progress-linear
              v-if="isListLoading"
              indeterminate
              color="primary"
            />

            <v-card-text v-if="listError" class="text-error">
              {{ listError }}
            </v-card-text>

            <v-card-text
              v-else-if="!isListLoading && filteredProjects.length === 0 && projectAppIds.length === 0"
              class="text-center py-16"
            >
              <v-icon icon="mdi-folder-multiple-outline" size="72" class="mb-4" color="textbody2" />
              <div class="text-h5 text-textbody1 mb-2">No Projects yet</div>
              <div class="text-body-2 text-textbody2">
                Mark a Collection as a Project by adding the
                <code>urn:shepard:project = "true"</code> annotation.
              </div>
            </v-card-text>

            <v-list
              v-else
              lines="two"
              density="comfortable"
              data-testid="project-list"
            >
              <v-list-item
                v-for="p in filteredProjects"
                :key="p.appId"
                @click="openProject(p.appId)"
              >
                <template #prepend>
                  <v-icon icon="mdi-folder-multiple" size="large" color="primary" />
                </template>
                <v-list-item-title class="text-h6">
                  {{ p.name }}
                </v-list-item-title>
                <v-list-item-subtitle>
                  <v-chip
                    v-for="prog in p.programmes"
                    :key="prog"
                    size="x-small"
                    variant="tonal"
                    color="secondary"
                    class="mr-2"
                    prepend-icon="mdi-flag-outline"
                  >{{ prog }}</v-chip>
                  <span v-if="p.subCollectionCount > 0" class="text-caption text-textbody2">
                    {{ p.subCollectionCount }} sub-Collection{{ p.subCollectionCount === 1 ? '' : 's' }}
                    · {{ p.aggregateDoCount.toLocaleString() }} DataObjects
                  </span>
                  <span v-if="p.lastActivityMillis" class="text-caption text-textbody2 ml-2">
                    · last activity {{ lastActivityLabel(p.lastActivityMillis) }}
                  </span>
                </v-list-item-subtitle>
                <template #append>
                  <v-icon icon="mdi-chevron-right" />
                </template>
              </v-list-item>
            </v-list>
          </v-card>
        </v-col>
      </v-row>
    </v-container>
  </PageShell>
</template>
