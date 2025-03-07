<script setup lang="ts">
import {
  LabJournalEntryApi,
  type LabJournalEntry,
  type Roles,
} from "@dlr-shepard/backend-client";
import { computed } from "vue";
import RichTextEditor from "~/components/common/editor/RichTextEditor.vue";

interface LabJournalEntryProps {
  labJournal: LabJournalEntry;
  userRoles?: Roles;
  dataObjectLink?: DataObjectLink;
}

type DataObjectLink = {
  dataObjectId: number;
  dataObjectName: string;
};

const props = defineProps<LabJournalEntryProps>();
const model = ref(props.labJournal);
const emit = defineEmits(["deleted"]);

const title = `${toShortDateString(model.value.createdAt)} | by ${model.value.createdBy}`;
const isEditing = ref<boolean>(false);
const isExpanded = ref<boolean>(false);
const isHovering = ref<boolean>(false);
const showDeleteDialog = ref<boolean>(false);

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

function toggleExpanded() {
  if (isEditing.value === true) return;
  isExpanded.value = !isExpanded.value;
}

function getDataObjectLink(dataObjectId: number): string {
  const routePath = useRoute().path;
  return routePath + `/dataobjects/${dataObjectId}`;
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
        class="pa-2 pr-0 text-textbody1 text-subtitle-2"
        :class="{ clickable: !isEditing }"
        @click="toggleExpanded"
      >
        {{ title }}
      </span>
      <span v-if="props.dataObjectLink" id="lab-journal-title" class="pa-2">
        |
        <NuxtLink
          class="dataobject-link"
          :to="getDataObjectLink(props.dataObjectLink.dataObjectId)"
        >
          {{ props.dataObjectLink.dataObjectName }}
        </NuxtLink>
      </span>
      <v-spacer />
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
        <template v-if="(isHovering || isExpanded) && isAllowedToEdit()">
          <v-icon
            icon="mdi-delete-outline"
            size="24"
            color="info"
            style="cursor: pointer"
            @click="showDeleteDialog = true"
          />
          <ConfirmDeleteDialog
            v-model:show-dialog="showDeleteDialog"
            prompt-text="Are you sure you want to delete this item?"
            @confirmed="deleteEntry"
          />
        </template>
      </span>
    </div>

    <!-- text editor -->
    <div class="mx-4">
      <RichTextEditor
        v-model="model.journalContent"
        class="pr-2 pl-4"
        :initial-content="model.journalContent"
        :is-editable="isEditing"
        :is-preview-collapsed="!isExpanded"
        :autofocus="true"
      />
    </div>

    <div
      v-if="isExpanded && model.updatedAt != undefined && !isEditing"
      class="pl-8 py-2 text-textbody2 text-subtitle-2"
    >
      {{ getUpdatedInfoString }}
    </div>

    <!-- action buttons -->
    <div v-if="isEditing" class="d-flex justify-end pt-2 px-6">
      <v-btn
        variant="flat"
        color="treeview"
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
  font-style: normal;
}

.dataobject-link {
  color: rgb(var(--v-theme-primary));
}

.border {
  border: 1px solid rgb(var(--v-theme-divider1));
}

.border-active {
  border: 1px solid rgb(var(--v-theme-primary)) !important;
}

.clickable {
  cursor: pointer;
}

:deep(.tiptap) {
  min-height: 6lh;
}
</style>
