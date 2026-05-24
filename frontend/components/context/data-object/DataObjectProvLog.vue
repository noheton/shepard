<template>
  <div>
    <div class="d-flex flex-wrap align-center ga-2 pb-3">
      <v-chip
        v-for="opt in actionFilters"
        :key="opt.value"
        :variant="selectedActions.includes(opt.value) ? 'flat' : 'tonal'"
        :color="selectedActions.includes(opt.value) ? actionColor(opt.value) : undefined"
        size="small"
        :prepend-icon="actionIcon(opt.value)"
        @click="toggleAction(opt.value)"
      >
        {{ opt.label }}
      </v-chip>
      <v-spacer />
      <v-text-field
        v-model="filterText"
        density="compact"
        variant="outlined"
        hide-details
        placeholder="Filter (actor / summary / path)"
        prepend-inner-icon="mdi-magnify"
        style="max-width: 280px"
      />
    </div>

    <v-progress-linear v-if="loading" indeterminate />

    <EmptyListIcon
      v-else-if="visibleRows.length === 0"
      :label="emptyLabel"
      :hint="emptyHint"
    />

    <v-table v-else density="compact" class="prov-log">
      <thead>
        <tr>
          <th style="width: 1%; white-space: nowrap">When</th>
          <th style="width: 1%; white-space: nowrap">Action</th>
          <th>Actor</th>
          <th>Summary</th>
          <th style="width: 1%; white-space: nowrap">Status</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in visibleRows" :key="row.appId">
          <td :title="formatExact(row.startedAtMillis)" class="text-no-wrap">
            {{ formatRelative(row.startedAtMillis) }}
          </td>
          <td>
            <v-chip
              :color="actionColor(row.actionKind)"
              size="x-small"
              variant="flat"
              :prepend-icon="actionIcon(row.actionKind)"
            >
              {{ row.actionKind }}
            </v-chip>
          </td>
          <td class="text-no-wrap">{{ row.agentUsername }}</td>
          <td>
            <div>{{ row.summary || "(no summary)" }}</div>
            <div v-if="row.method || row.path" class="text-caption text-medium-emphasis">
              {{ row.method }} {{ row.path }}
            </div>
          </td>
          <td class="text-no-wrap">
            <span
              v-if="row.status != null"
              :class="statusClass(row.status)"
            >
              {{ row.status }}
            </span>
          </td>
        </tr>
      </tbody>
    </v-table>

    <div v-if="hasMore" class="d-flex justify-center pt-3">
      <v-btn
        :loading="loading"
        variant="text"
        @click="loadMore"
      >
        Load more
      </v-btn>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * DataObjectProvLog — structured time-based provenance view, paired with
 * `DataObjectProvGraph.vue` (force-directed). Renders the entity's
 * activity log as a sortable, filterable Vuetify table.
 *
 * User feedback 2026-05-18: the force-directed graph "wobble" is nice
 * eye candy but a structured view (git-tree or time-based log) is
 * more useful for actually reading what happened. This component is
 * the time-based half; the git-tree (parent/predecessor chains) is a
 * separate slice once the table view ships.
 *
 * Backend: GET /v2/provenance/activities?targetAppId={appId}&limit=N.
 * Pagination is "load more" — bump the limit on each click. The
 * endpoint already supports `since` / `until` filters; this UI keeps
 * it simple and filters client-side for now (the typical entity has
 * <100 events).
 */
import { computed, onMounted, ref } from "vue";
import {
  ProvenanceApi,
  type ActivityIO,
} from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";
import {
  emptyStateHint,
  emptyStateLabel,
} from "~/utils/provenanceEmptyState";

const props = defineProps<{
  targetAppId: string;
}>();

const activities = ref<ActivityIO[]>([]);
const loading = ref(false);
const filterText = ref("");
const limit = ref(50);
const hasMore = ref(false);

const ACTION_KINDS = ["CREATE", "UPDATE", "READ", "DELETE", "EXECUTE"] as const;
const selectedActions = ref<string[]>([...ACTION_KINDS]);

const actionFilters = ACTION_KINDS.map(value => ({ value, label: value }));

function toggleAction(value: string) {
  const set = new Set(selectedActions.value);
  if (set.has(value)) set.delete(value);
  else set.add(value);
  selectedActions.value = Array.from(set);
}

function actionColor(action: string): string {
  return (
    {
      CREATE: "success",
      UPDATE: "info",
      DELETE: "error",
      READ: "grey",
      EXECUTE: "warning",
    } as Record<string, string>
  )[action] ?? "grey";
}

function actionIcon(action: string): string {
  return (
    {
      CREATE: "mdi-plus-circle-outline",
      UPDATE: "mdi-pencil-outline",
      DELETE: "mdi-delete-outline",
      READ: "mdi-eye-outline",
      EXECUTE: "mdi-play-circle-outline",
    } as Record<string, string>
  )[action] ?? "mdi-circle-outline";
}

function statusClass(status: number): string {
  if (status >= 500) return "text-error";
  if (status >= 400) return "text-warning";
  if (status >= 200 && status < 300) return "text-success";
  return "text-medium-emphasis";
}

function formatExact(ms: number): string {
  return new Date(ms).toISOString();
}

function formatRelative(ms: number): string {
  const delta = Date.now() - ms;
  const abs = Math.abs(delta);
  if (abs < 60_000) return "just now";
  if (abs < 3_600_000) return `${Math.round(abs / 60_000)} min ago`;
  if (abs < 86_400_000) return `${Math.round(abs / 3_600_000)} h ago`;
  if (abs < 7 * 86_400_000) return `${Math.round(abs / 86_400_000)} d ago`;
  return new Date(ms).toLocaleDateString();
}

async function load() {
  loading.value = true;
  try {
    const rows = await useV2ShepardApi(ProvenanceApi).value.listActivities({
      targetAppId: props.targetAppId,
      limit: limit.value,
    });
    activities.value = rows ?? [];
    hasMore.value = rows.length >= limit.value;
  } catch {
    activities.value = [];
    hasMore.value = false;
  } finally {
    loading.value = false;
  }
}

function loadMore() {
  limit.value += 50;
  void load();
}

const visibleRows = computed(() => {
  const f = filterText.value.trim().toLowerCase();
  return activities.value.filter(row => {
    if (!selectedActions.value.includes(row.actionKind)) return false;
    if (!f) return true;
    return (
      (row.agentUsername ?? "").toLowerCase().includes(f) ||
      (row.summary ?? "").toLowerCase().includes(f) ||
      (row.path ?? "").toLowerCase().includes(f)
    );
  });
});

/**
 * RDM-2026-05-24-004 closure: when the response is genuinely empty
 * (no Activity rows targeting this entity at all), explain *why* —
 * provenance capture is currently scoped to write verbs and only
 * resolves entities whose appId lands as the tail segment of a v2
 * path. Historic v1-id traffic against this DO will not surface
 * here until PROV-V1-NUMERIC-LOOKUP ships. Filter-induced empties
 * keep the original copy so the user knows the filter is the cause.
 */
const emptyLabel = computed(() => emptyStateLabel(activities.value.length));
const emptyHint = computed(() => emptyStateHint(activities.value.length));

onMounted(() => void load());
</script>

<style scoped>
.prov-log :deep(td),
.prov-log :deep(th) {
  font-size: 13px;
}
</style>
