<script setup lang="ts">
import type { Collection } from "@dlr-shepard/backend-client";
import { useCollectionListQueryParams } from "./useCollectionListQueryParams";
import { useWatchedCollections } from "~/composables/context/useWatchedCollections";

defineProps<{
  itemsPerPage: number;
  serverItems: Collection[];
  loading: boolean;
  pageCount: number;
}>();

const router = useRouter();
const { queryParams } = useCollectionListQueryParams();
const { isWatched, toggle: toggleWatched } = useWatchedCollections();

// LIC1: defensive accessor — the generated `Collection` client model may not
// yet expose `accessRights`, but the wire payload carries it. Keeping the
// cast out of template prevents the Vue compiler from misparsing `as unknown`
// in v-if attribute strings.
function rowAccessRights(item: Collection): string | null {
  return (item as unknown as { accessRights?: string | null }).accessRights ?? null;
}

const headers = [
  {
    title: "ID",
    key: "id",
    width: "8%",
    cellProps: {
      class: "text-body-1",
    },
  },
  {
    title: "Name",
    key: "name",
    width: "32%",
    cellProps: {
      class: "text-subtitle-2 word-wrap-anywhere",
    },
  },
  // LIC1: surface accessRights at list level so an auditor can scan a page of
  // collections and immediately see open vs. restricted vs. closed.
  {
    title: "Access",
    key: "accessRights",
    width: "12%",
    sortable: false,
    cellProps: {
      class: "text-body-2",
    },
  },
  {
    title: "Created by",
    key: "createdBy",
    width: "18%",
    cellProps: {
      class: "text-body-1 word-wrap-anywhere",
    },
  },
  {
    title: "Created at",
    key: "createdAt",
    width: "18%",
    sort: (a: Date, b: Date) => {
      return a.valueOf() - b.valueOf();
    },
    cellProps: {
      class: "text-body-1 word-wrap-anywhere",
    },
  },
  {
    title: "",
    key: "watched",
    width: "5%",
    sortable: false,
  },
];

function onSortBy(args: { key: string; order: "asc" | "desc" }[]) {
  if (args[0]) {
    router.push({
      path: collectionsPath,
      query: {
        ...router.currentRoute.value.query,
        page: 1,
        sortBy: JSON.stringify(args[0]),
      },
    });
    return;
  }
  router.push({
    path: collectionsPath,
    query: {
      ...router.currentRoute.value.query,
      page: 1,
      sortBy: undefined,
    },
  });
}

function onPageChange(page: number) {
  router.push({
    path: collectionsPath,
    query: { ...router.currentRoute.value.query, page },
  });
}
</script>

<template>
  <div style="overflow-x: auto">
  <DataTable
    :sort-by="queryParams.sortBy ? [queryParams.sortBy] : []"
    :header-props="{
      class: 'text-subtitle-2 text-textbody1',
    }"
    :cell-props="{
      class: 'text-textbody1',
    }"
    :headers="headers"
    :items="serverItems"
    :items-per-page="itemsPerPage"
    :loading="loading"
    @update:sort-by="onSortBy"
  >
    <template #item="rowProps">
      <v-data-table-row
        v-bind="rowProps"
        @click="router.push(collectionsPath + rowProps.item.id)"
      >
        <template #[`item.id`]>{{ rowProps.item.id }}</template>
        <template #[`item.name`]>{{ rowProps.item.name }}</template>
        <template #[`item.accessRights`]>
          <AccessRightsChip
            v-if="rowAccessRights(rowProps.item)"
            :access-rights="rowAccessRights(rowProps.item)!"
          />
          <span v-else class="text-disabled">—</span>
        </template>
        <template #[`item.createdBy`]>{{ rowProps.item.createdBy }}</template>
        <template #[`item.createdAt`]>
          {{ toShortDateString(rowProps.item.createdAt) }}
        </template>
        <template #[`item.watched`]>
          <v-btn
            icon
            variant="text"
            density="compact"
            size="small"
            :color="isWatched(rowProps.item.id!) ? 'amber-darken-2' : undefined"
            :title="isWatched(rowProps.item.id!) ? 'Remove from watched' : 'Add to watched'"
            @click.stop="toggleWatched(rowProps.item)"
          >
            <v-icon>{{ isWatched(rowProps.item.id!) ? 'mdi-star' : 'mdi-star-outline' }}</v-icon>
          </v-btn>
        </template>
      </v-data-table-row>
    </template>

    <template #bottom>
      <v-divider :thickness="8" color="divider2" opacity="1" />
      <v-pagination
        :model-value="queryParams.page ?? 1"
        :length="pageCount"
        :total-visible="6"
        @update:model-value="onPageChange"
      />
    </template>
  </DataTable>
  </div>
</template>

<style scoped lang="scss">
.v-table {
  :deep(.word-wrap-anywhere) {
    word-wrap: anywhere;
  }

  :deep(tbody) > tr > td {
    padding: 20px 24px !important;
  }

  :deep(tbody > tr) {
    cursor: pointer;
  }
}
</style>
