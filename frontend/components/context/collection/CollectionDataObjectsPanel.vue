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
      <!-- Timeline drill-down date filter chip (COLL-TIMELINE-DRILLDOWN-FILTER-1) -->
      <v-chip
        v-if="drillDownDate"
        closable
        color="primary"
        size="small"
        prepend-icon="mdi-calendar-filter"
        @click:close="clearDrillDownDate"
      >
        Day: {{ drillDownDate }}
      </v-chip>
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

    <v-progress-linear v-if="loading && pagedItems.length === 0" indeterminate aria-label="Loading datasets" />

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
          tabindex="0"
          @keydown.enter="navigateTo(row)"
          @click="navigateTo(row)"
        >
          <td>
            <NuxtLink :to="rowHref(row)" class="reference-link">
              {{ row.name }}
            </NuxtLink>
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

    <!-- Pagination bar: summary + prev/next + jump-to-page -->
    <div v-if="page > 0 || hasMore || totalItems != null" class="d-flex align-center justify-space-between flex-wrap ga-2 pt-3">
      <!-- "Showing X–Y of Z" summary -->
      <span class="text-body-2 text-medium-emphasis">
        <template v-if="totalItems != null && totalItems > 0">
          Showing {{ page * pageSize + 1 }}–{{ Math.min((page + 1) * pageSize, totalItems) }} of {{ totalItems }}
        </template>
        <template v-else-if="totalItems === 0">
          No DataObjects
        </template>
        <template v-else>
          Page {{ page + 1 }}
        </template>
      </span>

      <!-- Prev / Next + jump-to-page -->
      <div class="d-flex align-center ga-2">
        <v-btn
          variant="text"
          size="small"
          prepend-icon="mdi-chevron-left"
          :disabled="page === 0 || loading"
          @click="page--"
        >Prev</v-btn>
        <v-btn
          variant="text"
          size="small"
          append-icon="mdi-chevron-right"
          :disabled="!hasMore || loading"
          @click="page++"
        >Next</v-btn>
        <v-text-field
          v-if="totalItems != null && totalPages > 1"
          v-model.number="jumpToPageInput"
          type="number"
          density="compact"
          variant="outlined"
          hide-details
          :min="1"
          :max="totalPages"
          style="width: 80px"
          label="Page"
          @keydown.enter="onJumpToPage"
          @blur="onJumpToPage"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { useRouter, useRoute } from "vue-router";
import { usePagedDataObjects } from "~/composables/context/usePagedDataObjects";
import { useTimeoutFn } from "@vueuse/core";

const props = defineProps<{
  collectionId: number;
  collectionAppId?: string | null;
}>();

const router = useRouter();
const route = useRoute();
const collectionAppId = computed(() => props.collectionAppId ?? null);

// ── Timeline drill-down date filter ──────────────────────────────────────────
// The collection timeline pane navigates here with ?date=YYYY-MM-DD when a
// user clicks a bin (COLL-TIMELINE-DRILLDOWN-FILTER-1). We map the calendar
// day to a 24 h createdAt window and pass it to the server-side filter.
const drillDownDate = computed<string | undefined>(() => {
  const d = route.query['date'];
  return typeof d === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(d) ? d : undefined;
});

const drillDownCreatedAfter = computed<string | undefined>(() => {
  const d = drillDownDate.value;
  return d ? `${d}T00:00:00.000Z` : undefined;
});

const drillDownCreatedBefore = computed<string | undefined>(() => {
  if (!drillDownDate.value) return undefined;
  const [y, m, day] = drillDownDate.value.split('-').map(Number);
  const next = new Date(Date.UTC(y!, m! - 1, day! + 1));
  return next.toISOString().replace(/\.\d{3}Z$/, '.000Z');
});

function clearDrillDownDate(): void {
  const q = { ...route.query };
  delete q['date'];
  void router.replace({ query: q });
}

const STATUSES = [
  "DRAFT", "IN_REVIEW", "READY", "PUBLISHED", "ARCHIVED", "FAILED",
  // MFG1 / QM1a — EN 9100 quality-engineering statuses
  "NCR_OPEN", "ON_HOLD", "REJECTED", "CERTIFIED", "CONCESSION_PENDING",
] as const;
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

// Reset page when status filter changes and trigger a server-side refetch
watch(statusFilter, () => { page.value = 0; });

const pageSize = 25;

const { items: rawItems, loading, hasMore, totalItems } = usePagedDataObjects({
  collectionId: props.collectionId,
  collectionAppId,
  name: serverName,
  status: statusFilter,
  page,
  pageSize,
  includeTimeBounds: true,
  createdAfter: drillDownCreatedAfter,
  createdBefore: drillDownCreatedBefore,
});

// Reset to page 0 when the drill-down date filter changes
watch(drillDownDate, () => { page.value = 0; });

// Computed total pages for the page-jump widget
const totalPages = computed(() => (totalItems.value != null ? Math.ceil(totalItems.value / pageSize) : 0));

// Page-jump input: 1-indexed for display; clamp + convert to 0-indexed on commit
const jumpToPageInput = ref<number>(1);
watch(page, (p) => { jumpToPageInput.value = p + 1; });

function onJumpToPage() {
  const raw = jumpToPageInput.value;
  if (!Number.isFinite(raw)) return;
  const clamped = Math.max(1, Math.min(Math.round(raw), totalPages.value || 1));
  jumpToPageInput.value = clamped;
  page.value = clamped - 1;
}

interface Row {
  id: number;
  /**
   * v2 appId (UUID v7) for route construction. The v2 list endpoint
   * always emits `appId` as identity (DB-OPT5 default fields). When the
   * v1 fallback path emits a row without an `appId` we fall back to the
   * numeric id for the link, which the route param parser still accepts
   * via the legacy /collections/123 deep-link path.
   */
  appId: string | null;
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
    appId: (d as unknown as { appId?: string | null }).appId ?? null,
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

// Server-side status filter: rows already contain only the matching status.
// Alias for template compatibility.
const pagedItems = rows;

/**
 * Build a route href for a DataObject row.
 *
 * Per CLAUDE.md "frontend builds on /v2/ exclusively + appId routes":
 * routes carry the v2 appId (UUID v7), never the numeric Neo4j id. We
 * prefer the parent collection's appId + the row's appId; if either is
 * absent (legacy v1-only fallback path) we fall back to the numeric id —
 * the route param parser (`parseIdLike`) accepts both shapes so the link
 * still resolves on the destination page.
 */
function rowHref(row: { id: number; appId: string | null }): string {
  const colSegment = props.collectionAppId ?? props.collectionId;
  const doSegment = row.appId ?? row.id;
  return `/collections/${colSegment}/dataobjects/${doSegment}`;
}

function navigateTo(row: { id: number; appId: string | null }) {
  router.push(rowHref(row));
}

function statusColor(status: string): string {
  return ({
    DRAFT: "grey",
    IN_REVIEW: "warning",
    READY: "info",
    PUBLISHED: "success",
    ARCHIVED: "default",
    FAILED: "red",
    // MFG1 / QM1a — EN 9100 quality-engineering statuses
    NCR_OPEN: "error",
    ON_HOLD: "orange",
    REJECTED: "error",
    CERTIFIED: "success",
    CONCESSION_PENDING: "warning",
  } as Record<string, string>)[status] ?? "grey";
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
.do-row:hover,
.do-row:focus-visible {
  background-color: rgb(var(--v-theme-focus1));
  outline: 2px solid rgb(var(--v-theme-primary));
  outline-offset: -2px;
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
