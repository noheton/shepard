<script lang="ts" setup>
import type { LabJournalEntry } from "@dlr-shepard/backend-client";
import { useFetchCollectionLabJournalEntries } from "~/composables/context/useFetchCollectionLabJournalEntries";

// UI-DO-LABJOURNAL-V2: migrated from v1 numeric-id props to v2 appId-keyed props.
// The v2 CollectionLabJournalEntriesApi fetches all entries for the collection and
// we filter client-side by dataObjectAppId, so the panel works for appId-only
// DataObjects (post-reset) that have no numeric Neo4j id on the frontend.
interface DataObjectLabJournalEntryListProps {
  collectionAppId: string;
  dataObjectAppId: string;
  // v1 compat: required only for LabJournalNewEntry.createLabJournal (still v1).
  // Absent = create form hidden (appId-only DOs cannot create via v1 API without numeric id).
  collectionNumericId?: number;
  dataObjectNumericId?: number;
}

const props = defineProps<DataObjectLabJournalEntryListProps>();
const emit = defineEmits(["numberOfEntriesChanged"]);

// Local optimistic state: seeded from the v2 bulk-fetch composable, then mutated
// on create/delete so the UI stays responsive without a full refetch.
const localEntries = ref<LabJournalEntry[] | undefined>(undefined);

const collectionAppIdRef = computed(() => props.collectionAppId);
const { entries: collectionEntries, isLoading } =
  useFetchCollectionLabJournalEntries(collectionAppIdRef);

// Filter the collection-wide entries down to this DataObject's entries.
// Prefer dataObjectAppId match; fall back to numeric dataObjectId when appId absent.
watch(
  collectionEntries,
  all => {
    if (all === undefined) return;
    localEntries.value = all.filter(e => {
      if (e.dataObjectAppId) return e.dataObjectAppId === props.dataObjectAppId;
      // Numeric fallback for pre-L2a entries that lack a dataObjectAppId.
      return (
        props.dataObjectNumericId !== undefined &&
        e.dataObjectId === props.dataObjectNumericId
      );
    });
    emit("numberOfEntriesChanged", localEntries.value.length);
  },
  { immediate: true },
);

// Create is only available when the numeric id is in hand (v1 create API).
function canCreate() {
  return (
    props.dataObjectNumericId !== undefined &&
    props.collectionNumericId !== undefined
  );
}

function appendNewLabJournalEntry(newEntry: LabJournalEntry) {
  if (localEntries.value) {
    localEntries.value.unshift(newEntry);
    emit("numberOfEntriesChanged", localEntries.value.length);
  }
}

function onLabJournalDeleted(deletedIndex: number) {
  if (localEntries.value) {
    localEntries.value.splice(deletedIndex, 1);
    emit("numberOfEntriesChanged", localEntries.value.length);
  }
}
</script>

<template>
  <LabJournalNewEntry
    v-if="canCreate()"
    :collection-id="collectionNumericId!"
    :data-object-id="dataObjectNumericId!"
    @new-lab-journal-saved="newEntry => appendNewLabJournalEntry(newEntry)"
  />
  <div v-if="localEntries !== undefined">
    <LabJournalExistingEntry
      v-for="(entry, index) in localEntries"
      :key="entry.id"
      :collection-id="collectionNumericId ?? 0"
      :data-object-id="dataObjectNumericId ?? entry.dataObjectId"
      :lab-journal="entry"
      @deleted="onLabJournalDeleted(index)"
    />
    <EmptyListIcon v-if="localEntries.length === 0" label="No entry yet" />
  </div>
  <CenteredLoadingSpinner v-else-if="isLoading" />
</template>
