<script lang="ts" setup>
/**
 * UI21 — Container list table with per-kind icons, column filters,
 * sizebar (relative-scale) column, multi-select bulk delete with
 * orphan guard, and per-row "open in detail" affordance.
 *
 * Search-as-you-type is handled by the parent (ContainerListPage.vue)
 * via the existing v2 SearchApi call in useSearchContainers; this
 * component only renders.
 */
import {
  BasicContainerAttributes,
  type BasicContainer,
} from "@dlr-shepard/backend-client";
import {
  ContainerTypeName,
  type ContainerFilterType,
} from "./containerTypeFilter";
import { useContainerListQueryParams } from "./useContainerListQueryParams";
import {
  describeContainerType,
  supportsReferenceCheck,
} from "~/utils/containerTypeRegistry";
import { partitionOrphans, sizeBarFraction } from "~/utils/containerListPage";

const props = defineProps<{
  itemsPerPage: number;
  serverItems: BasicContainer[];
  loading: boolean;
  pageCount: number;
}>();

const emit = defineEmits<{
  (e: "request-bulk-delete", payload: {
    deletable: BasicContainer[];
    blockedByReferences: BasicContainer[];
    unknownReferenceState: BasicContainer[];
  }): void;
  (e: "refs-resolved", payload: { id: number; refs: number[] | null }): void;
}>();

const router = useRouter();
const { queryParams } = useContainerListQueryParams();

// ── Column filters (c) ──────────────────────────────────────────────────────
// Per-column free-text refinement; applied client-side over the current
// page's items only. Each chip toggles a small inline text field.
const columnFilters = reactive<Record<string, string>>({
  name: "",
  type: "",
  createdBy: "",
  createdAt: "",
});

const filteredItems = computed<BasicContainer[]>(() =>
  props.serverItems.filter(c => {
    if (columnFilters.name && !c.name.toLowerCase().includes(columnFilters.name.toLowerCase())) return false;
    if (columnFilters.type && !c.type.toLowerCase().includes(columnFilters.type.toLowerCase())) return false;
    if (columnFilters.createdBy && !c.createdBy.toLowerCase().includes(columnFilters.createdBy.toLowerCase())) return false;
    if (columnFilters.createdAt) {
      const iso = new Date(c.createdAt).toISOString().toLowerCase();
      if (!iso.includes(columnFilters.createdAt.toLowerCase())) return false;
    }
    return true;
  }),
);

// ── Multi-select (e) ────────────────────────────────────────────────────────
const selectedIds = ref<number[]>([]);

/**
 * Per-row reference-collection state, populated by the
 * `refs-resolved` event each <ContainerReferencedByCell> emits when
 * its lazy CC1b fetch settles. The bulk-delete partition reads from
 * this map. null = unknown (no CC1b endpoint); [] = orphan; [...]
 * = referenced.
 *
 * Single source of truth for the network call: the cell fires the
 * fetch once; we just observe.
 */
const refsById = reactive(new Map<number, number[] | null>());

watchEffect(() => {
  // When the server items change (new page / new search), drop stale
  // entries so we don't leak deleted ids into the guard.
  const currentIds = new Set(props.serverItems.map(c => c.id));
  for (const id of refsById.keys()) {
    if (!currentIds.has(id)) refsById.delete(id);
  }
  // Seed unsupported types deterministically so partitionOrphans
  // classifies them correctly before any fetch completes.
  for (const c of props.serverItems) {
    if (!supportsReferenceCheck(c.type) && !refsById.has(c.id)) {
      refsById.set(c.id, null);
    }
  }
});

function onRefsResolved(payload: { id: string | number; refs: number[] | null }) {
  const numericId = Number(payload.id);
  if (!Number.isNaN(numericId)) refsById.set(numericId, payload.refs);
  emit("refs-resolved", { id: numericId, refs: payload.refs });
}

// ── Sizebar column (d) ──────────────────────────────────────────────────────
// Relative-scale indicator. Baseline is the max reference count across
// the current page; rows whose count we don't know yet render a
// neutral bar. (Real channel/file counts would need a per-row CC1b-
// style lazy lookup — same pattern, different endpoint — and are
// queued as a follow-up under UI21-SIZEBAR-DATA.)
const maxRefCount = computed(() => {
  let max = 0;
  for (const c of props.serverItems) {
    const refs = refsById.get(c.id);
    if (refs && refs.length > max) max = refs.length;
  }
  return Math.max(max, 1);
});

function sizeBarFor(item: BasicContainer): number {
  const refs = refsById.get(item.id);
  if (!refs) return 0;
  return sizeBarFraction(refs.length, maxRefCount.value);
}

// ── Table headers ───────────────────────────────────────────────────────────

const headers = [
  {
    title: "",
    key: "kind-icon",
    width: "44px",
    sortable: false,
    cellProps: { class: "text-textbody1" },
  },
  {
    title: "ID",
    key: "id",
    width: "7%",
    cellProps: { class: "text-body-1" },
  },
  {
    title: "Name",
    key: BasicContainerAttributes.Name,
    width: "22%",
    cellProps: { class: "text-subtitle-2 word-wrap-anywhere" },
  },
  {
    title: "Container Type",
    key: BasicContainerAttributes.Type,
    width: "14%",
    cellProps: { class: "text-body-1 word-wrap-anywhere" },
  },
  {
    title: "Created by",
    key: BasicContainerAttributes.CreatedBy,
    width: "13%",
    cellProps: { class: "text-body-1 word-wrap-anywhere" },
  },
  {
    title: "Created at",
    key: BasicContainerAttributes.CreatedAt,
    width: "12%",
    cellProps: { class: "text-body-1 word-wrap-anywhere" },
  },
  {
    title: "Scale",
    key: "sizebar",
    width: "8%",
    sortable: false,
    cellProps: { class: "text-body-2" },
  },
  {
    // CC1e — linked-from breadcrumb: how many Collections reference this container.
    title: "Referenced by",
    key: "referencedBy",
    width: "12%",
    sortable: false,
    cellProps: { class: "text-body-1" },
  },
  {
    title: "",
    key: "actions",
    width: "60px",
    sortable: false,
    cellProps: { class: "text-body-1" },
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

function detailLinkFor(item: BasicContainer): string {
  // V1-EXCEPTION (V2-LINKS / CONTAINER-V2-ROUTE in aidocs/16): container detail
  // pages fetch via the v1-generated `getXContainer({ containerId })`, whose
  // path resolves ONLY the numeric Neo4j id (the frozen v1 container GET 404s
  // on an appId). Keep numeric until the container accessors move to a v2
  // appId-keyed GET.
  return containersPath + describeContainerType(item.type).urlSegment + item.id;
}

function onRequestBulkDelete() {
  if (selectedIds.value.length === 0) return;
  const partition = partitionOrphans(selectedIds.value, refsById);
  const byId = new Map(props.serverItems.map(c => [c.id, c]));
  emit("request-bulk-delete", {
    deletable: partition.deletable.flatMap(id => {
      const c = byId.get(id);
      return c ? [c] : [];
    }),
    blockedByReferences: partition.blockedByReferences.flatMap(id => {
      const c = byId.get(id);
      return c ? [c] : [];
    }),
    unknownReferenceState: partition.unknownReferenceState.flatMap(id => {
      const c = byId.get(id);
      return c ? [c] : [];
    }),
  });
}

defineExpose({ clearSelection: () => { selectedIds.value = []; } });
</script>

<template>
  <div>
    <!-- Bulk-delete affordance: appears when ≥1 row is selected. -->
    <div
      v-if="selectedIds.length > 0"
      class="d-flex align-center ga-3 pa-3 mb-2 selection-bar"
    >
      <span class="text-body-2">
        {{ selectedIds.length }} container{{ selectedIds.length === 1 ? "" : "s" }} selected
      </span>
      <v-spacer />
      <v-btn
        color="error"
        variant="flat"
        size="small"
        prepend-icon="mdi-delete-outline"
        @click="onRequestBulkDelete"
      >
        Delete selected
      </v-btn>
      <v-btn
        variant="text"
        size="small"
        @click="selectedIds = []"
      >
        Clear
      </v-btn>
    </div>

    <!-- Per-column filter row (c). Stacks under the column header. -->
    <div class="column-filter-row d-flex align-center ga-2 pa-2">
      <v-text-field
        v-model="columnFilters.name"
        density="compact"
        variant="outlined"
        hide-details
        clearable
        placeholder="Filter name…"
        prepend-inner-icon="mdi-filter-variant"
        class="filter-input"
      />
      <v-text-field
        v-model="columnFilters.type"
        density="compact"
        variant="outlined"
        hide-details
        clearable
        placeholder="Filter type…"
        prepend-inner-icon="mdi-filter-variant"
        class="filter-input"
      />
      <v-text-field
        v-model="columnFilters.createdBy"
        density="compact"
        variant="outlined"
        hide-details
        clearable
        placeholder="Filter owner…"
        prepend-inner-icon="mdi-filter-variant"
        class="filter-input"
      />
      <v-text-field
        v-model="columnFilters.createdAt"
        density="compact"
        variant="outlined"
        hide-details
        clearable
        placeholder="Filter date (YYYY-MM)…"
        prepend-inner-icon="mdi-filter-variant"
        class="filter-input"
      />
    </div>

    <v-data-table
      v-model="selectedIds"
      :cell-props="{ class: 'text-textbody1' }"
      :header-props="{ class: 'text-subtitle-2 text-textbody1' }"
      :headers="headers"
      :items="filteredItems"
      :items-per-page="itemsPerPage"
      :loading="loading"
      :sort-by="queryParams.sortBy ? [queryParams.sortBy] : []"
      sort-desc-icon="mdi-triangle-small-down"
      sort-asc-icon="mdi-triangle-small-up"
      show-select
      item-value="id"
      class="container-data-table"
      @update:sort-by="onSortBy"
    >
      <template #item="rowProps">
        <v-data-table-row v-bind="(rowProps as any)">
          <template #[`item.kind-icon`]>
            <v-icon
              :icon="describeContainerType(rowProps.item.type).icon"
              :color="describeContainerType(rowProps.item.type).color"
              size="20"
              :aria-label="describeContainerType(rowProps.item.type).label + ' container'"
            />
          </template>
          <template #[`item.id`]>
            <NuxtLink
              :to="detailLinkFor(rowProps.item)"
              style="display: block; color: inherit; text-decoration: none;"
            >{{ rowProps.item.id }}</NuxtLink>
          </template>
          <template #[`item.name`]>
            <NuxtLink
              :to="detailLinkFor(rowProps.item)"
              style="display: block; color: inherit; text-decoration: none;"
            >{{ rowProps.item.name }}</NuxtLink>
          </template>
          <template #[`item.type`]>
            <NuxtLink
              :to="detailLinkFor(rowProps.item)"
              style="display: block; color: inherit; text-decoration: none;"
            >{{ ContainerTypeName[rowProps.item.type as ContainerFilterType] ?? describeContainerType(rowProps.item.type).label }}</NuxtLink>
          </template>
          <template #[`item.createdBy`]>
            <NuxtLink
              :to="detailLinkFor(rowProps.item)"
              style="display: block; color: inherit; text-decoration: none;"
            >{{ rowProps.item.createdBy }}</NuxtLink>
          </template>
          <template #[`item.createdAt`]>
            <NuxtLink
              :to="detailLinkFor(rowProps.item)"
              style="display: block; color: inherit; text-decoration: none;"
            >{{ toShortDateString(rowProps.item.createdAt) }}</NuxtLink>
          </template>
          <template #[`item.sizebar`]>
            <div
              class="size-bar"
              :title="`Relative reference count (max on this page = ${maxRefCount})`"
              :aria-label="`Reference scale ${Math.round(sizeBarFor(rowProps.item) * 100)} percent`"
            >
              <div
                class="size-bar-fill"
                :style="{
                  width: `${Math.max(2, sizeBarFor(rowProps.item) * 100)}%`,
                  background: describeContainerType(rowProps.item.type).color
                    ? `rgb(var(--v-theme-${describeContainerType(rowProps.item.type).color}))`
                    : 'rgb(var(--v-theme-primary))',
                }"
              />
            </div>
          </template>
          <!-- CC1e: referenced-by collection count — lazy fetch per row.
               UI21: cell emits its result up so the bulk-delete
               partition can read it without a second fetch. -->
          <template #[`item.referencedBy`]>
            <ContainerReferencedByCell
              :container-id="(rowProps.item as any).appId ?? rowProps.item.id"
              :container-type="rowProps.item.type"
              @refs-resolved="onRefsResolved"
            />
          </template>
          <template #[`item.actions`]>
            <v-btn
              :to="detailLinkFor(rowProps.item)"
              variant="text"
              size="small"
              icon="mdi-arrow-right"
              :title="`Open ${rowProps.item.name}`"
              :aria-label="`Open container ${rowProps.item.name}`"
            />
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
    </v-data-table>
  </div>
</template>

<style lang="scss" scoped>
.container-data-table {
  background-color: unset;

  :deep(thead) > tr > th {
    background-color: rgb(var(--v-theme-divider2)) !important;
  }

  :deep(td) {
    padding: 8px 24px !important;
  }

  :deep(tr):hover {
    background-color: rgb(var(--v-theme-focus1));
  }

  :deep(th) {
    font-size: 16px;
    padding: 8px 24px !important;
  }

  :deep(.mdi) {
    margin-left: 0.2em;
  }

  :deep(.word-wrap-anywhere) {
    word-wrap: anywhere;
  }
}

.selection-bar {
  background: rgba(var(--v-border-color), 0.05);
  border: 1px solid rgba(var(--v-border-color), var(--v-border-opacity));
  border-radius: 4px;
}

.column-filter-row {
  background: rgba(var(--v-border-color), 0.025);
  border-bottom: 1px solid rgba(var(--v-border-color), var(--v-border-opacity));
}

.filter-input {
  flex: 1;
  min-width: 140px;
  max-width: 240px;
}

.size-bar {
  position: relative;
  width: 100%;
  max-width: 80px;
  height: 6px;
  background: rgba(var(--v-border-color), 0.15);
  border-radius: 3px;
  overflow: hidden;
}

.size-bar-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 200ms ease-out;
}
</style>
