<script setup lang="ts">
import CreatedByLine from "@/components/generic/CreatedByLine.vue";
import GenericName from "@/components/generic/GenericName.vue";
import Loading from "@/components/generic/Loading.vue";
import CreateFileReferenceModal from "@/components/references/CreateFileReferenceModal.vue";
import FileReferenceModal from "@/components/references/FileReferenceModal.vue";
import FileReferenceService from "@/services/fileReferenceService";
import { handleError } from "@/utils/error-handling";
import {
  getQueryParam,
  removeQueryParam,
  setQueryParam,
} from "@/utils/helpers";
import type { FileReference, ResponseError } from "@dlr-shepard/shepard-client";
import { getCurrentInstance, onMounted, ref, watch } from "vue";

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

const vm = getCurrentInstance();

const emit = defineEmits(["reference-count-changed"]);

const fileReferenceList = ref<FileReference[]>();
const currentFileReference = ref<FileReference>();
const createdAlert = ref(false);
const deletedAlert = ref(false);

watch(currentFileReference, to => {
  setQueryParam("referenceId", String(to?.id));
});

function retrieveReferences() {
  FileReferenceService.getAllFileReferences({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
  })
    .then(response => {
      fileReferenceList.value = response;
      emit("reference-count-changed", fileReferenceList.value.length);
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching file references");
    })
    .finally(() => {
      currentFileReference.value = fileReferenceList.value?.find(e => {
        return e.id === Number(getQueryParam("referenceId"));
      });
      if (currentFileReference.value)
        vm?.proxy.$bvModal.show("view-file-modal");
    });
}

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
      emit("reference-count-changed", fileReferenceList.value.length);
      if (response.id) retrieveReferences();
    })
    .catch(e => {
      handleError(e as ResponseError, "creating file reference");
    });
}

function handleReferenceDelete() {
  deletedAlert.value = true;
  retrieveReferences();
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
        v-b-modal.view-file-modal
        button
        @click="currentFileReference = fileReference"
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
        </div>
        <CreatedByLine
          :created-by="fileReference.createdBy"
          :created-at="fileReference.createdAt"
        />
      </b-list-group-item>
    </b-list-group>

    <FileReferenceModal
      modal-id="view-file-modal"
      :modal-name="currentFileReference?.name || undefined"
      :current-collection-id="currentCollectionId"
      :current-data-object-id="currentDataObjectId"
      :file-reference="currentFileReference"
      @reference-deleted="handleReferenceDelete()"
      @hidden="removeQueryParam('referenceId')"
    />
  </div>
</template>
