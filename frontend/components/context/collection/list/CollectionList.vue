<script setup lang="ts">
import type { Collection } from "@dlr-shepard/backend-client";
import { useCollectionListQueryParams } from "./useCollectionListQueryParams";
import { useWatchedCollections } from "~/composables/context/useWatchedCollections";
import { useAdvancedMode } from "~/composables/context/useAdvancedMode";
import { descriptionPreview, toShortDateString } from "~/utils/helpers";

defineProps<{
  itemsPerPage: number;
  serverItems: Collection[];
  loading: boolean;
  pageCount: number;
}>();

const router = useRouter();
const { queryParams } = useCollectionListQueryParams();
const { isWatched, toggle: toggleWatched } = useWatchedCollections();
const { advancedMode } = useAdvancedMode();

// UI-011 (2026-05-24): "large" collection threshold for the # DOs chip.
// 1000+ DataObjects pulls the row out of the "small showcase" bucket and
// signals to the picker "this is a real workhorse collection". Conservative
// — no further bands until we have evidence the gradation helps.
const LARGE_COLLECTION_THRESHOLD = 1000;

// UI-011: Description preview chars in the list cell. ~120 keeps the row
// at a single line for typical descriptions; longer text gets a "…" tail
// and the full markdown is reachable on the detail page.
const DESCRIPTION_PREVIEW_CHARS = 120;

// Headers are computed so we can hide the numeric ID column when the user
// is not in advanced mode. The ID is the legacy long Neo4j PK and will go
// away with the appId migration; for normal users it's a noisy column.
const headers = computed(() => {
  const cols = [];
  if (advancedMode.value) {
    cols.push({
      title: "ID",
      key: "id",
      width: "8%",
      cellProps: { class: "text-body-2 text-textbody2" },
    });
  }
  cols.push(
    {
      title: "Name",
      key: "name",
      width: advancedMode.value ? "22%" : "26%",
      cellProps: { class: "text-subtitle-2 word-wrap-anywhere" },
    },
    {
      title: "Description",
      key: "description",
      width: "32%",
      sortable: false,
      cellProps: { class: "text-body-2 text-textbody2 word-wrap-anywhere" },
    },
    {
      title: "# DOs",
      key: "doCount",
      width: "10%",
      align: "end" as const,
      // Client-side sort by computed count — `Collection.dataObjectIds` is
      // already on the v2 list response, no extra fetch.
      sort: (_a: unknown, _b: unknown, itemA: Collection, itemB: Collection) => {
        return (itemA.dataObjectIds?.length ?? 0) - (itemB.dataObjectIds?.length ?? 0);
      },
      cellProps: { class: "text-body-1" },
    },
    {
      title: "Last updated",
      key: "updatedAt",
      width: "13%",
      sort: (a: Date | null | undefined, b: Date | null | undefined) => {
        return (a?.valueOf() ?? 0) - (b?.valueOf() ?? 0);
      },
      cellProps: { class: "text-body-2 text-textbody2 word-wrap-anywhere" },
    },
    {
      title: "Created by",
      key: "createdBy",
      width: advancedMode.value ? "10%" : "12%",
      cellProps: { class: "text-body-2 text-textbody2 word-wrap-anywhere" },
    },
    {
      title: "",
      key: "watched",
      width: "5%",
      sortable: false,
    },
  );
  return cols;
});

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
        <template v-if="advancedMode" #[`item.id`]>
          <span data-testid="collection-row-id">{{ rowProps.item.id }}</span>
        </template>
        <template #[`item.name`]>{{ rowProps.item.name }}</template>
        <template #[`item.description`]>
          <span
            v-if="rowProps.item.description"
            class="description-preview"
            :title="rowProps.item.description"
            data-testid="collection-row-description"
          >
            {{ descriptionPreview(rowProps.item.description, DESCRIPTION_PREVIEW_CHARS) }}
          </span>
          <span v-else class="text-textbody2" aria-hidden="true">—</span>
        </template>
        <template #[`item.doCount`]>
          <span class="do-count-cell" data-testid="collection-row-do-count">
            <span class="do-count-number">
              {{ (rowProps.item.dataObjectIds || []).length }}
            </span>
            <v-chip
              v-if="(rowProps.item.dataObjectIds || []).length >= LARGE_COLLECTION_THRESHOLD"
              size="x-small"
              color="primary"
              variant="tonal"
              class="ml-2"
              data-testid="collection-row-large-chip"
            >large</v-chip>
          </span>
        </template>
        <template #[`item.updatedAt`]>
          <span data-testid="collection-row-updated-at">
            {{ toShortDateString(rowProps.item.updatedAt ?? rowProps.item.createdAt) }}
          </span>
        </template>
        <template #[`item.createdBy`]>{{ rowProps.item.createdBy }}</template>
        <template #[`item.watched`]>
          <v-btn
            icon
            variant="text"
            density="compact"
            size="small"
            :color="isWatched(rowProps.item.id!) ? 'primary' : undefined"
            :title="isWatched(rowProps.item.id!) ? 'Remove from watched' : 'Add to watched'"
            @click.stop="toggleWatched(rowProps.item)"
          >
            <v-icon>{{ isWatched(rowProps.item.id!) ? 'mdi-binoculars' : 'mdi-binoculars-outline' }}</v-icon>
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

.description-preview {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
}

.do-count-cell {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  width: 100%;
}

.do-count-number {
  font-variant-numeric: tabular-nums;
}
</style>
