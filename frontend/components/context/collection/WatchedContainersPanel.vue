<script setup lang="ts">
import {
  useWatchedContainers,
  type WatchedContainerKind,
} from "~/composables/context/useWatchedContainers";

const props = defineProps<{
  collectionAppId: string;
  isAllowedToEdit: boolean;
}>();

const collectionAppIdRef = toRef(props, "collectionAppId");
const { watches, loading, mutating, add, remove } =
  useWatchedContainers(collectionAppIdRef);

const containerKindIcons: Record<WatchedContainerKind, string> = {
  TIMESERIES: "mdi-chart-line",
  FILE: "mdi-file-outline",
  STRUCTURED_DATA: "mdi-code-json",
};
const containerKindRoutes: Record<WatchedContainerKind, string> = {
  TIMESERIES: "/containers/timeseries/",
  FILE: "/containers/files/",
  STRUCTURED_DATA: "/containers/structureddata/",
};

// Add-watch form state.
const showAddForm = ref(false);
const draftKind = ref<WatchedContainerKind>("TIMESERIES");
const draftAppId = ref("");

async function commitAdd() {
  if (!draftAppId.value.trim()) return;
  const ok = await add(draftKind.value, draftAppId.value.trim());
  if (ok) {
    draftAppId.value = "";
    showAddForm.value = false;
  }
}

function availabilityChipColor(a?: string): string | undefined {
  if (a === "available") return "success";
  if (a === "forbidden") return "warning";
  if (a === "deleted") return "error";
  return undefined;
}
function availabilityChipLabel(a?: string): string {
  if (a === "available") return "available";
  if (a === "forbidden") return "no access";
  if (a === "deleted") return "deleted";
  if (a === "error") return "error";
  return "unknown";
}
</script>

<template>
  <div>
    <div v-if="loading" class="d-flex align-center ga-2 text-medium-emphasis text-body-2 pa-2">
      <v-progress-circular indeterminate size="14" width="2" />
      Loading watched containers…
    </div>
    <div
      v-else-if="!watches.length && !showAddForm"
      class="text-medium-emphasis text-body-2 pa-2"
    >
      No containers watched yet. Watched containers show live data on this
      collection page without needing a DataObject reference.
    </div>

    <div v-else class="d-flex flex-column ga-2">
      <div
        v-for="w in watches"
        :key="w.watchAppId"
        class="watch-row d-flex align-center ga-2 pa-2"
      >
        <v-icon :icon="containerKindIcons[w.containerKind]" size="20" />
        <div class="d-flex flex-column" style="min-width: 0; flex: 1">
          <div class="text-body-2 text-truncate">
            {{ w.containerName ?? w.containerAppId }}
          </div>
          <div class="text-caption text-medium-emphasis">
            <span>{{ w.containerKind.toLowerCase().replace("_", "-") }}</span>
            <span v-if="w.addedBy"> · added by {{ w.addedBy }}</span>
          </div>
        </div>
        <v-chip
          v-if="w.containerAvailability && w.containerAvailability !== 'available'"
          size="x-small"
          variant="tonal"
          :color="availabilityChipColor(w.containerAvailability)"
        >
          {{ availabilityChipLabel(w.containerAvailability) }}
        </v-chip>
        <v-btn
          v-if="w.containerAvailability === 'available' && w.containerOgmId != null"
          :to="containerKindRoutes[w.containerKind] + w.containerOgmId"
          variant="text"
          size="x-small"
          icon="mdi-arrow-right"
          title="Open container"
        />
        <v-btn
          v-if="isAllowedToEdit"
          variant="text"
          size="x-small"
          icon="mdi-close"
          :disabled="mutating"
          title="Remove watch"
          @click="remove(w.watchAppId)"
        />
      </div>
    </div>

    <div v-if="showAddForm" class="add-form pa-3 mt-2">
      <div class="text-caption text-medium-emphasis mb-2">
        Add a container to this collection's watchlist. You need Read on the target container.
      </div>
      <div class="d-flex flex-wrap ga-2 align-end">
        <v-select
          v-model="draftKind"
          label="Kind"
          :items="['TIMESERIES', 'FILE', 'STRUCTURED_DATA']"
          density="compact"
          hide-details
          style="max-width: 200px"
        />
        <v-text-field
          v-model="draftAppId"
          label="Container appId"
          placeholder="01HX..."
          density="compact"
          hide-details
          style="flex: 1; min-width: 240px"
        />
        <v-btn variant="text" size="small" @click="showAddForm = false">Cancel</v-btn>
        <v-btn
          variant="flat"
          color="primary"
          size="small"
          :loading="mutating"
          :disabled="!draftAppId.trim()"
          @click="commitAdd"
        >Add</v-btn>
      </div>
    </div>

    <div v-if="isAllowedToEdit && !showAddForm" class="d-flex mt-3">
      <v-btn
        variant="text"
        size="small"
        prepend-icon="mdi-plus-circle"
        color="primary"
        @click="showAddForm = true"
      >Add watch</v-btn>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.watch-row {
  border: 1px solid rgba(var(--v-border-color), var(--v-border-opacity));
  border-radius: 4px;
}
.add-form {
  background: rgba(var(--v-border-color), 0.05);
  border-left: 3px solid rgb(var(--v-theme-primary));
  border-radius: 4px;
}
</style>
