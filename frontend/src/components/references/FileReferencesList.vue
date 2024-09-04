<script setup lang="ts">
import CreatedByLine from "@/components/generic/CreatedByLine.vue";
import GenericName from "@/components/generic/GenericName.vue";
import Loading from "@/components/generic/Loading.vue";
import BasicReferenceModal from "@/components/references/BasicReferenceModal.vue";
import BasicReferenceModal_File from "@/components/references/BasicReferenceModal_File.vue";
import CreateFileReferenceModal from "@/components/references/CreateFileReferenceModal.vue";
import type { FileReference, ResponseError } from "@/generated/openapi";
import FileReferenceService from "@/services/fileReferenceService";
import { handleError } from "@/utils/error-handling";
import { getQueryParam } from "@/utils/helpers";
import { getCurrentInstance, nextTick, onMounted, ref } from "vue";

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
    })
    .finally(() => {
      currentFileReference.value = fileReferenceList.value?.find(e => {
        return e.id === Number(getQueryParam("referenceId"));
      });
      nextTick(() => {
        if (currentFileReference.value)
          vm?.proxy.$bvModal.show("view-file-modal");
      });
    });
}

function createReference(newReference: FileReference) {
  FileReferenceService.createFileReference({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
    fileReference: newReference,
  })
    .then(response => {
      createdAlert.value = true;
      const temp = fileReferenceList.value || [];
      fileReferenceList.value = [...temp, response];
      emit("reference-count-changed", fileReferenceList.value.length);
    })
    .catch(e => {
      handleError(e as ResponseError, "creating file reference");
    });
}

function deleteReference() {
  if (!currentFileReference.value?.id) return;
  FileReferenceService.deleteFileReference({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
    fileReferenceId: currentFileReference.value.id,
  })
    .then(() => {
      const temp = fileReferenceList.value || [];
      fileReferenceList.value = temp.filter(e => {
        return e.id != currentFileReference.value?.id;
      });
      emit("reference-count-changed", fileReferenceList.value.length);
      deletedAlert.value = true;
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
      @create="createReference($event)"
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

    <BasicReferenceModal
      v-if="currentFileReference"
      modal-id="view-file-modal"
      :current-collection-id="currentCollectionId"
      :current-data-object-id="currentDataObjectId"
      :reference="currentFileReference"
      @delete-reference="deleteReference()"
    >
      <BasicReferenceModal_File
        :current-collection-id="currentCollectionId"
        :current-data-object-id="currentDataObjectId"
        :file-reference="currentFileReference"
      />
    </BasicReferenceModal>
  </div>
</template>
