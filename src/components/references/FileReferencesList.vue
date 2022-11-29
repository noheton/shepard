<script setup lang="ts">
import DeleteConfirmationModal from "@/components/DeleteConfirmationModal.vue";
import CreatedByLine from "@/components/generic/CreatedByLine.vue";
import GenericName from "@/components/generic/GenericName.vue";
import Loading from "@/components/generic/Loading.vue";
import CreateFileReferenceModal from "@/components/references/CreateFileReferenceModal.vue";
import FileReferenceModal from "@/components/references/FileReferenceModal.vue";
import FileReferenceService from "@/services/fileReferenceService";
import { handleError } from "@/utils/error-handling";

import type { FileReference, ResponseError } from "@dlr-shepard/shepard-client";
import { onMounted, ref } from "vue";

const props = defineProps({
  currentCollectionId: {
    type: Number,
    required: true,
  },
  currentDataObjectId: {
    type: Number,
    required: true,
  },
});

const fileReferenceList = ref<FileReference[]>();
const currentFileReference = ref<FileReference>();
const createdAlert = ref(false);
const deletedAlert = ref(false);

function create(newReference: FileReference) {
  FileReferenceService.createFileReference({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
    fileReference: newReference,
  })
    .then(response => {
      createdAlert.value = true;
      fileReferenceList.value = [response].concat(
        fileReferenceList.value || [],
      );
      if (response.id) retrieveReferences();
    })
    .catch(e => {
      handleError(e as ResponseError, "creating file reference");
    });
}

function retrieveReferences() {
  FileReferenceService.getAllFileReferences({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
  })
    .then(response => {
      fileReferenceList.value = response;
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching file references");
    });
}

function handleDelete() {
  if (!currentFileReference.value?.id) return;
  FileReferenceService.deleteFileReference({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
    fileReferenceId: currentFileReference.value.id,
  })
    .then(() => {
      deletedAlert.value = true;
      retrieveReferences();
    })
    .catch(e => {
      handleError(e as ResponseError, "deleting file reference");
    });
}

onMounted(() => {
  retrieveReferences();
});
</script>

<template>
  <div class="list">
    <b-alert
      :show="createdAlert"
      dismissible
      variant="success"
      @dismissed="createdAlert = false"
    >
      Successfully created
    </b-alert>
    <b-alert
      :show="deletedAlert"
      dismissible
      variant="info"
      @dismissed="deletedAlert = false"
    >
      Successfully deleted
    </b-alert>
    <b-button v-b-modal.create-file-ref-modal class="mb-3" variant="primary">
      Create new Reference
    </b-button>

    <CreateFileReferenceModal
      modal-id="create-file-ref-modal"
      modal-name="Create File Reference"
      @create="create($event)"
    />

    <div v-if="fileReferenceList == undefined"><Loading /></div>
    <b-list-group v-else>
      <b-list-group-item
        v-for="(fileReference, index) in fileReferenceList"
        :key="index"
      >
        <div>
          <b>
            <GenericName :word-count="30" :name="fileReference.name || ''" />
          </b>
          | ID: {{ fileReference.id }} |
          <span v-if="fileReference.fileContainerId != -1">
            <b-link
              :to="{
                name: 'Files',
                params: { fileId: fileReference.fileContainerId },
              }"
            >
              Container: {{ fileReference.fileContainerId }}
            </b-link>
          </span>
          <span v-else class="text-danger">Container: Deleted</span>
          <span>
            | Files:
            <b-badge variant="secondary">
              {{ fileReference.fileOids.length }}
            </b-badge>
          </span>

          <b-button-group class="float-right">
            <b-button
              v-b-modal.view-ref-modal
              v-b-tooltip.hover
              title="View Reference"
              variant="primary"
              @click="currentFileReference = fileReference"
            >
              <EyeIcon />
            </b-button>

            <b-button
              v-b-modal.file-reference-delete-confirmation-modal
              v-b-tooltip.hover
              title="Delete"
              variant="info"
              @click="currentFileReference = fileReference"
            >
              <DeleteIcon />
            </b-button>
          </b-button-group>
        </div>
        <CreatedByLine
          :created-by="fileReference.createdBy"
          :created-at="fileReference.createdAt"
        />
      </b-list-group-item>
    </b-list-group>

    <FileReferenceModal
      modal-id="view-ref-modal"
      :modal-name="currentFileReference?.name || undefined"
      :current-collection-id="currentCollectionId"
      :current-data-object-id="currentDataObjectId"
      :file-reference="currentFileReference"
      @create="create($event)"
    />

    <DeleteConfirmationModal
      v-if="currentFileReference"
      modal-id="file-reference-delete-confirmation-modal"
      modal-name="Confirm to delete File Reference"
      :modal-text="
        'Do you really want do delete the File Reference with name ' +
        currentFileReference.name +
        '?'
      "
      @confirmation="handleDelete()"
    />
  </div>
</template>
