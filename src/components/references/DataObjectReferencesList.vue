<script setup lang="ts">
import CreatedByLine from "@/components/generic/CreatedByLine.vue";
import GenericName from "@/components/generic/GenericName.vue";
import Loading from "@/components/generic/Loading.vue";
import BasicReferenceModal from "@/components/references/BasicReferenceModal.vue";
import BasicReferenceModal_DataObject from "@/components/references/BasicReferenceModal_DataObject.vue";
import CreateDataObjectReferenceModal from "@/components/references/CreateDataObjectReferenceModal.vue";
import DataObjectReferenceService from "@/services/dataObjectReferenceService";
import { handleError, logError } from "@/utils/error-handling";
import { getQueryParam } from "@/utils/helpers";
import type {
  DataObject,
  DataObjectReference,
  ResponseError,
} from "@dlr-shepard/shepard-client";
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

const dataObjectList = ref<DataObjectReference[]>();
const referencedList = ref<{ [key: number]: DataObject }>({});
const currentDataObjectReference = ref<DataObjectReference>();
const createdAlert = ref(false);
const deletedAlert = ref(false);

function retrieveReferences() {
  DataObjectReferenceService.getAllDataObjectReferences({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
  })
    .then(response => {
      dataObjectList.value = response;
      response.forEach(reference => {
        if (reference.id) retrieveDataObject(reference.id);
      });
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching data object references");
    })
    .finally(() => {
      currentDataObjectReference.value = dataObjectList.value?.find(e => {
        return e.id === Number(getQueryParam("referenceId"));
      });
      nextTick(() => {
        if (currentDataObjectReference.value)
          vm?.proxy.$bvModal.show("view-data-object-modal");
      });
    });
}

function retrieveDataObject(referenceId: number) {
  DataObjectReferenceService.getDataObjectReferencePayload({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
    dataObjectReferenceId: referenceId,
  })
    .then(response => {
      const temp: { [key: number]: DataObject } = {};
      temp[referenceId] = response;
      referencedList.value = { ...referencedList.value, ...temp };
    })
    .catch(e => {
      logError(e as ResponseError, "fetching data object reference payload");
    });
}

function createReference(newReference: DataObjectReference) {
  DataObjectReferenceService.createDataObjectReference({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
    dataObjectReference: newReference,
  })
    .then(response => {
      createdAlert.value = true;
      dataObjectList.value = [response].concat(dataObjectList.value || []);
      if (response.id) retrieveDataObject(response.id);
      emit("reference-count-changed", dataObjectList.value.length);
    })
    .catch(e => {
      handleError(e as ResponseError, "creating data object reference");
    });
}

function deleteReference() {
  if (!currentDataObjectReference.value?.id) return;
  DataObjectReferenceService.deleteDataObjectReference({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
    dataObjectReferenceId: currentDataObjectReference.value.id,
  })
    .then(() => {
      const temp = dataObjectList.value || [];
      dataObjectList.value = temp.filter(e => {
        return e.id != currentDataObjectReference.value?.id;
      });
      emit("reference-count-changed", dataObjectList.value.length);
      deletedAlert.value = true;
    })
    .catch(e => {
      handleError(e as ResponseError, "deleting data object reference");
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

    <b-button
      v-b-modal.create-data-object-ref-modal
      class="mb-3"
      variant="primary"
    >
      Create new Reference
    </b-button>

    <CreateDataObjectReferenceModal
      modal-id="create-data-object-ref-modal"
      modal-name="Create DataObject Reference"
      @create="createReference($event)"
    />

    <div v-if="dataObjectList == undefined"><Loading /></div>
    <b-list-group v-else>
      <b-list-group-item
        v-for="(dataObjectItem, index) in dataObjectList"
        :key="index"
        v-b-modal.view-data-object-modal
        button
        @click="currentDataObjectReference = dataObjectItem"
      >
        <div>
          <b><GenericName :name="dataObjectItem.name || ''" /></b> | ID:
          {{ dataObjectItem.id }}
        </div>
        <CreatedByLine
          :created-by="dataObjectItem.createdBy"
          :created-at="dataObjectItem.createdAt"
        />
        <small>
          {{ dataObjectItem.relationship }}:
          <span
            v-if="
              dataObjectItem.id && dataObjectItem.referencedDataObjectId != -1
            "
          >
            <b-link
              v-if="referencedList[dataObjectItem.id]"
              :to="{
                name: 'DataObject',
                params: {
                  collectionId: referencedList[dataObjectItem.id].collectionId,
                  dataObjectId: dataObjectItem.referencedDataObjectId,
                },
              }"
            >
              <b>{{ referencedList[dataObjectItem.id].name }}</b> | ID:
              {{ referencedList[dataObjectItem.id].id }}
            </b-link>
          </span>
          <span v-else class="text-danger">DateObject Deleted</span>
        </small>
      </b-list-group-item>
    </b-list-group>

    <BasicReferenceModal
      v-if="currentDataObjectReference?.id"
      modal-id="view-data-object-modal"
      :current-collection-id="currentCollectionId"
      :current-data-object-id="currentDataObjectId"
      :reference="currentDataObjectReference"
      @delete-reference="deleteReference()"
    >
      <BasicReferenceModal_DataObject
        :data-object-reference="currentDataObjectReference"
        :referenced-data-object="referencedList[currentDataObjectReference.id]"
      />
    </BasicReferenceModal>
  </div>
</template>
