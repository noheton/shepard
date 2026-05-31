<script setup lang="ts">
import { useAdminPublications } from "~/composables/context/admin/useAdminPublications";
import { AdminFragments } from "./adminMenuItems";

const {
  items,
  isLoading,
  totalCount,
  page,
  hasMore,
  showRetired,
  filterEntityKind,
  filterMinterId,
  applyFilters,
  nextPage,
  prevPage,
  refresh,
} = useAdminPublications();

const TABLE_HEADERS = [
  { title: "PID", key: "pid", sortable: false },
  { title: "Entity Kind", key: "entityKind", sortable: false, width: "130px" },
  { title: "Entity AppId", key: "entityAppId", sortable: false },
  { title: "Version", key: "versionNumber", sortable: false, width: "80px" },
  { title: "Minted At", key: "mintedAt", sortable: false, width: "160px" },
  { title: "Minted By", key: "publishedBy", sortable: false },
  { title: "Minter", key: "minterId", sortable: false, width: "90px" },
  { title: "Status", key: "digitalObjectMutability", sortable: false, width: "100px" },
];

const KIND_OPTIONS = [
  { title: "All kinds", value: null },
  { title: "Data objects", value: "data-objects" },
  { title: "Collections", value: "collections" },
];

const MINTER_OPTIONS = [
  { title: "All minters", value: null },
  { title: "local", value: "local" },
  { title: "datacite", value: "datacite" },
  { title: "epic", value: "epic" },
  { title: "mock", value: "mock" },
];

function formatDate(iso: string | null | undefined): string {
  if (!iso) return "—";
  try {
    return new Date(iso).toLocaleString(undefined, {
      year: "numeric",
      month: "short",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  } catch {
    return iso;
  }
}

function isRetired(item: { digitalObjectMutability?: string | null }): boolean {
  return item.digitalObjectMutability === "retired";
}
</script>

<template>
  <div :id="AdminFragments.PUBLICATIONS" class="d-flex flex-column ga-4">
    <div class="d-flex align-center justify-space-between flex-wrap ga-2">
      <div>
        <h4 class="text-h4">Publications</h4>
        <p class="text-body-2 text-medium-emphasis mt-1">
          Instance-wide PID audit list — every minted persistent identifier,
          including retired records. Read-only.
          <span v-if="totalCount > 0" class="font-weight-medium">{{ totalCount }} total rows.</span>
        </p>
      </div>
      <v-btn
        variant="text"
        size="small"
        prepend-icon="mdi-refresh"
        :loading="isLoading"
        @click="refresh"
      >
        Refresh
      </v-btn>
    </div>

    <!-- Filters -->
    <v-card variant="outlined" class="pa-3">
      <div class="d-flex flex-wrap align-center ga-3">
        <v-select
          v-model="filterEntityKind"
          :items="KIND_OPTIONS"
          item-title="title"
          item-value="value"
          label="Entity kind"
          hide-details
          density="compact"
          variant="outlined"
          clearable
          style="max-width: 180px"
          @update:model-value="applyFilters"
        />
        <v-select
          v-model="filterMinterId"
          :items="MINTER_OPTIONS"
          item-title="title"
          item-value="value"
          label="Minter"
          hide-details
          density="compact"
          variant="outlined"
          clearable
          style="max-width: 160px"
          @update:model-value="applyFilters"
        />
        <v-switch
          v-model="showRetired"
          label="Show retired"
          hide-details
          density="compact"
          color="secondary"
          class="flex-shrink-0"
          @update:model-value="applyFilters"
        />
      </div>
    </v-card>

    <!-- Loading / empty state -->
    <centered-loading-spinner v-if="isLoading && items.length === 0" />

    <EmptyListIcon
      v-else-if="!isLoading && items.length === 0"
      label="No publications found"
    />

    <template v-else>
      <v-data-table
        :headers="TABLE_HEADERS"
        :items="items"
        :loading="isLoading"
        hide-default-footer
        density="compact"
        hover
        item-value="appId"
      >
        <!-- PID column — clickable external link -->
        <template #item.pid="{ item }">
          <div class="d-flex align-center ga-1" style="max-width: 380px;">
            <a
              :href="item.pid.startsWith('http') ? item.pid : undefined"
              :target="item.pid.startsWith('http') ? '_blank' : undefined"
              :rel="item.pid.startsWith('http') ? 'noopener noreferrer' : undefined"
              class="text-caption text-truncate"
              :class="item.pid.startsWith('http') ? 'text-primary' : ''"
              :title="item.pid"
              style="max-width: 340px; display: inline-block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;"
            >{{ item.pid }}</a>
            <v-btn
              icon="mdi-content-copy"
              size="x-small"
              variant="text"
              :title="`Copy PID: ${item.pid}`"
              @click.stop="() => { if (navigator?.clipboard) navigator.clipboard.writeText(item.pid) }"
            />
          </div>
        </template>

        <!-- Entity kind chip -->
        <template #item.entityKind="{ item }">
          <v-chip
            v-if="item.entityKind"
            size="x-small"
            variant="tonal"
            :color="item.entityKind === 'collections' ? 'secondary' : 'primary'"
          >
            {{ item.entityKind }}
          </v-chip>
          <span v-else class="text-medium-emphasis">—</span>
        </template>

        <!-- Entity appId — truncated -->
        <template #item.entityAppId="{ item }">
          <span class="text-caption text-truncate" :title="item.entityAppId ?? ''" style="max-width: 180px; display: inline-block;">
            {{ item.entityAppId ?? "—" }}
          </span>
        </template>

        <!-- Minted-at date -->
        <template #item.mintedAt="{ item }">
          <span class="text-caption text-no-wrap">{{ formatDate(item.mintedAt) }}</span>
        </template>

        <!-- Publisher -->
        <template #item.publishedBy="{ item }">
          <span class="text-caption">{{ item.publishedBy ?? "—" }}</span>
        </template>

        <!-- Minter chip -->
        <template #item.minterId="{ item }">
          <v-chip
            v-if="item.minterId"
            size="x-small"
            variant="outlined"
          >
            {{ item.minterId }}
          </v-chip>
          <span v-else class="text-medium-emphasis">—</span>
        </template>

        <!-- Status badge -->
        <template #item.digitalObjectMutability="{ item }">
          <v-chip
            :color="isRetired(item) ? 'grey' : 'success'"
            variant="tonal"
            size="small"
          >
            {{ isRetired(item) ? "Retired" : "Active" }}
          </v-chip>
        </template>
      </v-data-table>

      <!-- Pagination -->
      <div class="d-flex align-center ga-2 mt-1">
        <v-btn
          variant="tonal"
          size="small"
          :disabled="page === 0 || isLoading"
          prepend-icon="mdi-chevron-left"
          @click="prevPage"
        >
          Previous
        </v-btn>
        <span class="text-body-2">Page {{ page + 1 }}</span>
        <v-btn
          variant="tonal"
          size="small"
          :disabled="!hasMore || isLoading"
          append-icon="mdi-chevron-right"
          @click="nextPage"
        >
          Next
        </v-btn>
      </div>
    </template>
  </div>
</template>

<style scoped lang="scss"></style>
