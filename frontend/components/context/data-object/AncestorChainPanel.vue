<script lang="ts" setup>
/**
 * AncestorChainPanel — UX-PROV1
 *
 * Collapsible panel showing the upstream predecessor chain for a DataObject
 * as a vertical timeline. Each row is one ancestor; the list is ordered from
 * nearest predecessor (top) back to the root (bottom).
 *
 * Backend: GET /v2/collections/{collectionAppId}/data-objects/{dataObjectAppId}
 *   /predecessor-chain?depth=N  (ANC-1 endpoint, reused verbatim).
 *
 * Shown only in advanced mode (advanced is a strict superset of basic).
 * Gated externally in the detail page via `v-if="advancedMode"`.
 */
import { useFetchPredecessorChain } from "~/composables/context/useFetchPredecessorChain";
import { collectionsPath, dataObjectsPathFragment } from "~/utils/constants";

const props = defineProps<{
  /** UUID v7 of the parent Collection (needed to resolve v1 numeric IDs for NuxtLink). */
  collectionId: number;
  /** UUID v7 of the Collection (needed for the predecessor-chain API call). */
  collectionAppId: string;
  /** UUID v7 of the DataObject whose ancestor chain we display. */
  dataObjectAppId: string;
}>();

const { chain, isLoading, hasError } = useFetchPredecessorChain(
  computed(() => props.collectionAppId),
  computed(() => props.dataObjectAppId),
  10,
);
</script>

<template>
  <div class="ancestor-chain-panel">
    <!-- Loading -->
    <v-progress-linear
      v-if="isLoading"
      indeterminate
      aria-label="Loading ancestor chain"
    />

    <!-- Error -->
    <v-alert
      v-else-if="hasError"
      type="warning"
      density="compact"
      variant="tonal"
      class="my-2"
      text="Could not load ancestor chain — the endpoint may be unavailable."
    />

    <!-- Empty state -->
    <EmptyListIcon
      v-else-if="chain.length === 0"
      label="No predecessor chain"
      hint="This DataObject has no recorded predecessors."
    />

    <!-- Timeline -->
    <v-timeline
      v-else
      side="end"
      density="compact"
      class="py-2"
      data-testid="ancestor-chain-timeline"
    >
      <v-timeline-item
        v-for="(item, index) in chain"
        :key="item.appId"
        :dot-color="index === 0 ? 'primary' : 'grey-lighten-1'"
        size="x-small"
      >
        <div class="d-flex align-center flex-wrap ga-2">
          <NuxtLink
            :to="`${collectionsPath}${collectionId}${dataObjectsPathFragment}${item.id}`"
            class="text-body-2 font-weight-medium text-decoration-none ancestor-link"
          >
            {{ item.name }}
          </NuxtLink>
          <StatusChip
            v-if="item.status"
            :status="item.status"
          />
          <span class="text-caption text-medium-emphasis font-weight-light">
            ({{ item.appId.slice(0, 8) }}&hellip;)
          </span>
        </div>
      </v-timeline-item>
    </v-timeline>
  </div>
</template>

<style scoped>
.ancestor-chain-panel {
  min-height: 48px;
}

.ancestor-link {
  color: inherit;
}

.ancestor-link:hover {
  text-decoration: underline !important;
}
</style>
