<script setup lang="ts">
import {
  LabJournalEntryApi,
  type LabJournalEntry,
  type Roles,
} from "@dlr-shepard/backend-client";
import { computed } from "vue";

interface LabJournalEntryProps {
  labJournal: LabJournalEntry;
  userRoles: Roles | undefined;
}

const props = defineProps<LabJournalEntryProps>();
const model = ref(props.labJournal);
const emit = defineEmits(["deleted"]);

const title = `${toShortDateString(model.value.createdAt)} | by ${model.value.createdBy}`;
const isEditing = ref<boolean>(false);
const isExpanded = ref<boolean>(false);
const isHovering = ref<boolean>(false);

async function startEditing(event: Event) {
  event.stopPropagation();
  isEditing.value = true;
  isExpanded.value = true;
}

async function cancelEditing() {
  createApiInstance(LabJournalEntryApi)
    .getLabJournalById({ labJournalEntryId: model.value.id })
    .then(response => {
      model.value = response;
      isEditing.value = false;
      isExpanded.value = false;
    })
    .catch(error => {
      handleError(error, "getLabJournalById");
    });
}

async function saveChanges() {
  createApiInstance(LabJournalEntryApi)
    .updateLabJournal({
      labJournalEntryId: model.value.id,
      updateLabJournalRequest: {
        journalContent: model.value.journalContent,
      },
    })
    .then(response => {
      model.value = response;
      isEditing.value = false;
      isExpanded.value = true;
    })
    .catch(error => {
      handleError(error, "updateLabJournal");
    });
}

async function deleteEntry() {
  createApiInstance(LabJournalEntryApi)
    .deleteLabJournal({ labJournalEntryId: model.value.id })
    .then(_ => {
      emit("deleted");
    })
    .catch(error => {
      handleError(error, "deleteLabJournal");
    });
}

function isAllowedToEdit() {
  return props.userRoles?.owner || props.userRoles?.writer;
}

async function toggleExpanded() {
  if (isEditing.value === true) return;
  isExpanded.value = !isExpanded.value;
}

const getUpdatedInfoString = computed(() => {
  return `Last edited: ${toShortDateString(model.value.updatedAt)} by ${model.value.updatedBy}`;
});
</script>

<template>
  <div
    v-bind="props"
    class="w-100 mb-2 pa-2 border rounded"
    :class="{ 'border-active': isHovering }"
    @mouseenter="isHovering = true"
    @mouseleave="isHovering = false"
  >
    <!-- title row -->
    <div class="d-flex align-center">
      <v-icon
        :icon="isExpanded ? 'mdi-chevron-down' : 'mdi-chevron-right'"
        @click="toggleExpanded"
      />
      <span
        id="lab-journal-title"
        class="me-auto pa-2"
        :class="{ clickable: !isEditing }"
        @click="toggleExpanded"
      >
        {{ title }}
      </span>
      <span class="pr-2">
        <v-icon
          v-if="(isHovering || isExpanded) && isAllowedToEdit()"
          icon="mdi-pencil-outline"
          size="24"
          color="info"
          class="mr-4"
          :disabled="isEditing"
          @click="startEditing"
        />
        <CommonConfirmationDialog
          prompt-text="Are you sure you want to delete this item?"
          confirm-button-text="Delete"
          @confirmed="deleteEntry"
        >
          <v-icon
            v-if="(isHovering || isExpanded) && isAllowedToEdit()"
            icon="mdi-delete-outline"
            size="24"
            color="info"
            style="cursor: pointer"
          />
        </CommonConfirmationDialog>
      </span>
    </div>

    <!-- text editor -->
    <LabJournalEditor
      v-model="model.journalContent"
      class="pr-2 pl-4"
      :initial-content="model.journalContent"
      :is-editable="isEditing"
      :is-preview-collapsed="!isExpanded"
    />

    <div
      v-if="isExpanded && model.updatedAt != undefined && !isEditing"
      class="pl-8 py-2 updated-info-text"
    >
      {{ getUpdatedInfoString }}
    </div>

    <!-- action buttons -->
    <div v-if="isEditing" class="d-flex justify-end pt-2 px-6">
      <v-btn
        variant="flat"
        color="black-50"
        class="mr-4"
        @click="cancelEditing"
      >
        Cancel
      </v-btn>
      <v-btn variant="flat" color="primary" @click="saveChanges">Save</v-btn>
    </div>
  </div>
</template>

<style scoped>
#lab-journal-title {
  font-size: 16px;
  font-weight: 500;
  line-height: 28px;
}

.border {
  border: 1px solid rgb(var(--v-theme-black-100));
}

.border-active {
  border: 1px solid rgb(var(--v-theme-blue-500)) !important;
}

.clickable {
  cursor: pointer;
}

.updated-info-text {
  color: rgb(var(--v-theme-black-300));
}
</style>
