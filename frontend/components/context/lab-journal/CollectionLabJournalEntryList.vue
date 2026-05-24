<script lang="ts" setup>
import {
  CollectionApi,
  type LabJournalEntry,
  type Roles,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "~/composables/common/api/useShepardApi";
import { useFetchCollectionLabJournalEntries } from "~/composables/context/useFetchCollectionLabJournalEntries";
import LabJournalExistingEntry from "~/components/context/lab-journal/LabJournalExistingEntry.vue";

interface CollectionLabJournalEntryListProps {
  collectionId: number;
  collectionAppId: string | null;
  dataObjectMap: Map<number, string>;
}

const props = defineProps<CollectionLabJournalEntryListProps>();
const emit = defineEmits(["numberOfEntriesChanged"]);

// UI-020 — bulk fetch in a single round-trip via
// GET /v2/collections/{collectionAppId}/lab-journal-entries instead of one
// GET /shepard/api/labJournalEntries?dataObjectId=N per DataObject. The old
// fan-out collapsed at MFFD-Dropbox scale (8500 DOs → 8500 concurrent
// requests → browser socket exhaustion + thousands of console errors).
const collectionAppIdRef = computed(() => props.collectionAppId);
const { entries: fetched } = useFetchCollectionLabJournalEntries(collectionAppIdRef);

// Local mutable copy the template renders. Kept separate so onLabJournalDeleted
// can splice without re-fetching, mirroring pre-UI-020 behaviour.
const entries = ref<LabJournalEntry[] | undefined>(undefined);
const userRoles = ref<Roles | undefined>(undefined);

watch(fetched, value => {
  if (value === undefined) {
    entries.value = undefined;
    return;
  }
  entries.value = [...value].sort(
    (a, b) => b.createdAt.getTime() - a.createdAt.getTime(),
  );
  emit("numberOfEntriesChanged", entries.value.length);
});

async function fetchRoles() {
  useShepardApi(CollectionApi)
    .value.getCollectionRoles({ collectionId: props.collectionId })
    .then(response => {
      userRoles.value = response;
    })
    .catch(error => {
      handleError(error, "getCollectionRoles");
    });
}

async function onLabJournalDeleted(deletedLabjournalIndex: number) {
  if (entries.value) {
    entries.value.splice(deletedLabjournalIndex, 1);
    emit("numberOfEntriesChanged", entries.value.length);
  }
}

// Resolve a display name for a DataObject id. The bulk endpoint may surface
// entries from DataObjects the parent map did not pre-fetch (e.g. paginated
// listings); fall back to the numeric id so the row still renders without
// crashing on a missing map entry.
function dataObjectName(dataObjectId: number): string {
  return props.dataObjectMap.get(dataObjectId) ?? `#${dataObjectId}`;
}

fetchRoles();
</script>

<template>
  <div v-if="entries != undefined">
    <LabJournalExistingEntry
      v-for="(entry, index) in entries"
      :key="'lab-journal-' + entry.id"
      :collection-id="collectionId"
      :data-object-id="entry.dataObjectId"
      :data-object-name="dataObjectName(entry.dataObjectId)"
      :lab-journal="entry"
      :user-roles="userRoles"
      @deleted="onLabJournalDeleted(index)"
    />
    <EmptyListIcon
      v-if="entries.length === 0 || entries === undefined"
      label="No entry yet"
    />
  </div>
  <CenteredLoadingSpinner v-else />
</template>
