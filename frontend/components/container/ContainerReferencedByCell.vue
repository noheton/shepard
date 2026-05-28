<script setup lang="ts">
/**
 * CC1e — "Referenced by" cell for the /containers list page.
 *
 * Shows a count chip linking to /collections?referencedContainerId=<id>
 * (future deep-link; today it just links to the collections root so the
 * operator can search from there).
 *
 * Renders a loading spinner while the CC1b fetch is in flight, a "—" when
 * the container type has no CC1b endpoint (BASIC, SPATIALDATA), and a
 * clickable chip "N collections" otherwise.
 */
import { useContainerReferencedByCollections } from "~/composables/containers/useContainerReferencedByCollections";

const props = defineProps<{
  containerId: number;
  containerType: string;
}>();

// UI21 — when the fetch resolves, surface the result to the parent so
// the bulk-delete orphan guard can read it without firing a second
// network request. `refs` is null for unsupported types, [] for true
// orphans, or the list of referencing collection IDs.
const emit = defineEmits<{
  (e: "refs-resolved", payload: { id: number; refs: number[] | null }): void;
}>();

const { collectionIds, isLoading } = useContainerReferencedByCollections(
  props.containerId,
  props.containerType,
);

watch(
  collectionIds,
  v => {
    emit("refs-resolved", { id: props.containerId, refs: v });
  },
  { immediate: true },
);

const count = computed(() => collectionIds.value?.length ?? 0);
</script>

<template>
  <!-- Loading state — small inline spinner, does not shift column width -->
  <span v-if="isLoading" class="d-inline-flex align-center">
    <v-progress-circular
      indeterminate
      size="14"
      width="2"
      color="medium-emphasis"
      aria-label="Loading collection count"
    />
  </span>

  <!-- Unsupported type (BASIC, SPATIALDATA) — no CC1b endpoint -->
  <!-- TODO(CC1e-backend): add linked-data-objects endpoints for BASIC and SPATIALDATA
       container types so this "—" placeholder can be replaced with real counts. -->
  <span
    v-else-if="collectionIds === null"
    class="text-medium-emphasis"
    title="Collection reference count unavailable for this container type"
  >—</span>

  <!-- 0 collections -->
  <span
    v-else-if="count === 0"
    class="text-medium-emphasis text-body-2"
  >—</span>

  <!-- N collections -->
  <v-chip
    v-else
    :to="`/collections`"
    size="x-small"
    variant="tonal"
    color="primary"
    :title="`Referenced by ${count} collection${count === 1 ? '' : 's'}`"
  >
    {{ count }} collection{{ count === 1 ? "" : "s" }}
  </v-chip>
</template>
