<script lang="ts" setup>
/**
 * UI21 — Containers landing page.
 *
 * Adds over the previous version:
 *   (a) per-type icon + colour (registry-driven, rendered in ContainerList)
 *   (b) search-as-you-type via the existing v2 SearchApi (debounced 300ms
 *       in SearchField)
 *   (c) column filters (rendered in ContainerList)
 *   (d) relative-scale sizebar column (rendered in ContainerList)
 *   (e) per-row "open in detail" affordance + multi-select bulk delete
 *       with the referenced-data-infinite-retention orphan guard —
 *       the partition + confirmation dialog live here so the table
 *       component stays presentational.
 *   (f) hero empty state matching the home page style.
 *   (g) advanced-mode-only "grouped by referencing-Collection" accordion;
 *       basic mode keeps the flat list as before.
 *   (h) URL-param-driven filters: ?q=...&type=...&owner=...&group=...
 *       so paste-and-share works.
 */
import ContainerTypeSelect from "./ContainerTypeSelect.vue";
import { useSearchContainers } from "./useSearchContainers";
import type { BasicContainer, ContainerType } from "@dlr-shepard/backend-client";
import { SpatialDataContainerApi } from "@dlr-shepard/backend-client";
import { useShepardApi } from "~/composables/common/api/useShepardApi";
import { safeDeleteContainer } from "~/composables/container/safeDeleteContainer";
import { useAdvancedMode } from "~/composables/context/useAdvancedMode";
import { describeContainerType } from "~/utils/containerTypeRegistry";
import { handleContainerUpdate } from "~/utils/resourceUpdateBus";
import { handleError } from "~/utils/errorBus";
import { emitSuccess } from "~/utils/successBus";
import {
  stateFromUrlParams,
  urlParamsFromState,
  groupByCollection,
  type ContainerWithRefs,
} from "~/utils/containerListPage";

const itemsPerPage = 20;

const { serverItems, pageCount, loading, searchResultHint } =
  useSearchContainers(itemsPerPage);

const showCreateDialog = ref(false);

const { advancedMode } = useAdvancedMode();
const router = useRouter();
const route = useRoute();

// ── URL-param syncing (h) ───────────────────────────────────────────────────
// Bridge: the existing useContainerListQueryParams owns `page`, `searchText`,
// `sortBy`, `selectedFilter`. UI21 adds three new params (`q`, `type`,
// `owner`, `group`) on top. We translate `q` → `searchText` and
// `type` → `selectedFilter` on first navigation so paste-and-share URLs
// land coherently regardless of which casing the caller used.

onMounted(() => {
  const state = stateFromUrlParams(
    Object.fromEntries(
      Object.entries(route.query).map(([k, v]) => [
        k,
        Array.isArray(v) ? v.filter(x => typeof x === "string") as string[] : (v ?? null) as string | null,
      ]),
    ),
  );
  // Only push if the canonical (legacy) params don't already cover the
  // new ones. Avoid an infinite redirect loop by guarding on identity.
  const patch: Record<string, string | undefined> = {};
  let changed = false;
  if (state.q && !route.query.searchText) {
    patch.searchText = state.q;
    changed = true;
  }
  if (state.type && !route.query.selectedFilter) {
    patch.selectedFilter = state.type;
    changed = true;
  }
  if (state.page && !route.query.page) {
    patch.page = String(state.page);
    changed = true;
  }
  if (changed) {
    router.replace({
      path: route.path,
      query: { ...route.query, ...patch },
    });
  }
});

// Group view: basic mode is always "flat"; in advanced mode the user
// can switch to "collection" grouping via the URL or the toggle button.
const groupView = computed<"flat" | "collection">(() => {
  if (!advancedMode.value) return "flat";
  return route.query.group === "collection" ? "collection" : "flat";
});

function setGroupView(v: "flat" | "collection") {
  router.push({
    path: route.path,
    query: urlParamsFromState({
      ...stateFromUrlParams(
        Object.fromEntries(
          Object.entries(route.query).map(([k, val]) => [
            k,
            Array.isArray(val) ? val.filter(x => typeof x === "string") as string[] : (val ?? null) as string | null,
          ]),
        ),
      ),
      group: v,
    }),
  });
}

// ── Owner filter (h) — client-side ─────────────────────────────────────────
// `owner=…` is intentionally client-side: there's no backend filter for
// `createdBy` on the v2 SearchApi today, and the task spec forbids
// backend extension during the Jandex hang. Filed in aidocs/16 as
// UI21-BACKEND-Q for the server-side enrichment.

const ownerFilter = computed(() => {
  const raw = route.query.owner;
  return typeof raw === "string" && raw.trim() ? raw.trim().toLowerCase() : "";
});

const visibleServerItems = computed<BasicContainer[]>(() => {
  if (!ownerFilter.value) return serverItems.value;
  return serverItems.value.filter(c =>
    c.createdBy.toLowerCase().includes(ownerFilter.value),
  );
});

// ── Advanced-mode grouping (g) ──────────────────────────────────────────────
// The orphan/reference map is populated by ContainerList's `refs-resolved`
// listener. Mirror the events here so the grouped view can read them.

const refsById = reactive(new Map<number, number[] | null>());

function onRefsResolved(payload: { id: number; refs: number[] | null }) {
  refsById.set(payload.id, payload.refs);
}

const groupedView = computed<Map<string, BasicContainer[]>>(() => {
  const rows: ContainerWithRefs<BasicContainer>[] = visibleServerItems.value.map(c => ({
    container: c,
    referencingCollectionIds: refsById.get(c.id) ?? null,
  }));
  return groupByCollection(rows);
});

// ── Bulk delete (e) ─────────────────────────────────────────────────────────

interface DeletePayload {
  deletable: BasicContainer[];
  blockedByReferences: BasicContainer[];
  unknownReferenceState: BasicContainer[];
}

const pendingDelete = ref<DeletePayload | null>(null);
const isDeleting = ref(false);
const listRef = ref<{ clearSelection: () => void } | null>(null);

function onRequestBulkDelete(payload: DeletePayload) {
  pendingDelete.value = payload;
}

function cancelBulkDelete() {
  pendingDelete.value = null;
}

async function deleteContainerByType(container: BasicContainer): Promise<void> {
  // Use the per-type API; SPATIALDATA + BASIC + HDF5/VIDEO are
  // excluded above by the orphan-check rule, but be defensive here too.
  switch (container.type as ContainerType) {
    case "FILE": {
      // V2-SWEEP-003: v2 safe-delete (replaces v1 FileContainerApi.deleteFileContainer)
      const r = await safeDeleteContainer("file", container.id);
      if (!r.ok) throw new Error(`${r.conflict.referenceCount} active reference(s)`);
      return;
    }
    case "TIMESERIES": {
      // V2-SWEEP-003: v2 safe-delete (replaces v1 TimeseriesContainerApi.deleteTimeseriesContainer)
      const r = await safeDeleteContainer("timeseries", container.id);
      if (!r.ok) throw new Error(`${r.conflict.referenceCount} active reference(s)`);
      return;
    }
    case "STRUCTUREDDATA": {
      // V2-SWEEP-003: v2 safe-delete (replaces v1 StructuredDataContainerApi.deleteStructuredDataContainer)
      const r = await safeDeleteContainer("structured-data", container.id);
      if (!r.ok) throw new Error(`${r.conflict.referenceCount} active reference(s)`);
      return;
    }
    case "SPATIALDATA":
      await useShepardApi(SpatialDataContainerApi).value.deleteSpatialDataContainer({
        spatialDataContainerId: container.id,
      });
      return;
    default:
      // BASIC / HDF5 / VIDEO — no first-party delete endpoint we can
      // call here. Surface the limitation so the operator picks the
      // per-row affordance on the detail page.
      throw new Error(`Bulk delete is not supported for container type "${container.type}"`);
  }
}

async function confirmBulkDelete() {
  if (!pendingDelete.value) return;
  isDeleting.value = true;
  const target = pendingDelete.value.deletable;
  let succeeded = 0;
  let failed = 0;
  for (const c of target) {
    try {
      await deleteContainerByType(c);
      succeeded++;
    } catch (e) {
      failed++;
      handleError(e as Error, `deleting container ${c.id}`);
    }
  }
  isDeleting.value = false;
  pendingDelete.value = null;
  listRef.value?.clearSelection();
  handleContainerUpdate();
  if (succeeded > 0) {
    emitSuccess(
      `Deleted ${succeeded} container${succeeded === 1 ? "" : "s"}` +
      (failed > 0 ? ` (${failed} failed — see logs)` : ""),
    );
  }
}

// ── Empty / search-empty / populated state ─────────────────────────────────

const isEmpty = computed(
  () => !loading.value && visibleServerItems.value.length === 0 && !searchResultHint.value,
);
const isSearchEmpty = computed(
  () => !loading.value && visibleServerItems.value.length === 0 && !!searchResultHint.value,
);
</script>

<template>
  <div style="max-width: 1200px; margin: auto">
    <v-container fluid>
      <v-row>
        <!-- Hero header (f) — visually parallel to /collections landing -->
        <v-col class="py-14" cols="12">
          <div class="d-flex align-baseline">
            <h1 class="text-h1 text-textbody1 pr-4">Containers</h1>
            <Tooltip>
              <div>
                The data you reference in your project collections is stored in
                containers.
              </div>
              <div>
                There are different types of containers for the different types
                of data they store.
              </div>
            </Tooltip>
          </div>
          <div v-if="!isEmpty" class="text-body-1 text-textbody2 mt-2">
            Browse, search, and bulk-manage every container on this instance.
            Switch on Advanced mode to group by referencing collection.
          </div>
        </v-col>

        <template v-if="isEmpty">
          <v-col cols="12" class="d-flex flex-column align-center py-16">
            <v-icon icon="mdi-database-outline" size="72" color="textbody2" class="mb-4" />
            <div class="text-h4 text-semibold mb-2">No containers yet</div>
            <div class="text-body-1 text-textbody2 mb-2" style="max-width: 540px; text-align: center;">
              Containers hold the actual raw data — timeseries channels,
              uploaded files, structured tables, spatial extents. They are
              referenced from your collection DataObjects.
            </div>
            <div class="text-body-2 text-textbody2 mb-6" style="max-width: 540px; text-align: center;">
              Create your first container to start storing data, or import
              an existing dataset via the importer plugin.
            </div>
            <v-btn
              class="bg-primary text-canvas"
              variant="flat"
              size="large"
              @click="showCreateDialog = true"
            >
              <template #prepend>
                <v-icon color="canvas" icon="mdi-plus-circle" />
              </template>
              Create container
            </v-btn>
          </v-col>
        </template>

        <template v-else-if="isSearchEmpty">
          <v-col class="pb-4" cols="auto">
            <ContainerSearchField :search-result-hint="searchResultHint" />
          </v-col>
          <v-spacer />
          <v-col class="pb-4" cols="auto" justify-self="end">
            <v-btn
              :style="{ marginTop: '3px' }"
              class="bg-primary text-canvas"
              variant="flat"
              @click="showCreateDialog = true"
            >
              <template #prepend>
                <v-icon color="canvas" icon="mdi-plus-circle" />
              </template>
              Create new container
            </v-btn>
          </v-col>
          <v-col cols="12" class="d-flex flex-column align-center py-12">
            <v-icon icon="mdi-magnify" size="64" color="textbody2" class="mb-4" />
            <div class="text-h5 text-textbody1 mb-2">No containers found</div>
            <div class="text-body-1 text-textbody2">{{ searchResultHint }}</div>
          </v-col>
        </template>

        <template v-else>
          <v-col class="pb-4" cols="auto">
            <ContainerSearchField :search-result-hint="searchResultHint" />
          </v-col>
          <v-spacer />
          <v-col class="pb-4" cols="auto" justify-self="end">
            <v-btn
              :style="{ marginTop: '3px' }"
              class="bg-primary text-canvas"
              variant="flat"
              @click="showCreateDialog = true"
            >
              <template #prepend>
                <v-icon color="canvas" icon="mdi-plus-circle" />
              </template>
              Create new container
            </v-btn>
          </v-col>
          <v-col class="pt-4 pb-1" cols="12">
            <div class="d-flex align-center ga-3">
              <ContainerTypeSelect />
              <!-- Advanced-mode-only grouping toggle (g) -->
              <v-btn-toggle
                v-if="advancedMode"
                :model-value="groupView"
                density="compact"
                color="primary"
                mandatory
                variant="outlined"
                @update:model-value="(v: string | null) => setGroupView((v as 'flat' | 'collection') ?? 'flat')"
              >
                <v-btn value="flat" prepend-icon="mdi-format-list-bulleted" size="small">Flat</v-btn>
                <v-btn value="collection" prepend-icon="mdi-folder-multiple-outline" size="small">Grouped by collection</v-btn>
              </v-btn-toggle>
            </div>
          </v-col>

          <!-- Flat view: the existing rework with icons / column filters /
               sizebar / multi-select / per-row open affordance. -->
          <v-col v-if="groupView === 'flat'" cols="12">
            <ContainerList
              ref="listRef"
              :items-per-page="itemsPerPage"
              :loading="loading"
              :page-count="pageCount"
              :server-items="visibleServerItems"
              @request-bulk-delete="onRequestBulkDelete"
              @refs-resolved="onRefsResolved"
            />
          </v-col>

          <!-- Advanced-mode grouped view (g): expansion panels keyed by
               referencing-collection id. Orphans + unknowns get their own
               sections. -->
          <v-col v-else cols="12">
            <v-expansion-panels variant="accordion" multiple>
              <v-expansion-panel
                v-for="[groupKey, items] in Array.from(groupedView.entries())"
                :key="groupKey"
              >
                <v-expansion-panel-title>
                  <div class="d-flex align-center ga-2">
                    <v-icon
                      :icon="
                        groupKey === '__orphans__' ? 'mdi-alert-circle-outline'
                          : groupKey === '__unknown__' ? 'mdi-help-circle-outline'
                          : 'mdi-folder-outline'
                      "
                      size="20"
                    />
                    <span>
                      <template v-if="groupKey === '__orphans__'">
                        Orphan containers (no referencing collection)
                      </template>
                      <template v-else-if="groupKey === '__unknown__'">
                        Reference state unknown
                      </template>
                      <template v-else>
                        Collection #{{ groupKey }}
                      </template>
                    </span>
                    <v-chip size="x-small" variant="tonal">{{ items.length }}</v-chip>
                  </div>
                </v-expansion-panel-title>
                <v-expansion-panel-text>
                  <v-list density="compact">
                    <v-list-item
                      v-for="c in items"
                      :key="c.id"
                      :to="`/containers/${describeContainerType(c.type).urlSegment}${c.id}/`"
                      :prepend-icon="describeContainerType(c.type).icon"
                      :title="c.name"
                      :subtitle="describeContainerType(c.type).label + ' · created by ' + c.createdBy"
                    />
                  </v-list>
                </v-expansion-panel-text>
              </v-expansion-panel>
            </v-expansion-panels>
            <!-- Trigger the per-row CC1b lookups even in grouped view by
                 mounting a hidden ContainerList. It emits refs-resolved
                 the same way as the flat view, so groupedView re-runs as
                 each row's reference count lands. -->
            <div class="d-sr-only" aria-hidden="true">
              <ContainerList
                :items-per-page="itemsPerPage"
                :loading="loading"
                :page-count="1"
                :server-items="visibleServerItems"
                @request-bulk-delete="onRequestBulkDelete"
                @refs-resolved="onRefsResolved"
              />
            </div>
          </v-col>
        </template>
      </v-row>
    </v-container>

    <CreateContainerDialog
      v-if="showCreateDialog"
      v-model:show-dialog="showCreateDialog"
      @container-created="
        (id: number, type: ContainerType) =>
          $router.push(
            containersPath + describeContainerType(type).urlSegment + id,
          )
      "
    />

    <!-- Bulk-delete confirmation dialog (e). Splits the selection into
         deletable orphans, reference-blocked, and unknown-state buckets
         and surfaces each one so the operator sees exactly what will
         happen. -->
    <v-dialog
      v-if="pendingDelete"
      :model-value="true"
      max-width="640"
      persistent
      @update:model-value="cancelBulkDelete"
    >
      <v-card>
        <v-card-title class="d-flex align-center ga-2">
          <v-icon icon="mdi-delete-alert-outline" color="error" />
          Confirm bulk delete
        </v-card-title>
        <v-card-text>
          <div v-if="pendingDelete.deletable.length > 0" class="mb-4">
            <div class="text-subtitle-2 mb-2">
              Will delete {{ pendingDelete.deletable.length }} container{{ pendingDelete.deletable.length === 1 ? "" : "s" }}:
            </div>
            <v-list density="compact" max-height="200" class="bulk-list">
              <v-list-item
                v-for="c in pendingDelete.deletable"
                :key="c.id"
                :prepend-icon="describeContainerType(c.type).icon"
                :title="c.name"
                :subtitle="`#${c.id} · ${describeContainerType(c.type).label}`"
              />
            </v-list>
          </div>
          <v-alert
            v-if="pendingDelete.blockedByReferences.length > 0"
            type="warning"
            variant="tonal"
            class="mb-3"
          >
            <div class="text-subtitle-2">
              {{ pendingDelete.blockedByReferences.length }} container{{ pendingDelete.blockedByReferences.length === 1 ? "" : "s" }} skipped — still in use:
            </div>
            <div class="text-body-2 mt-1">
              These containers are referenced by at least one collection.
              Delete the references first, then retry.
            </div>
            <v-list density="compact" max-height="160" class="bulk-list mt-2">
              <v-list-item
                v-for="c in pendingDelete.blockedByReferences"
                :key="c.id"
                :prepend-icon="describeContainerType(c.type).icon"
                :title="c.name"
                :subtitle="`#${c.id} · in use by references`"
              />
            </v-list>
          </v-alert>
          <v-alert
            v-if="pendingDelete.unknownReferenceState.length > 0"
            type="info"
            variant="tonal"
            class="mb-3"
          >
            <div class="text-subtitle-2">
              {{ pendingDelete.unknownReferenceState.length }} container{{ pendingDelete.unknownReferenceState.length === 1 ? "" : "s" }} skipped — orphan check unavailable:
            </div>
            <div class="text-body-2 mt-1">
              Container types like BASIC and SPATIALDATA don't yet expose
              the linked-data-objects endpoint, so we can't prove they're
              orphans. Open the per-container detail page to delete them
              one at a time.
            </div>
          </v-alert>
          <div
            v-if="pendingDelete.deletable.length === 0"
            class="text-body-2 text-textbody2"
          >
            Nothing to delete — all selected containers are either in use
            or have no orphan check available.
          </div>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" :disabled="isDeleting" @click="cancelBulkDelete">Cancel</v-btn>
          <v-btn
            color="error"
            variant="flat"
            :disabled="pendingDelete.deletable.length === 0 || isDeleting"
            :loading="isDeleting"
            @click="confirmBulkDelete"
          >
            Delete {{ pendingDelete.deletable.length }} container{{ pendingDelete.deletable.length === 1 ? "" : "s" }}
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<style lang="scss" scoped>
.d-sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.bulk-list {
  background: rgba(var(--v-border-color), 0.05);
  border-radius: 4px;
}
</style>
