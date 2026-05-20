<script setup lang="ts">
import { useFetchPermissionAuditLog } from "~/composables/context/admin/useFetchPermissionAuditLog";
import { AdminFragments } from "./adminMenuItems";

const {
  entries,
  isLoading,
  hasMore,
  page,
  filterEntityAppId,
  filterActor,
  filterFrom,
  filterTo,
  applyFilters,
  nextPage,
  prevPage,
  refresh,
} = useFetchPermissionAuditLog();

const headers = [
  { title: "Time", key: "occurredAt", sortable: false },
  { title: "Actor", key: "actorUsername", sortable: false },
  { title: "Entity", key: "entityAppId", sortable: false },
  { title: "Kind", key: "entityKind", sortable: false },
  { title: "Action", key: "action", sortable: false },
  { title: "Detail", key: "detailJson", sortable: false },
];

function actionColor(action: string) {
  if (action === "GRANT") return "success";
  if (action === "REVOKE") return "error";
  return "info";
}

function formatTime(iso: string) {
  try {
    return new Date(iso).toLocaleString();
  } catch {
    return iso;
  }
}
</script>

<template>
  <div :id="AdminFragments.PERMISSION_AUDIT_LOG" class="d-flex flex-column ga-4">
    <h4 class="text-h4">Permission Audit Log</h4>

    <v-card variant="outlined">
      <v-card-title class="text-subtitle-1">Filters</v-card-title>
      <v-card-text>
        <v-row dense>
          <v-col cols="12" md="3">
            <v-text-field
              v-model="filterEntityAppId"
              label="Entity App ID"
              clearable
              hide-details
              density="compact"
            />
          </v-col>
          <v-col cols="12" md="3">
            <v-text-field
              v-model="filterActor"
              label="Actor username"
              clearable
              hide-details
              density="compact"
            />
          </v-col>
          <v-col cols="12" md="3">
            <v-text-field
              v-model="filterFrom"
              label="From (ISO-8601)"
              clearable
              hide-details
              density="compact"
              placeholder="2026-01-01T00:00:00Z"
            />
          </v-col>
          <v-col cols="12" md="3">
            <v-text-field
              v-model="filterTo"
              label="To (ISO-8601)"
              clearable
              hide-details
              density="compact"
              placeholder="2026-12-31T23:59:59Z"
            />
          </v-col>
        </v-row>
      </v-card-text>
      <v-card-actions>
        <v-btn variant="tonal" color="primary" @click="applyFilters">Apply</v-btn>
        <v-btn variant="text" @click="refresh">Reset page</v-btn>
      </v-card-actions>
    </v-card>

    <centered-loading-spinner v-if="isLoading && entries.length === 0" />

    <EmptyListIcon
      v-else-if="!isLoading && entries.length === 0"
      label="No audit log entries found"
    />

    <template v-else>
      <v-data-table
        :headers="headers"
        :items="entries"
        :loading="isLoading"
        hide-default-footer
        density="compact"
      >
        <template #item.occurredAt="{ item }">
          <span class="text-caption text-no-wrap">{{ formatTime(item.occurredAt) }}</span>
        </template>

        <template #item.actorUsername="{ item }">
          <span>{{ item.actorUsername ?? "—" }}</span>
        </template>

        <template #item.entityKind="{ item }">
          <span class="text-caption">{{ item.entityKind ?? "—" }}</span>
        </template>

        <template #item.action="{ item }">
          <v-chip :color="actionColor(item.action)" size="small" label>
            {{ item.action }}
          </v-chip>
        </template>

        <template #item.detailJson="{ item }">
          <span class="text-caption text-truncate" style="max-width: 300px; display: inline-block;">
            {{ item.detailJson ?? "—" }}
          </span>
        </template>
      </v-data-table>

      <div class="d-flex align-center ga-2">
        <v-btn
          variant="tonal"
          size="small"
          :disabled="page === 0 || isLoading"
          @click="prevPage"
        >
          Previous
        </v-btn>
        <span class="text-body-2">Page {{ page + 1 }}</span>
        <v-btn
          variant="tonal"
          size="small"
          :disabled="!hasMore || isLoading"
          @click="nextPage"
        >
          Next
        </v-btn>
      </div>
    </template>
  </div>
</template>

<style scoped lang="scss"></style>
