<script lang="ts" setup>
import { useCollectionWatch } from "~/composables/context/useCollectionWatch";

/**
 * CW1 — Watch/unwatch toggle button for a Collection.
 *
 * Shows a bell icon (mdi-bell when watching, mdi-bell-outline when not).
 * Clicking toggles the watch state via POST/DELETE
 * /v2/collections/{collectionAppId}/watches.
 */
const props = defineProps<{
  collectionAppId: string;
}>();

const appIdRef = computed(() => props.collectionAppId);
const { isWatching, loading, toggle } = useCollectionWatch(appIdRef);
</script>

<template>
  <v-btn
    icon
    variant="text"
    :loading="loading"
    :color="isWatching ? 'primary' : undefined"
    :title="isWatching ? 'Stop watching this collection' : 'Watch this collection for new data objects'"
    @click="toggle"
  >
    <v-icon>{{ isWatching ? 'mdi-bell' : 'mdi-bell-outline' }}</v-icon>
  </v-btn>
</template>
