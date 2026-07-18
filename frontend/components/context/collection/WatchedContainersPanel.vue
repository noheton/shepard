<script setup lang="ts">
import {
  useWatchedContainers,
  type WatchedContainerKind,
} from "~/composables/context/useWatchedContainers";
import { useFetchWatchableContainerOptions } from "~/composables/context/useFetchWatchableContainers";
import {
  iconForContainerType,
  urlSegmentForContainerType,
} from "~/utils/containerTypeRegistry";

const props = defineProps<{
  collectionAppId: string;
  isAllowedToEdit: boolean;
}>();

const collectionAppIdRef = toRef(props, "collectionAppId");
const { watches, loading, mutating, add, remove } =
  useWatchedContainers(collectionAppIdRef);

// The watch-list uses STRUCTURED_DATA (underscore); the registry uses
// STRUCTUREDDATA (canonical `BasicContainer.type` casing). Map at the
// call site rather than mutating either source of truth.
const containerKindIcons: Record<WatchedContainerKind, string> = {
  TIMESERIES: iconForContainerType("TIMESERIES"),
  FILE: iconForContainerType("FILE"),
  STRUCTURED_DATA: iconForContainerType("STRUCTUREDDATA"),
};
const containerKindRoutes: Record<WatchedContainerKind, string> = {
  TIMESERIES: `/containers/${urlSegmentForContainerType("TIMESERIES")}`,
  FILE: `/containers/${urlSegmentForContainerType("FILE")}`,
  STRUCTURED_DATA: `/containers/${urlSegmentForContainerType("STRUCTUREDDATA")}`,
};

// Add-watch form state.
const showAddForm = ref(false);
const draftKind = ref<WatchedContainerKind>("TIMESERIES");
// v-autocomplete with `clearable` sets the model to null on clear, hence
// `string | null`. commitAdd() and the disabled guard both null-check.
const draftAppId = ref<string | null>("");
// UIRULE-NO-MANUAL-IDS — the "advanced: paste appId" escape hatch, kept for ONE
// deprecation window so a container in another collection can still be added
// until the picker covers every reachable container. Picker is the default.
const showAdvancedPaste = ref(false);

// UIRULE-NO-MANUAL-IDS — searchable, permission-filtered container picker over
// GET /v2/containers?kind=…, scoped by the selected draftKind.
const {
  query: containerSearch,
  options: containerOptions,
  isLoading: containerOptionsLoading,
  refresh: refreshContainerOptions,
} = useFetchWatchableContainerOptions(draftKind);

function openAddForm() {
  draftAppId.value = "";
  showAdvancedPaste.value = false;
  showAddForm.value = true;
  refreshContainerOptions();
}

function toggleAdvancedPaste() {
  showAdvancedPaste.value = !showAdvancedPaste.value;
  // Switching modes clears the draft so a stale pick/paste can't leak across.
  draftAppId.value = "";
}

// Changing the kind invalidates any container picked for the previous kind.
watch(draftKind, () => {
  draftAppId.value = "";
});

async function commitAdd() {
  const appId = draftAppId.value?.trim();
  if (!appId) return;
  const ok = await add(draftKind.value, appId);
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
    <div v-if="loading" role="status" class="d-flex align-center ga-2 text-medium-emphasis text-body-2 pa-2">
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
          v-if="w.containerAvailability === 'available'"
          :to="containerKindRoutes[w.containerKind] + w.containerAppId"
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
      <div class="d-flex flex-wrap ga-2 align-start">
        <!-- UIRULE-DROPDOWN-SEARCH-SORT: 3-option type enum kept as v-select;
             items listed in natural (alphabetical) order — no meaningful ladder.
             It scopes the searchable container picker beside it. -->
        <v-select
          v-model="draftKind"
          label="Kind"
          :items="['FILE', 'STRUCTURED_DATA', 'TIMESERIES']"
          density="compact"
          hide-details
          style="max-width: 200px"
        />
        <div style="flex: 1; min-width: 260px">
          <!-- UIRULE-NO-MANUAL-IDS: searchable, permission-filtered container
               picker over GET /v2/containers?kind=… — the user searches by name;
               the appId travels invisibly (item-value). Replaces the raw
               "paste the container appId" text field. -->
          <v-autocomplete
            v-if="!showAdvancedPaste"
            v-model="draftAppId"
            v-model:search="containerSearch"
            :items="containerOptions"
            :loading="containerOptionsLoading"
            item-title="name"
            item-value="appId"
            label="Container"
            density="compact"
            variant="outlined"
            hide-details
            clearable
            auto-select-first
            spellcheck="false"
            placeholder="search a container by name"
            no-data-text="No matching containers you can read"
            data-testid="watch-container-autocomplete"
          />
          <!-- Advanced escape hatch (one deprecation window): paste an appId to
               watch a container the picker doesn't list (e.g. in another
               collection). The picker above is the default per the
               "user never types an ID" rule. -->
          <v-text-field
            v-else
            v-model="draftAppId"
            label="Container appId"
            placeholder="01HX..."
            density="compact"
            variant="outlined"
            hide-details
            clearable
            spellcheck="false"
            data-testid="watch-container-paste"
          />
          <v-btn
            variant="text"
            size="x-small"
            density="compact"
            class="mt-1"
            :prepend-icon="showAdvancedPaste ? 'mdi-chevron-up' : 'mdi-chevron-down'"
            data-testid="watch-advanced-toggle"
            @click="toggleAdvancedPaste"
          >
            {{ showAdvancedPaste ? "Pick from a list instead" : "Advanced: paste an appId" }}
          </v-btn>
        </div>
        <div class="d-flex ga-2 align-center">
          <v-btn variant="text" size="small" @click="showAddForm = false">Cancel</v-btn>
          <v-btn
            variant="flat"
            color="primary"
            size="small"
            :loading="mutating"
            :disabled="!draftAppId || !draftAppId.trim()"
            @click="commitAdd"
          >Add</v-btn>
        </div>
      </div>
    </div>

    <div v-if="isAllowedToEdit && !showAddForm" class="d-flex mt-3">
      <v-btn
        variant="text"
        size="small"
        prepend-icon="mdi-plus-circle"
        color="primary"
        @click="openAddForm"
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
