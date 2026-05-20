<template>
  <div>
    <!-- Search + status filter row. Always-visible above the table so the
         "where is X" question has an entry point at first glance. -->
    <div class="d-flex flex-wrap align-center ga-2 pb-3">
      <v-text-field
        v-model="filterText"
        density="compact"
        variant="outlined"
        hide-details
        clearable
        placeholder="Search by name…"
        prepend-inner-icon="mdi-magnify"
        style="max-width: 320px"
      />
      <v-chip-group v-model="statusFilter" mandatory selected-class="v-chip--variant-flat">
        <v-chip value="all" size="small" variant="tonal">All ({{ totalCount }})</v-chip>
        <v-chip
          v-for="s in STATUSES"
          :key="s"
          :value="s"
          size="small"
          variant="tonal"
          :disabled="statusCounts[s] === 0"
        >
          {{ s }} ({{ statusCounts[s] }})
        </v-chip>
      </v-chip-group>
      <v-spacer />
      <v-chip
        v-if="hasRefsFilter"
        closable
        size="small"
        color="primary"
        variant="tonal"
        @click:close="hasRefsFilter = false"
      >
        Only DataObjects with references
      </v-chip>
      <v-btn
        v-else
        variant="text"
        size="small"
        prepend-icon="mdi-filter-outline"
        @click="hasRefsFilter = true"
      >
        Only with refs
      </v-btn>
    </div>

    <EmptyListIcon
      v-if="!loading && filteredItems.length === 0"
      :label="filterText || statusFilter !== 'all' || hasRefsFilter ? 'No DataObjects match the filters' : 'No DataObjects yet'"
    />

    <v-progress-linear v-else-if="loading && filteredItems.length === 0" indeterminate />

    <v-table v-else density="compact" class="do-panel-table">
      <thead>
        <tr>
          <th>Name</th>
          <th style="width: 1%; white-space: nowrap">Status</th>
          <th style="width: 1%; white-space: nowrap" :title="'References attached to this DataObject'">
            Refs
          </th>
          <th style="width: 1%; white-space: nowrap" :title="'Direct children DataObjects'">
            Children
          </th>
          <th style="width: 1%; white-space: nowrap" :title="'Incoming DataObjectReferences (other DOs pointing at this one)'">
            Incoming
          </th>
          <th style="width: 1%; white-space: nowrap">Created</th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="row in pagedItems"
          :key="row.id"
          class="do-row"
          @click="navigateTo(row.id)"
        >
          <td>
            <a
              href="#"
              class="reference-link"
              @click.prevent="navigateTo(row.id)"
            >
              {{ row.name }}
            </a>
          </td>
          <td>
            <v-chip
              v-if="row.status"
              :color="statusColor(row.status)"
              size="x-small"
              variant="flat"
            >
              {{ row.status }}
            </v-chip>
            <span v-else class="text-medium-emphasis">—</span>
          </td>
          <td class="text-no-wrap ref-kind-cell">
            <span v-if="row.refCount === 0" title="No references">
              <v-icon size="small" color="rgba(0,0,0,0.25)">mdi-link-variant-off</v-icon>
            </span>
            <template v-else>
              <v-badge
                v-if="row.tsCount > 0"
                :content="row.tsCount"
                color="primary"
                inline
                title="Timeseries references"
              >
                <v-icon size="small">mdi-chart-line</v-icon>
              </v-badge>
              <v-badge
                v-if="row.fileBundleCount > 0"
                :content="row.fileBundleCount"
                color="secondary"
                inline
                title="File-bundle references"
              >
                <v-icon size="small">mdi-file-multiple-outline</v-icon>
              </v-badge>
              <v-badge
                v-if="row.sdCount > 0"
                :content="row.sdCount"
                color="info"
                inline
                title="Structured-data references"
              >
                <v-icon size="small">mdi-code-json</v-icon>
              </v-badge>
              <v-badge
                v-if="row.videoCount > 0"
                :content="row.videoCount"
                color="warning"
                inline
                title="Video-stream references"
              >
                <v-icon size="small">mdi-video-outline</v-icon>
              </v-badge>
            </template>
          </td>
          <td class="text-no-wrap">
            <v-badge
              v-if="row.childCount > 0"
              :content="row.childCount"
              color="secondary"
              inline
            >
              <v-icon size="small">mdi-file-tree</v-icon>
            </v-badge>
            <span v-else class="text-medium-emphasis">—</span>
          </td>
          <td class="text-no-wrap">
            <v-badge
              v-if="row.incomingCount > 0"
              :content="row.incomingCount"
              color="info"
              inline
            >
              <v-icon size="small">mdi-import</v-icon>
            </v-badge>
            <span v-else class="text-medium-emphasis">—</span>
          </td>
          <td class="text-no-wrap text-medium-emphasis" :title="row.createdAt.toISOString()">
            {{ formatRelative(row.createdAt) }}
          </td>
        </tr>
      </tbody>
    </v-table>

    <div
      v-if="filteredItems.length > pageSize"
      class="d-flex justify-center pt-3"
    >
      <v-pagination
        v-model="page"
        :length="Math.ceil(filteredItems.length / pageSize)"
        :total-visible="6"
        density="comfortable"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * CollectionDataObjectsPanel — flat, searchable, paginated DataObjects
 * list on the Collection page. The first slice of #24 ("Collection-
 * scale navigation aids"): user reported that in foreign collections,
 * finding "where is X" is tedious because the sidebar tree alone
 * doesn't scale and there's no per-Collection search.
 *
 * Phase 1 (this slice) — name search, status filter chips, "only with
 * refs" toggle, paginated table sorted by name. Click any row to drill
 * into the DataObject detail page.
 *
 * Phase 2 (deferred) — reference-kind filter chips (videos only, TS
 * only, …). The current DataObjectIO carries only a flat
 * `referenceIds` array without kind discrimination, so kind-filter
 * needs either a new /v2/ endpoint that joins ref-kind counts, or
 * a client-side N+1 fetch (bad at scale). Left for a follow-up.
 */
import { computed, ref } from "vue";
import { useRouter } from "vue-router";
import { useFetchAllDataObjects } from "~/composables/context/useFetchAllDataObjects";

const props = defineProps<{
  collectionId: number;
  collectionAppId?: string | null;
}>();

const router = useRouter();
const collectionAppId = computed(() => props.collectionAppId ?? null);
const { dataObjects, loading } = useFetchAllDataObjects(props.collectionId, collectionAppId);

const STATUSES = ["DRAFT", "IN_REVIEW", "READY", "PUBLISHED", "ARCHIVED"] as const;
type Status = (typeof STATUSES)[number];

const filterText = ref("");
const statusFilter = ref<"all" | Status>("all");
const hasRefsFilter = ref(false);
const page = ref(1);
const pageSize = 25;

interface Row {
  id: number;
  name: string;
  status: string | null;
  refCount: number;
  tsCount: number;
  fileBundleCount: number;
  sdCount: number;
  videoCount: number;
  childCount: number;
  incomingCount: number;
  createdAt: Date;
}

const rows = computed<Row[]>(() =>
  dataObjects.value.map(d => ({
    id: d.id,
    name: d.name ?? `#${d.id}`,
    status: (d.status as string) ?? null,
    refCount: (d.referenceIds ?? []).length,
    tsCount: d.timeseriesCount ?? 0,
    fileBundleCount: d.fileCount ?? 0,
    sdCount: d.structuredDataCount ?? 0,
    videoCount: 0,
    childCount: (d.childrenIds ?? []).length,
    incomingCount: (d.incomingIds ?? []).length,
    createdAt: d.createdAt instanceof Date ? d.createdAt : new Date(d.createdAt as unknown as string),
  })),
);

const totalCount = computed(() => rows.value.length);

const statusCounts = computed<Record<Status, number>>(() => {
  const counts: Record<Status, number> = {
    DRAFT: 0, IN_REVIEW: 0, READY: 0, PUBLISHED: 0, ARCHIVED: 0,
  };
  for (const r of rows.value) {
    if (r.status && r.status in counts) counts[r.status as Status]++;
  }
  return counts;
});

const filteredItems = computed<Row[]>(() => {
  const term = filterText.value.trim().toLowerCase();
  return rows.value
    .filter(r => {
      if (term && !r.name.toLowerCase().includes(term)) return false;
      if (statusFilter.value !== "all" && r.status !== statusFilter.value) return false;
      if (hasRefsFilter.value && r.refCount === 0) return false;
      return true;
    })
    .sort((a, b) => a.name.localeCompare(b.name));
});

const pagedItems = computed<Row[]>(() => {
  const start = (page.value - 1) * pageSize;
  return filteredItems.value.slice(start, start + pageSize);
});

function navigateTo(dataObjectId: number) {
  router.push(`/collections/${props.collectionId}/dataobjects/${dataObjectId}`);
}

function statusColor(status: string): string {
  return (
    {
      DRAFT: "grey",
      IN_REVIEW: "warning",
      READY: "info",
      PUBLISHED: "success",
      ARCHIVED: "default",
    } as Record<string, string>
  )[status] ?? "grey";
}

function formatRelative(d: Date): string {
  const delta = Date.now() - d.getTime();
  if (delta < 60_000) return "just now";
  if (delta < 3_600_000) return `${Math.round(delta / 60_000)} min ago`;
  if (delta < 86_400_000) return `${Math.round(delta / 3_600_000)} h ago`;
  if (delta < 7 * 86_400_000) return `${Math.round(delta / 86_400_000)} d ago`;
  return d.toLocaleDateString();
}
</script>

<style scoped>
.do-panel-table :deep(td),
.do-panel-table :deep(th) {
  font-size: 13px;
}
.do-row {
  cursor: pointer;
}
.ref-kind-cell {
  white-space: nowrap;
  :deep(.v-badge) {
    margin-right: 6px;
  }
  :deep(.v-badge:last-child) {
    margin-right: 0;
  }
}
.do-row:hover {
  background-color: rgb(var(--v-theme-focus1));
}
.reference-link {
  color: rgb(var(--v-theme-primary));
  text-decoration: none;
}
.reference-link:hover {
  text-decoration: underline;
}
</style>
