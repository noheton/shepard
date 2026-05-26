<template>
  <div>
    <!-- Search + status filter row -->
    <div class="d-flex flex-wrap align-center ga-2 pb-3">
      <v-text-field
        v-model="searchInput"
        density="compact"
        variant="outlined"
        hide-details
        clearable
        placeholder="Search by name…"
        prepend-inner-icon="mdi-magnify"
        style="max-width: 320px"
        @update:model-value="onSearchChange"
      />
      <v-chip-group v-model="statusFilter" selected-class="v-chip--variant-flat">
        <v-chip value="" size="small" variant="tonal">All</v-chip>
        <v-chip
          v-for="s in STATUSES"
          :key="s"
          :value="s"
          size="small"
          variant="tonal"
        >{{ s }}</v-chip>
      </v-chip-group>
    </div>

    <v-progress-linear v-if="loading && pagedItems.length === 0" indeterminate />

    <EmptyListIcon
      v-else-if="!loading && pagedItems.length === 0"
      :label="searchInput || statusFilter ? 'No DataObjects match the filters' : 'No DataObjects yet'"
    />

    <v-table v-else density="compact" class="do-panel-table">
      <thead>
        <tr>
          <th>Name</th>
          <th style="width: 1%; white-space: nowrap">Status</th>
          <th style="width: 1%; white-space: nowrap" title="References attached to this DataObject">Refs</th>
          <th style="width: 1%; white-space: nowrap" title="Direct children DataObjects">Children</th>
          <th style="width: 1%; white-space: nowrap" title="Incoming DataObjectReferences">Incoming</th>
          <th v-if="anyTimeBounds" style="width: 140px; white-space: nowrap" title="Timeseries data coverage across collection timeline">Time span</th>
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
            <a href="#" class="reference-link" @click.prevent="navigateTo(row.id)">
              {{ row.name }}
            </a>
          </td>
          <td>
            <v-chip v-if="row.status" :color="statusColor(row.status)" size="x-small" variant="flat">
              {{ row.status }}
            </v-chip>
            <span v-else class="text-medium-emphasis">—</span>
          </td>
          <td class="text-no-wrap ref-kind-cell">
            <span v-if="row.refCount === 0" title="No references">
              <v-icon size="small" color="rgba(0,0,0,0.25)">mdi-link-variant-off</v-icon>
            </span>
            <template v-else>
              <v-badge v-if="row.tsCount > 0" :content="row.tsCount" color="primary" inline title="Timeseries">
                <v-icon size="small">mdi-chart-line</v-icon>
              </v-badge>
              <v-badge v-if="row.fileBundleCount > 0" :content="row.fileBundleCount" color="secondary" inline title="Files">
                <v-icon size="small">mdi-file-multiple-outline</v-icon>
              </v-badge>
              <v-badge v-if="row.sdCount > 0" :content="row.sdCount" color="info" inline title="Structured data">
                <v-icon size="small">mdi-code-json</v-icon>
              </v-badge>
              <v-badge v-if="row.videoCount > 0" :content="row.videoCount" color="warning" inline title="Video">
                <v-icon size="small">mdi-video-outline</v-icon>
              </v-badge>
            </template>
          </td>
          <td class="text-no-wrap">
            <v-badge v-if="row.childCount > 0" :content="row.childCount" color="secondary" inline>
              <v-icon size="small">mdi-file-tree</v-icon>
            </v-badge>
            <span v-else class="text-medium-emphasis">—</span>
          </td>
          <td class="text-no-wrap">
            <v-badge v-if="row.incomingCount > 0" :content="row.incomingCount" color="info" inline>
              <v-icon size="small">mdi-import</v-icon>
            </v-badge>
            <span v-else class="text-medium-emphasis">—</span>
          </td>
          <td v-if="anyTimeBounds" class="time-bar-cell">
            <v-tooltip v-if="row.timeBoundsStart != null && row.timeBoundsEnd != null" :text="timeBoundsTooltip(row.timeBoundsStart, row.timeBoundsEnd)" location="top">
              <template #activator="{ props: tp }">
                <svg v-bind="tp" class="time-bar-svg" width="120" height="10" xmlns="http://www.w3.org/2000/svg">
                  <rect x="0" y="3" width="120" height="4" rx="2" fill="rgba(0,0,0,0.08)" />
                  <rect
                    :x="timeBoundsBarLeft(row.timeBoundsStart) * 120"
                    y="3"
                    :width="Math.max(3, timeBoundsBarWidth(row.timeBoundsStart, row.timeBoundsEnd) * 120)"
                    height="4"
                    rx="2"
                    fill="rgb(var(--v-theme-primary))"
                  />
                </svg>
              </template>
            </v-tooltip>
            <span v-else class="text-medium-emphasis" style="font-size: 11px">—</span>
          </td>
          <td class="text-no-wrap text-medium-emphasis" :title="row.createdAt.toISOString()">
            {{ formatRelative(row.createdAt) }}
          </td>
        </tr>
      </tbody>
    </v-table>

    <!-- Prev / Next — no total count available yet from the backend -->
    <div v-if="page > 0 || hasMore" class="d-flex align-center justify-center ga-2 pt-3">
      <v-btn
        variant="text"
        size="small"
        prepend-icon="mdi-chevron-left"
        :disabled="page === 0 || loading"
        @click="page--"
      >Prev</v-btn>
      <span class="text-body-2 text-medium-emphasis">Page {{ page + 1 }}</span>
      <v-btn
        variant="text"
        size="small"
        append-icon="mdi-chevron-right"
        :disabled="!hasMore || loading"
        @click="page++"
      >Next</v-btn>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { useRouter } from "vue-router";
import { usePagedDataObjects } from "~/composables/context/usePagedDataObjects";
import { useTimeoutFn } from "@vueuse/core";

const props = defineProps<{
  collectionId: number;
  collectionAppId?: string | null;
}>();

const router = useRouter();
const collectionAppId = computed(() => props.collectionAppId ?? null);

const STATUSES = ["DRAFT", "IN_REVIEW", "READY", "PUBLISHED", "ARCHIVED"] as const;
type Status = (typeof STATUSES)[number];

const searchInput = ref("");
const serverName = ref("");
const statusFilter = ref<Status | "">("");
const page = ref(0);

// Debounce: avoid a server call on every keystroke
const { start: scheduleSearch } = useTimeoutFn(() => {
  serverName.value = searchInput.value;
  page.value = 0;
}, 300, { immediate: false });

function onSearchChange() {
  scheduleSearch();
}

// Reset page when status filter changes (client-side, no server round-trip needed)
watch(statusFilter, () => { page.value = 0; });

const { items: rawItems, loading, hasMore } = usePagedDataObjects({
  collectionId: props.collectionId,
  collectionAppId,
  name: serverName,
  page,
  pageSize: 25,
  includeTimeBounds: true,
});

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
  timeBoundsStart: number | null;
  timeBoundsEnd: number | null;
}

const rows = computed<Row[]>(() =>
  rawItems.value.map(d => ({
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
    timeBoundsStart: d.timeBoundsStart ?? null,
    timeBoundsEnd: d.timeBoundsEnd ?? null,
  })),
);

// Time-bounds derived values — null-safe
const anyTimeBounds = computed(() => rows.value.some(r => r.timeBoundsStart != null));

const globalTimeBoundsMin = computed<number | null>(() => {
  const vals = rows.value.map(r => r.timeBoundsStart).filter((v): v is number => v != null);
  return vals.length > 0 ? Math.min(...vals) : null;
});
const globalTimeBoundsMax = computed<number | null>(() => {
  const vals = rows.value.map(r => r.timeBoundsEnd).filter((v): v is number => v != null);
  return vals.length > 0 ? Math.max(...vals) : null;
});

function timeBoundsBarLeft(start: number): number {
  const gMin = globalTimeBoundsMin.value;
  const gMax = globalTimeBoundsMax.value;
  if (gMin == null || gMax == null || gMax === gMin) return 0;
  return (start - gMin) / (gMax - gMin);
}

function timeBoundsBarWidth(start: number, end: number): number {
  const gMin = globalTimeBoundsMin.value;
  const gMax = globalTimeBoundsMax.value;
  if (gMin == null || gMax == null || gMax === gMin) return 1;
  return (end - start) / (gMax - gMin);
}

function timeBoundsTooltip(startNs: number, endNs: number): string {
  // nanoseconds → milliseconds for Date
  const startMs = startNs / 1_000_000;
  const endMs   = endNs   / 1_000_000;
  return `${new Date(startMs).toLocaleString()} → ${new Date(endMs).toLocaleString()}`;
}

const pagedItems = computed<Row[]>(() => {
  if (!statusFilter.value) return rows.value;
  return rows.value.filter(r => r.status === statusFilter.value);
});

function navigateTo(dataObjectId: number) {
  router.push(`/collections/${props.collectionId}/dataobjects/${dataObjectId}`);
}

function statusColor(status: string): string {
  return ({ DRAFT: "grey", IN_REVIEW: "warning", READY: "info", PUBLISHED: "success", ARCHIVED: "default" } as Record<string, string>)[status] ?? "grey";
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
.do-row:hover {
  background-color: rgb(var(--v-theme-focus1));
}
.ref-kind-cell {
  white-space: nowrap;
  :deep(.v-badge) { margin-right: 6px; }
  :deep(.v-badge:last-child) { margin-right: 0; }
}
.reference-link {
  color: rgb(var(--v-theme-primary));
  text-decoration: none;
}
.reference-link:hover {
  text-decoration: underline;
}
.time-bar-cell {
  padding-top: 0 !important;
  padding-bottom: 0 !important;
  vertical-align: middle;
}
.time-bar-svg {
  display: block;
  cursor: default;
}
</style>
