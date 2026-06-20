<script lang="ts" setup>
import {
  type LabJournalEntry,
  type Roles,
} from "@dlr-shepard/backend-client";
import { useFetchCollectionLabJournalEntries } from "~/composables/context/useFetchCollectionLabJournalEntries";

// UI-DO-LABJOURNAL-V2: replaced v1 numeric-id calls with the v2 collection-level
// endpoint (GET /v2/collections/{collectionAppId}/lab-journal-entries).
// Filter by dataObjectAppId (v2 IO field added UI-DO-LABJOURNAL-V2) so this works
// even when the v2 entities suppress numeric `id` (@JsonIgnoreProperties).
//
// Create/edit/delete child components remain on v1 (no v2 CRUD surface yet;
// tracked UI-DO-LABJOURNAL-V3). They are hidden when dataObjectId is undefined.
//
// Permission: isAllowedToWrite replaces the former v1 getCollectionRoles call,
// which required a numeric collectionId unavailable from v2 collection responses.

interface Props {
  /** UUID v7 appId of the parent Collection (v2 route param). */
  collectionAppId: string;
  /** UUID v7 appId of the DataObject — used to filter the collection-level fetch. */
  dataObjectAppId: string;
  /**
   * Numeric Neo4j id — optional. Still needed by LabJournalNewEntry (v1 create path).
   * When undefined (v2-only DOs) the create affordance is hidden.
   */
  dataObjectId?: number;
  /** Caller has write permission on the Collection (owner or writer role). */
  isAllowedToWrite: boolean;
}

const props = defineProps<Props>();
const emit = defineEmits(["numberOfEntriesChanged"]);

// Bulk fetch all entries for the collection (UI-020 N+1 fix), filter here.
const { entries: allEntries, isLoading } = useFetchCollectionLabJournalEntries(
  computed(() => props.collectionAppId),
);

// Filter to entries belonging to this DataObject by appId.
// Entries whose dataObjectAppId is null (pre-L2a) fall back to matching on the
// numeric dataObjectId when it is available — belt-and-braces for old rows.
const filteredEntries = computed<LabJournalEntry[] | undefined>(() => {
  if (!allEntries.value) return undefined;
  return allEntries.value.filter(e => {
    if (e.dataObjectAppId != null) return e.dataObjectAppId === props.dataObjectAppId;
    // fallback for pre-L2a entries: match by numeric id when available
    return props.dataObjectId != null && e.dataObjectId === props.dataObjectId;
  });
});

// Local mutable copy: avoids re-fetching the full collection list on create/delete.
const localEntries = ref<LabJournalEntry[]>([]);
watch(
  filteredEntries,
  val => {
    if (val !== undefined) {
      localEntries.value = [...val];
      emit("numberOfEntriesChanged", val.length);
    }
  },
  { immediate: true },
);

function appendNewLabJournalEntry(newEntry: LabJournalEntry) {
  localEntries.value.unshift(newEntry);
  emit("numberOfEntriesChanged", localEntries.value.length);
}

function onLabJournalDeleted(idx: number) {
  localEntries.value.splice(idx, 1);
  emit("numberOfEntriesChanged", localEntries.value.length);
}

// LabJournalExistingEntry still expects a Roles object for its permission guard.
// Synthesise from isAllowedToWrite so we don't need a separate v1 roles call.
const syntheticRoles = computed<Roles>(() => ({
  owner: props.isAllowedToWrite,
  manager: props.isAllowedToWrite,
  writer: props.isAllowedToWrite,
  reader: true,
}));
</script>

<template>
  <LabJournalNewEntry
    v-if="!!dataObjectId && isAllowedToWrite"
    :collection-id="0"
    :data-object-id="dataObjectId"
    @new-lab-journal-saved="savedEntry => appendNewLabJournalEntry(savedEntry)"
  />
  <CenteredLoadingSpinner v-if="isLoading && localEntries.length === 0" />
  <div v-if="!isLoading || localEntries.length > 0">
    <LabJournalExistingEntry
      v-for="(entry, index) in localEntries"
      :key="entry.id"
      :collection-id="0"
      :data-object-id="dataObjectId ?? 0"
      :lab-journal="entry"
      :user-roles="syntheticRoles"
      @deleted="onLabJournalDeleted(index)"
    />
    <EmptyListIcon
      v-if="!isLoading && localEntries.length === 0"
      label="No entry yet"
    />
  </div>
</template>
