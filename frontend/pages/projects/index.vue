<script setup lang="ts">
/**
 * PROJ-NAV-1 — /projects top-nav route.
 *
 * Lists every Collection that carries the semantic annotation
 * `urn:shepard:project = true`. Frontend-filter-first: fetches all
 * Collections from the existing search endpoint, then resolves each
 * Collection's annotations concurrently to identify Projects.
 *
 * A backend `GET /v2/collections?filter=is-project=true` endpoint can
 * replace this N+1 pattern later (PROJ-REST-1-FILTER) when the
 * collection count grows large enough to matter.
 *
 * Spec: `aidocs/integrations/121-project-and-subcollections.md §4.2`.
 */
import { useFetchProjectCollections } from "~/composables/context/useFetchProjectCollections";

useHead({
  title: "Projects | shepard",
});

const router = useRouter();

const { projects, loading, error } = useFetchProjectCollections();

const programmeFilter = ref<string>("");

/** Unique programme values across all visible projects (for the side filter). */
const availableProgrammes = computed<string[]>(() => {
  const all = new Set<string>();
  for (const p of projects.value) {
    for (const prog of p.programmes) {
      all.add(prog);
    }
  }
  return Array.from(all).sort();
});

/** Projects filtered by programme (client-side, exact match). */
const filteredProjects = computed(() => {
  if (!programmeFilter.value) return projects.value;
  return projects.value.filter(p =>
    p.programmes.includes(programmeFilter.value),
  );
});

function navigateToCollection(collectionId: number | undefined) {
  if (collectionId === undefined) return;
  void router.push(`/collections/${collectionId}`);
}

// ── Hero image / gradient placeholder helpers ─────────────────────────────
function placeholderGradient(id: number | undefined): string {
  const n = id ?? 0;
  const hue1 = (n * 83) % 360;
  const hue2 = (hue1 + 40) % 360;
  return `linear-gradient(135deg, hsl(${hue1},60%,60%), hsl(${hue2},55%,50%))`;
}

function collectionInitial(name: string | undefined): string {
  return (name ?? "?").charAt(0).toUpperCase();
}
</script>

<template>
  <PageShell>
    <v-container data-testid="projects-page" width="100%" fluid>
      <v-row>
        <v-col cols="12" class="py-14">
          <div class="d-flex align-baseline">
            <h1 class="text-h1 text-textbody1 pr-4">Projects</h1>
            <v-chip
              v-if="!loading && projects.length > 0"
              size="small"
              variant="tonal"
              color="primary"
              class="ml-2"
              data-testid="projects-count-chip"
            >
              {{ projects.length }}
            </v-chip>
          </div>
          <p class="text-body-1 text-textbody2 mt-2">
            Collections marked as Projects — each bundles related Collections
            under one entry point.
          </p>
        </v-col>

        <!-- Loading state -->
        <v-col v-if="loading" cols="12" class="d-flex justify-center py-16">
          <v-progress-circular indeterminate color="primary" size="48" />
        </v-col>

        <!-- Error state -->
        <v-col v-else-if="error" cols="12" class="py-8">
          <v-alert
            type="error"
            variant="tonal"
            data-testid="projects-error"
          >
            {{ error }}
          </v-alert>
        </v-col>

        <!-- Empty state — no projects at all -->
        <template v-else-if="projects.length === 0">
          <v-col cols="12" class="d-flex flex-column align-center py-16">
            <v-icon
              icon="mdi-folder-star-multiple-outline"
              size="72"
              color="textbody2"
              class="mb-4"
            />
            <div
              class="text-h4 text-semibold mb-2"
              data-testid="projects-empty-heading"
            >
              No Projects yet
            </div>
            <div
              class="text-body-1 text-textbody2 mb-6 text-center"
              style="max-width: 480px"
              data-testid="projects-empty-body"
            >
              Create a Collection and mark it as a Project by adding the
              semantic annotation
              <code>urn:shepard:project = true</code>. It will appear here
              automatically.
            </div>
            <v-btn
              color="primary"
              variant="tonal"
              to="/collections"
              data-testid="projects-empty-goto-collections"
            >
              <v-icon start>mdi-folder-multiple-outline</v-icon>
              Go to Collections
            </v-btn>
          </v-col>
        </template>

        <!-- Project list -->
        <template v-else>
          <!-- Programme side-filter (only rendered when there are programmes) -->
          <v-col
            v-if="availableProgrammes.length > 0"
            cols="12"
            sm="auto"
            class="pb-4"
          >
            <v-select
              v-model="programmeFilter"
              :items="['', ...availableProgrammes]"
              label="Filter by programme"
              density="compact"
              variant="outlined"
              hide-details
              clearable
              style="min-width: 220px"
              data-testid="projects-programme-filter"
            >
              <template #item="{ item, props: itemProps }">
                <v-list-item
                  v-bind="itemProps"
                  :title="item.value || 'All programmes'"
                />
              </template>
              <template #selection="{ item }">
                <span>{{ item.value || "All programmes" }}</span>
              </template>
            </v-select>
          </v-col>

          <!-- Filtered-empty state -->
          <v-col
            v-if="filteredProjects.length === 0"
            cols="12"
            class="d-flex flex-column align-center py-12"
          >
            <v-icon
              icon="mdi-filter-off-outline"
              size="48"
              color="textbody2"
              class="mb-4"
            />
            <div class="text-h5 text-textbody1 mb-2">
              No Projects match this filter
            </div>
            <v-btn
              variant="text"
              color="primary"
              @click="programmeFilter = ''"
            >
              Clear filter
            </v-btn>
          </v-col>

          <!-- Project tiles grid -->
          <v-col v-else cols="12">
            <v-row>
              <v-col
                v-for="project in filteredProjects"
                :key="project.collection.id ?? project.collection.name"
                cols="12"
                sm="6"
                md="4"
                lg="3"
              >
                <v-card
                  class="project-card d-flex flex-column"
                  variant="outlined"
                  :data-testid="`project-card-${project.collection.id}`"
                  @click="navigateToCollection(project.collection.id)"
                >
                  <!-- Hero banner -->
                  <div class="project-card-hero">
                    <img
                      v-if="project.collection.heroImageUrl"
                      :src="project.collection.heroImageUrl"
                      :alt="project.collection.name"
                      class="project-card-hero-img"
                    >
                    <div
                      v-else
                      class="project-card-hero-placeholder"
                      :style="{
                        background: placeholderGradient(project.collection.id),
                      }"
                      aria-hidden="true"
                    >
                      <span class="project-card-initial text-white">
                        {{ collectionInitial(project.collection.name) }}
                      </span>
                    </div>
                    <!-- "Project" role badge -->
                    <v-chip
                      size="x-small"
                      color="primary"
                      variant="flat"
                      class="project-card-badge"
                      data-testid="project-card-badge"
                    >
                      <v-icon start size="x-small">
                        mdi-folder-star-outline
                      </v-icon>
                      Project
                    </v-chip>
                  </div>

                  <!-- Card body -->
                  <v-card-title
                    class="text-subtitle-1 font-weight-medium text-textbody1 pt-3 pb-0 px-4"
                    data-testid="project-card-name"
                  >
                    {{ project.collection.name }}
                  </v-card-title>

                  <v-card-text class="py-1 px-4 flex-grow-1">
                    <p
                      v-if="project.collection.description"
                      class="text-body-2 text-textbody2 project-card-description mb-2"
                      :title="project.collection.description"
                      data-testid="project-card-description"
                    >
                      {{
                        project.collection.description.length > 100
                          ? project.collection.description.slice(0, 100) + "…"
                          : project.collection.description
                      }}
                    </p>
                    <p
                      v-else
                      class="text-body-2 text-disabled mb-2"
                      aria-hidden="true"
                    >
                      No description
                    </p>

                    <!-- Programme chips -->
                    <div
                      v-if="project.programmes.length > 0"
                      class="d-flex flex-wrap ga-1 mb-2"
                      data-testid="project-card-programmes"
                    >
                      <v-chip
                        v-for="prog in project.programmes"
                        :key="prog"
                        size="x-small"
                        variant="tonal"
                        color="secondary"
                        :title="`Programme: ${prog}`"
                        class="project-programme-chip"
                        @click.stop="programmeFilter = prog"
                      >
                        {{ prog }}
                      </v-chip>
                    </div>

                    <!-- Stats row -->
                    <div class="d-flex align-center flex-wrap ga-2 mt-1">
                      <v-chip
                        size="x-small"
                        variant="tonal"
                        color="primary"
                        :title="`${(project.collection.dataObjectIds ?? []).length} Data Objects`"
                        data-testid="project-card-do-count"
                      >
                        <v-icon start size="x-small">mdi-cube-outline</v-icon>
                        {{ (project.collection.dataObjectIds ?? []).length }} DOs
                      </v-chip>

                      <span
                        v-if="project.collection.createdBy"
                        class="text-caption text-textbody2 ml-auto"
                        :title="`Created by ${project.collection.createdBy}`"
                        data-testid="project-card-creator"
                      >
                        {{ project.collection.createdBy }}
                      </span>
                    </div>
                  </v-card-text>

                  <v-card-actions class="px-4 pb-3 pt-1">
                    <v-btn
                      variant="tonal"
                      color="primary"
                      size="small"
                      density="comfortable"
                      data-testid="project-card-open-btn"
                      @click.stop="navigateToCollection(project.collection.id)"
                    >
                      Open
                      <v-icon end size="small">mdi-arrow-right</v-icon>
                    </v-btn>
                  </v-card-actions>
                </v-card>
              </v-col>
            </v-row>
          </v-col>
        </template>
      </v-row>
    </v-container>
  </PageShell>
</template>

<style scoped lang="scss">
.project-card {
  cursor: pointer;
  transition: box-shadow 0.15s ease, transform 0.12s ease;

  &:hover {
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.12);
    transform: translateY(-2px);
  }
}

.project-card-hero {
  position: relative;
  height: 120px;
  overflow: hidden;
  border-radius: 4px 4px 0 0;
  flex-shrink: 0;
}

.project-card-hero-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.project-card-hero-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.project-card-initial {
  font-size: 2.5rem;
  font-weight: 700;
  opacity: 0.85;
  user-select: none;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.25);
}

.project-card-badge {
  position: absolute;
  top: 6px;
  left: 6px;
  opacity: 0.9;
}

.project-card-description {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
  min-height: 2.6em;
}

.project-programme-chip {
  cursor: pointer;

  &:hover {
    opacity: 0.85;
  }
}
</style>
