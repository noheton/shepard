<script setup lang="ts">
import {
  AdminPublicationsApi,
  type AdminPublication,
} from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";
import { AdminFragments } from "./adminMenuItems";

const publications = ref<AdminPublication[]>([]);
const loading = ref(false);
const fetchError = ref(false);

async function load() {
  loading.value = true;
  fetchError.value = false;
  try {
    publications.value = await useV2ShepardApi(
      AdminPublicationsApi,
    ).value.listPublications();
  } catch (error: unknown) {
    const status = (error as { response?: { status?: number } })?.response
      ?.status;
    if (status === 403 || status === 401) {
      fetchError.value = true;
    } else {
      handleError(error, "loading publications list");
      fetchError.value = true;
    }
  } finally {
    loading.value = false;
  }
}

load();

// ─── table columns ────────────────────────────────────────────────────────────

const headers = [
  { title: "PID", key: "pid", sortable: true },
  { title: "Entity Kind", key: "entityKind", sortable: true },
  { title: "Entity AppId", key: "entityAppId", sortable: true },
  { title: "Minter", key: "minterId", sortable: true },
  { title: "Minted At", key: "mintedAt", sortable: true },
  { title: "Published By", key: "publishedBy", sortable: true },
  { title: "Status", key: "digitalObjectMutability", sortable: true },
];

// ─── formatters ───────────────────────────────────────────────────────────────

function fmtDate(isoStr: string | null): string {
  if (!isoStr) return "—";
  try {
    return new Date(isoStr).toLocaleString();
  } catch {
    return isoStr;
  }
}

function truncateAppId(appId: string | null): string {
  if (!appId) return "—";
  return appId.length > 20 ? appId.slice(0, 18) + "…" : appId;
}

function isRetired(pub: AdminPublication): boolean {
  return pub.digitalObjectMutability === "retired";
}
</script>

<template>
  <div :id="AdminFragments.PUBLICATIONS" class="d-flex flex-column ga-4">
    <!-- Header row -->
    <div class="d-flex align-center ga-3">
      <h4 class="text-h4">Publications</h4>
      <v-btn
        icon="mdi-refresh"
        variant="text"
        size="small"
        :loading="loading"
        @click="load"
      />
    </div>

    <div class="text-body-2 text-medium-emphasis">
      All minted PIDs across this instance, ordered newest first.
      Active publications are shown in green; retired (KIP1f tombstone)
      in red. Use this panel as your DMP §5 data-security audit trail.
    </div>

    <!-- Error state -->
    <v-alert v-if="fetchError" type="error" variant="tonal">
      Could not load publications. Check that the backend is reachable and
      that you have the instance-admin role.
    </v-alert>

    <!-- Loading skeleton -->
    <v-progress-linear v-if="loading && publications.length === 0" indeterminate />

    <!-- Data table -->
    <v-card v-if="!fetchError" variant="outlined">
      <v-data-table
        :headers="headers"
        :items="publications"
        :loading="loading"
        density="compact"
        hover
        :items-per-page="25"
      >
        <!-- PID column: show full PID with copy button -->
        <!-- eslint-disable-next-line vue/valid-v-slot -->
        <template #item.pid="{ item }">
          <div class="d-flex align-center ga-1">
            <code class="text-caption text-truncate" style="max-width: 280px">
              {{ item.pid }}
            </code>
            <v-btn
              v-if="item.resolverUrl"
              :href="item.resolverUrl"
              target="_blank"
              icon="mdi-open-in-new"
              size="x-small"
              variant="text"
              density="compact"
              :title="item.resolverUrl"
            />
          </div>
        </template>

        <!-- Entity AppId: truncated with full value in tooltip -->
        <!-- eslint-disable-next-line vue/valid-v-slot -->
        <template #item.entityAppId="{ item }">
          <span :title="item.entityAppId ?? ''">
            {{ truncateAppId(item.entityAppId) }}
          </span>
        </template>

        <!-- Minted At: human-readable -->
        <!-- eslint-disable-next-line vue/valid-v-slot -->
        <template #item.mintedAt="{ item }">
          {{ fmtDate(item.mintedAt) }}
        </template>

        <!-- Status badge: active (green) / retired (red) -->
        <!-- eslint-disable-next-line vue/valid-v-slot -->
        <template #item.digitalObjectMutability="{ item }">
          <v-chip
            :color="isRetired(item) ? 'error' : 'success'"
            :variant="isRetired(item) ? 'tonal' : 'tonal'"
            size="small"
            :prepend-icon="
              isRetired(item)
                ? 'mdi-archive-remove-outline'
                : 'mdi-check-circle-outline'
            "
          >
            {{ isRetired(item) ? "retired" : "active" }}
          </v-chip>
        </template>

        <!-- Empty slot -->
        <template #no-data>
          <div class="text-center py-8 text-medium-emphasis">
            <v-icon size="48" class="mb-2">mdi-tag-off-outline</v-icon>
            <div>No publications minted yet on this instance.</div>
          </div>
        </template>
      </v-data-table>
    </v-card>
  </div>
</template>
