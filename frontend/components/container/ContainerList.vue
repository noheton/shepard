<script lang="ts" setup>
import {
  BasicContainerAttributes,
  type BasicContainer,
} from "@dlr-shepard/backend-client";
import { containerTypeUrlPathSegmentMappings } from "~/utils/containerPathMappings";
import {
  ContainerTypeName,
  type ContainerFilterType,
} from "./containerTypeFilter";
import { useContainerListQueryParams } from "./useContainerListQueryParams";
import type { ContainerCardinalitySummary } from "~/composables/containers/useContainerCardinalitySummary";

// UI21-SIZEBAR-DATA: import the summary fetcher.
import { useContainerCardinalitySummary } from "~/composables/containers/useContainerCardinalitySummary";

const props = defineProps<{
  itemsPerPage: number;
  serverItems: BasicContainer[];
  loading: boolean;
  pageCount: number;
}>();

const router = useRouter();
const { queryParams } = useContainerListQueryParams();

// UI21-SIZEBAR-DATA: reactive map from container id → fetched cardinality.
// Populated lazily as each row's composable resolves.
const cardinalityMap = ref<Map<number, number>>(new Map());

/** Maximum cardinality in the current page — used to scale all sizebars. */
const maxCardinality = computed(() => {
  let max = 0;
  for (const v of cardinalityMap.value.values()) {
    if (v > max) max = v;
  }
  return max;
});

/**
 * UI21-SIZEBAR-DATA: When the page of containers changes, reset the
 * cardinality map and kick off a summary fetch for each row.
 * Fire-and-forget: failures are swallowed inside the composable.
 */
function fetchSummariesForPage(items: BasicContainer[]) {
  cardinalityMap.value = new Map();
  for (const item of items) {
    const { summary } = useContainerCardinalitySummary(item.id, item.type);
    watch(
      summary,
      (val: ContainerCardinalitySummary | null) => {
        if (val != null) {
          const next = new Map(cardinalityMap.value);
          next.set(item.id, val.cardinality);
          cardinalityMap.value = next;
        }
      },
      { immediate: true },
    );
  }
}

watch(() => props.serverItems, fetchSummariesForPage, { immediate: true });

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
    key: BasicContainerAttributes.Name,
    width: "28%",
    cellProps: {
      class: "text-subtitle-2 word-wrap-anywhere",
    },
  },
  {
    title: "Container Type",
    key: BasicContainerAttributes.Type,
    width: "16%",
    cellProps: {
      class: "text-body-1 word-wrap-anywhere",
    },
  },
  {
    title: "Contents",
    key: "cardinality",
    sortable: false,
    width: "18%",
  },
  {
    title: "Created by",
    key: BasicContainerAttributes.CreatedBy,
    width: "16%",
    cellProps: {
      class: "text-body-1 word-wrap-anywhere",
    },
  },
  {
    title: "Created at",
    key: BasicContainerAttributes.CreatedAt,
    width: "14%",
    cellProps: {
      class: "text-body-1 word-wrap-anywhere",
    },
  },
];

function onSortBy(args: { key: string; order: "asc" | "desc" }[]) {
  if (args[0]) {
    router.push({
      path: containersPath,
      query: {
        ...router.currentRoute.value.query,
        page: 1,
        sortBy: JSON.stringify(args[0]),
      },
    });
    return;
  }
  router.push({
    path: containersPath,
    query: {
      ...router.currentRoute.value.query,
      page: 1,
      sortBy: undefined,
    },
  });
}

function onPageChange(page: number) {
  router.push({
    path: containersPath,
    query: { ...router.currentRoute.value.query, page },
  });
}
</script>

<template>
  <DataTable
    :cell-props="{
      class: 'text-textbody1',
    }"
    :header-props="{
      class: 'text-subtitle-2 text-textbody1',
    }"
    :headers="headers"
    :items="serverItems"
    :items-per-page="itemsPerPage"
    :loading="loading"
    :sort-by="queryParams.sortBy ? [queryParams.sortBy] : []"
    @update:sort-by="onSortBy"
  >
    <template #item="rowProps">
      <v-data-table-row
        v-bind="rowProps"
        @click="
          router.push(
            containersPath +
              containerTypeUrlPathSegmentMappings[
                rowProps.item.type as ContainerFilterType
              ] +
              rowProps.item.id,
          )
        "
      >
        <template #[`item.id`]>
          {{ rowProps.item.id }}
        </template>
        <template #[`item.name`]>
          {{ rowProps.item.name }}
        </template>
        <template #[`item.type`]>
          {{ ContainerTypeName[rowProps.item.type as ContainerFilterType] }}
        </template>
        <!-- UI21-SIZEBAR-DATA: per-kind cardinality sizebar -->
        <template #[`item.cardinality`]>
          <ContainerSizeBar
            :container-id="rowProps.item.id"
            :container-type="rowProps.item.type"
            :max-cardinality="maxCardinality"
          />
        </template>
        <template #[`item.createdBy`]>{{ rowProps.item.createdBy }}</template>
        <template #[`item.createdAt`]>
          {{ toShortDateString(rowProps.item.createdAt) }}
        </template>
      </v-data-table-row>
    </template>
    <template #bottom>
      <v-divider :thickness="8" color="divider2" opacity="1" />
      <v-pagination
        :length="pageCount"
        :model-value="queryParams.page ?? 1"
        :total-visible="6"
        @update:model-value="onPageChange"
      />
    </template>
  </DataTable>
</template>

<style lang="scss" scoped>
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
