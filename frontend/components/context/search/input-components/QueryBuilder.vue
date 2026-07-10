<script lang="ts" setup>
/**
 * QueryBuilder — UI-SEARCH-JSON-QUERY-001
 *
 * Property + operator + value rows that serialize to the backend's
 * existing JSON query shape. Pairs with `utils/searchQueryBuilder.ts`
 * for the actual serialization; this component is the dumb form.
 *
 * Emits `update:jsonQuery` whenever any row or the combine mode
 * changes. The parent (search/index.vue) treats the resulting JSON
 * as if the user had typed it into the legacy Code-view textarea.
 */

import {
  SEARCH_PROPERTIES,
  operatorsForProperty,
  serializeQuery,
  type CombineMode,
  type QueryFilter,
  type SearchProperty,
} from "~/utils/searchQueryBuilder";
import { naturalSort } from "~/utils/naturalSort";

const jsonQuery = defineModel<string>("jsonQuery", { required: true });

const filters = ref<QueryFilter[]>([
  { property: "name", operator: "contains", value: "" },
]);
const combine = ref<CombineMode>("AND");

// UIRULE-DROPDOWN-SEARCH-SORT: property names in natural order.
const propertyOptions = naturalSort(
  SEARCH_PROPERTIES.map(p => ({
    title: p.label,
    value: p.value,
  })),
  o => o.title,
);

function operatorOptions(prop: SearchProperty) {
  return operatorsForProperty(prop).map(o => ({ title: o.label, value: o.value }));
}

/** Date-typed input when the property is a timestamp. */
function inputTypeFor(prop: SearchProperty): "text" | "date" {
  const def = SEARCH_PROPERTIES.find(p => p.value === prop);
  return def?.kind === "date" ? "date" : "text";
}

/** Ensure the chosen operator remains valid after switching property. */
function onPropertyChange(idx: number) {
  const f = filters.value[idx];
  if (!f) return;
  const allowed = operatorsForProperty(f.property).map(o => o.value);
  if (!allowed.includes(f.operator)) {
    f.operator = allowed[0] ?? "contains";
  }
}

function addFilter() {
  filters.value.push({ property: "name", operator: "contains", value: "" });
}

function removeFilter(idx: number) {
  filters.value.splice(idx, 1);
  if (filters.value.length === 0) addFilter();
}

watch(
  [filters, combine],
  () => {
    jsonQuery.value = serializeQuery(filters.value, combine.value);
  },
  { deep: true, immediate: true },
);
</script>

<template>
  <div class="d-flex flex-column ga-3">
    <div
      v-for="(f, idx) in filters"
      :key="idx"
      class="d-flex flex-wrap ga-2 align-center"
    >
      <!-- UIRULE-DROPDOWN-SEARCH-SORT: property list — searchable + natural order. -->
      <v-autocomplete
        v-model="f.property"
        :items="propertyOptions"
        auto-select-first
        density="compact"
        variant="outlined"
        label="Property"
        hide-details
        style="min-width: 200px"
        @update:model-value="onPropertyChange(idx)"
      />
      <!-- UIRULE-DROPDOWN-SEARCH-SORT: a string property exposes up to 8 operators
           → searchable (v-autocomplete); the operator order (contains/equals/…/
           comparisons) is meaningful, so it is NOT natural-sorted. -->
      <v-autocomplete
        v-model="f.operator"
        :items="operatorOptions(f.property)"
        auto-select-first
        density="compact"
        variant="outlined"
        label="Operator"
        hide-details
        style="min-width: 180px"
      />
      <v-text-field
        v-model="f.value"
        :type="inputTypeFor(f.property)"
        density="compact"
        variant="outlined"
        label="Value"
        hide-details
        style="min-width: 220px"
        class="flex-grow-1"
      />
      <v-btn
        v-if="filters.length > 1"
        icon="mdi-close"
        size="small"
        variant="text"
        aria-label="Remove filter"
        @click="removeFilter(idx)"
      />
    </div>
    <div class="d-flex flex-wrap ga-3 align-center mt-1">
      <v-btn
        prepend-icon="mdi-plus"
        size="small"
        variant="tonal"
        @click="addFilter"
      >
        Add filter
      </v-btn>
      <v-btn-toggle
        v-if="filters.length > 1"
        v-model="combine"
        mandatory
        density="compact"
        variant="tonal"
      >
        <v-btn :value="'AND'" size="small">match ALL (AND)</v-btn>
        <v-btn :value="'OR'" size="small">match ANY (OR)</v-btn>
      </v-btn-toggle>
    </div>
    <v-card variant="outlined" density="compact">
      <v-card-title class="text-caption text-medium-emphasis py-1 px-3">
        JSON preview
      </v-card-title>
      <pre class="text-caption pa-3 ma-0 json-preview">{{ jsonQuery }}</pre>
    </v-card>
  </div>
</template>

<style scoped>
.json-preview {
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 180px;
  overflow: auto;
}
</style>
