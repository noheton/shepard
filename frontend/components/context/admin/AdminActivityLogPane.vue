<script setup lang="ts">
/**
 * AdminActivityLogPane — instance-wide provenance activity log viewer.
 *
 * Backed by GET /v2/provenance/activities (ProvenanceApi#listActivities).
 * Instance-admins see all rows when no agent filter is set; casual users
 * are restricted server-side to their own rows (this pane lives under the
 * admin fragment so only instance-admins reach it).
 *
 * UI pattern reuses DataObjectProvLog.vue; pagination via "Load more"
 * (the activity endpoint takes `limit`, not page/size).
 */
import { AdminFragments } from "./adminMenuItems";
import { useFetchAdminActivities } from "~/composables/context/admin/useFetchAdminActivities";

const {
  activities,
  isLoading,
  hasMore,
  filterAgent,
  filterTargetKind,
  filterTargetAppId,
  applyFilters,
  loadMore,
  resetFilters,
} = useFetchAdminActivities();

// Client-side text filter (actor / summary / path)
const filterText = ref("");

// Action kind multi-select chips
const ACTION_KINDS = ["CREATE", "UPDATE", "READ", "DELETE", "EXECUTE"] as const;
const selectedActions = ref<string[]>([...ACTION_KINDS]);

function toggleAction(value: string) {
  const set = new Set(selectedActions.value);
  if (set.has(value)) set.delete(value);
  else set.add(value);
  selectedActions.value = Array.from(set);
}

const visibleRows = computed(() => {
  const f = filterText.value.trim().toLowerCase();
  return activities.value.filter((row) => {
    if (!selectedActions.value.includes(row.actionKind)) return false;
    if (!f) return true;
    return (
      (row.agentUsername ?? "").toLowerCase().includes(f) ||
      (row.summary ?? "").toLowerCase().includes(f) ||
      (row.path ?? "").toLowerCase().includes(f) ||
      (row.targetKind ?? "").toLowerCase().includes(f)
    );
  });
});

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

const TARGET_KIND_OPTIONS = [
  "Collection",
  "DataObject",
  "FileBundle",
  "TimeseriesReference",
  "FileReference",
  "StructuredDataReference",
  "UriReference",
  "GitReference",
  "LabJournalEntry",
  "Annotation",
];
</script>

<template>
  <div :id="AdminFragments.ACTIVITY_LOG" class="d-flex flex-column ga-4">
    <h4 class="text-h4">Activity Log</h4>

    <!-- Server-side filters -->
    <v-card variant="outlined">
      <v-card-title class="text-subtitle-1">Server-side filters</v-card-title>
      <v-card-text>
        <v-row dense>
          <v-col cols="12" md="4">
            <v-text-field
              v-model="filterAgent"
              label="Actor username"
              clearable
              hide-details
              density="compact"
              prepend-inner-icon="mdi-account-outline"
              @keyup.enter="applyFilters"
            />
          </v-col>
          <v-col cols="12" md="4">
            <v-autocomplete
              v-model="filterTargetKind"
              label="Target kind"
              clearable
              hide-details
              density="compact"
              :items="TARGET_KIND_OPTIONS"
              prepend-inner-icon="mdi-tag-outline"
            />
          </v-col>
          <v-col cols="12" md="4">
            <v-text-field
              v-model="filterTargetAppId"
              label="Target App ID"
              clearable
              hide-details
              density="compact"
              prepend-inner-icon="mdi-identifier"
              @keyup.enter="applyFilters"
            />
          </v-col>
        </v-row>
      </v-card-text>
      <v-card-actions>
        <v-btn variant="tonal" color="primary" @click="applyFilters">Apply</v-btn>
        <v-btn variant="text" @click="resetFilters">Reset</v-btn>
      </v-card-actions>
    </v-card>

    <!-- Client-side filter bar + action chips -->
    <div class="d-flex flex-wrap align-center ga-2">
      <v-chip
        v-for="opt in ACTION_KINDS"
        :key="opt"
        :variant="selectedActions.includes(opt) ? 'flat' : 'tonal'"
        :color="selectedActions.includes(opt) ? actionColor(opt) : undefined"
        size="small"
        :prepend-icon="actionIcon(opt)"
        @click="toggleAction(opt)"
      >
        {{ opt }}
      </v-chip>
      <v-spacer />
      <v-text-field
        v-model="filterText"
        density="compact"
        variant="outlined"
        hide-details
        placeholder="Filter (actor / summary / path / kind)"
        prepend-inner-icon="mdi-magnify"
        style="max-width: 320px"
      />
    </div>

    <centered-loading-spinner v-if="isLoading && activities.length === 0" />

    <EmptyListIcon
      v-else-if="!isLoading && activities.length === 0"
      label="No activity log entries found"
      hint="Activities are captured for write verbs on v2 endpoints. Historic v1 traffic does not appear here."
    />

    <template v-else>
      <v-progress-linear v-if="isLoading" indeterminate color="primary" />

      <v-table density="compact" class="activity-log">
        <thead>
          <tr>
            <th style="width: 1%; white-space: nowrap">When</th>
            <th style="width: 1%; white-space: nowrap">Action</th>
            <th>Actor</th>
            <th>Target</th>
            <th>Summary</th>
            <th style="width: 1%; white-space: nowrap">Status</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in visibleRows" :key="row.appId">
            <td :title="formatExact(row.startedAtMillis)" class="text-no-wrap">
              <span class="text-caption">{{ formatRelative(row.startedAtMillis) }}</span>
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
            <td class="text-no-wrap text-caption">{{ row.agentUsername }}</td>
            <td class="text-caption">
              <span v-if="row.targetKind" class="font-weight-medium">{{ row.targetKind }}</span>
              <span v-if="row.targetAppId" class="text-medium-emphasis d-block text-truncate" style="max-width: 200px" :title="row.targetAppId">
                {{ row.targetAppId }}
              </span>
              <span v-if="!row.targetKind && !row.targetAppId" class="text-medium-emphasis">—</span>
            </td>
            <td>
              <div class="text-body-2">{{ row.summary || "(no summary)" }}</div>
              <div v-if="row.method || row.path" class="text-caption text-medium-emphasis">
                {{ row.method }} {{ row.path }}
              </div>
            </td>
            <td class="text-no-wrap">
              <span v-if="row.status != null" :class="statusClass(row.status)" class="text-caption">
                {{ row.status }}
              </span>
              <span v-else class="text-medium-emphasis text-caption">—</span>
            </td>
          </tr>
        </tbody>
      </v-table>

      <div v-if="visibleRows.length === 0 && activities.length > 0" class="text-center text-medium-emphasis py-4">
        No rows match the current client-side filter.
      </div>

      <div v-if="hasMore" class="d-flex justify-center pt-2">
        <v-btn :loading="isLoading" variant="tonal" @click="loadMore">
          Load more
        </v-btn>
      </div>

      <div class="text-caption text-medium-emphasis text-center pt-1">
        Showing {{ visibleRows.length }} of {{ activities.length }} loaded rows
      </div>
    </template>
  </div>
</template>

<style scoped lang="scss">
.activity-log :deep(td),
.activity-log :deep(th) {
  font-size: 13px;
}
</style>
