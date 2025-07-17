<script lang="ts" setup>
import {
  LabJournalEntryApi,
  type LabJournalEntry,
  type Roles,
  FileContainerApi,
} from "@dlr-shepard/backend-client";
import { computed } from "vue";
import { useShepardApi } from "~/composables/common/api/useShepardApi";
import type { Editor } from "@tiptap/vue-3";
import BiMap from "bidirectional-map";
import { valsToNeo4jImg, db_img_regex } from "~/composables/labJournalHelper";

interface LabJournalExistingEntryProps {
  labJournal: LabJournalEntry;
  collectionId: number;
  dataObjectId: number;
  dataObjectName?: string;
  userRoles?: Roles;
}

const props = defineProps<LabJournalExistingEntryProps>();
const model = ref(props.labJournal);
const emit = defineEmits(["deleted"]);

const title = `${toShortDateString(model.value.createdAt)} | by ${model.value.createdBy} | entry id: ${props.labJournal.id}`;
const isEditing = ref<boolean>(false);
const isExpanded = ref<boolean>(false);
const isHovering = ref<boolean>(false);
const showDeleteDialog = ref<boolean>(false);

async function startEditing(event: Event) {
  event.stopPropagation();
  isEditing.value = true;
  isExpanded.value = true;
}

const labJournalApi = useShepardApi(LabJournalEntryApi);

async function cancelEditing() {
  labJournalApi.value
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
  labJournalApi.value
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
  labJournalApi.value
    .deleteLabJournal({ labJournalEntryId: model.value.id })
    .then(_ => {
      emit("deleted");
      emitSuccess("Entry deleted!");
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

const editor = shallowRef<Editor>();

/**
 Identifying information for an image that is part of the lab journal.
 Consists of the oid, the file container id and the description (alt).
 */
type ImageIdentifier = [number, string, string];
const oidUrlMap = ref<BiMap<ImageIdentifier>>(new BiMap());

async function updateOidUrlMap() {
  oidUrlMap.value = new BiMap<ImageIdentifier>();
  const editorVal = editor.value?.getHTML() ?? "";
  let arr;
  const reg = db_img_regex();
  while ((arr = reg.exec(editorVal)) !== null) {
    const [_, s_fcid, oid, alt] = arr;
    const fcid = Number(s_fcid);
    if (!oidUrlMap.value.hasValue([fcid, oid!, alt!])) {
      try {
        const imgUrl = URL.createObjectURL(await fetchImage(fcid, oid!));
        oidUrlMap.value.set(imgUrl, [fcid, oid!, alt!]);
      } catch (error) {
        handleError(error, "while fetching image");
      }
    }
  }
}

function addParagraph(str: string): string {
  return "<p>" + str + "</p>";
}

function toOptionalParagraphRegex(str: string): RegExp {
  return new RegExp("(?:<p>)?" + escapeRegex(str) + "(?:</p>)?");
}

function valsToHtmlImg(url: string, alt: string): string {
  return `<img src=${url} alt="${alt}" />`;
}

function dbImg2DisplayImg() {
  let editorVal = editor.value?.getHTML() ?? "";
  for (const [htmlImg, dbImg] of oidUrlMap.value.entries()) {
    const [fcid, oid, alt] = dbImg;
    editorVal = editorVal.replace(
      toOptionalParagraphRegex(valsToNeo4jImg(fcid, oid, alt)),
      valsToHtmlImg(htmlImg, alt),
    );
  }
  editor.value?.commands.setContent(editorVal);
}

function escapeRegex(str: string): string {
  return str.replace(/[/\-\\^$*+?.()|[\]{}]/g, "\\$&");
}

function displayImgs2dbImgs() {
  let editorVal = editor.value?.getHTML() ?? "";
  for (const [htmlImg, dbImg] of oidUrlMap.value.entries()) {
    const escapedHtmlImg = escapeRegex(htmlImg);
    const [fcid, oid, alt] = dbImg;
    const regex = new RegExp('<img[^>]*?src="' + escapedHtmlImg + '"[^>]*?>');
    editorVal = editorVal.replace(
      regex,
      addParagraph(valsToNeo4jImg(fcid, oid, alt)),
    );
  }
  editor.value?.commands.setContent(editorVal);
}

onMounted(async () => {
  await updateOidUrlMap();
  dbImg2DisplayImg();
});

watch(isEditing, async () => {
  if (isEditing.value) {
    displayImgs2dbImgs();
    await updateOidUrlMap();
  } else {
    await updateOidUrlMap();
    dbImg2DisplayImg();
  }
});

function fetchImage(fileContainerId: number, oid: string): Promise<Blob> {
  return useShepardApi(FileContainerApi).value.getFile({
    fileContainerId: fileContainerId,
    oid: oid,
  });
}
</script>

<template>
  <div
    :class="{ 'border-active': isHovering }"
    class="w-100 mb-2 pa-2 border rounded"
    v-bind="props"
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
        :class="{ clickable: !isEditing }"
        class="pa-2 pr-0 text-textbody1 text-subtitle-2"
        @click="toggleExpanded"
      >
        {{ title }}
      </span>
      &nbsp;|
      <span v-if="props.dataObjectName" id="lab-journal-title" class="pa-2">
        <NuxtLink
          :to="getDataObjectLink(props.dataObjectId)"
          class="dataobject-link"
        >
          {{ props.dataObjectName }}
        </NuxtLink>
      </span>
      <v-spacer />
      <span class="pr-2">
        <v-icon
          v-if="(isHovering || isExpanded) && isAllowedToEdit()"
          :disabled="isEditing"
          class="mr-4"
          color="info"
          icon="mdi-pencil-outline"
          size="24"
          @click="startEditing"
        />
        <template v-if="(isHovering || isExpanded) && isAllowedToEdit()">
          <v-icon
            color="info"
            icon="mdi-delete-outline"
            size="24"
            style="cursor: pointer"
            @click="showDeleteDialog = true"
          />
        </template>
      </span>
    </div>

    <ConfirmDeleteDialog
      v-if="showDeleteDialog"
      v-model:show-dialog="showDeleteDialog"
      @confirmed="deleteEntry"
    />

    <LabJournalEntry
      v-model:journal-content="model.journalContent"
      :collection-id="collectionId"
      :data-object-id="dataObjectId"
      :is-editing="isEditing"
      :is-expanded="isExpanded"
      @editor-created="ed => (editor = ed)"
    />

    <div
      v-if="isExpanded && model.updatedAt != undefined && !isEditing"
      class="pl-8 py-2 text-textbody2 text-subtitle-2"
    >
      {{ getUpdatedInfoString }}
    </div>

    <!-- action buttons -->
    <div v-if="isEditing" class="d-flex justify-end pt-2 px-6">
      <v-btn
        class="mr-4"
        color="treeview"
        variant="flat"
        @click="cancelEditing"
      >
        Cancel
      </v-btn>
      <v-btn color="primary" variant="flat" @click="saveChanges">Save</v-btn>
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

:deep(img) {
  max-width: 500px;
}
</style>
