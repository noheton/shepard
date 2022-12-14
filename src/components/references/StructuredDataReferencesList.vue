<script setup lang="ts">
import CreatedByLine from "@/components/generic/CreatedByLine.vue";
import GenericName from "@/components/generic/GenericName.vue";
import Loading from "@/components/generic/Loading.vue";
import CreateStructuredDataReferenceModal from "@/components/references/CreateStructuredDataReferenceModal.vue";
import StructuredDataReferenceModal from "@/components/references/StructuredDataReferenceModal.vue";
import StructuredDataReferenceService from "@/services/structuredDataReferenceService";
import { handleError } from "@/utils/error-handling";
import type {
  ResponseError,
  StructuredDataReference,
} from "@dlr-shepard/shepard-client";
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

const emit = defineEmits(["reference-count-changed"]);

const structuredDataList = ref<StructuredDataReference[]>();
const currentStructuredDataReference = ref<StructuredDataReference>();
const createdAlert = ref(false);
const deletedAlert = ref(false);

function retrieveReferences() {
  StructuredDataReferenceService.getAllStructuredDataReferences({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
  })
    .then(response => {
      structuredDataList.value = response;
      emit("reference-count-changed", structuredDataList.value.length);
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching structured data references");
    });
}

function create(newReference: StructuredDataReference) {
  StructuredDataReferenceService.createStructuredDataReference({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
    structuredDataReference: newReference,
  })
    .then(response => {
      createdAlert.value = true;
      structuredDataList.value = [response].concat(
        structuredDataList.value || [],
      );
      emit("reference-count-changed", structuredDataList.value.length);
    })
    .catch(e => {
      handleError(e as ResponseError, "creating structured data reference");
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

    <b-button
      v-b-modal.create-structured-data-ref-modal
      class="mb-3"
      variant="primary"
    >
      Create new Reference
    </b-button>

    <CreateStructuredDataReferenceModal
      modal-id="create-structured-data-ref-modal"
      modal-name="Create StructuredData Reference"
      @create="create($event)"
    />

    <div v-if="structuredDataList == undefined"><Loading /></div>
    <b-list-group v-else>
      <b-list-group-item
        v-for="(structuredDataReference, index) in structuredDataList"
        :key="index"
        v-b-modal.view-structured-data-modal
        button
        @click="currentStructuredDataReference = structuredDataReference"
      >
        <div>
          <b><GenericName :name="structuredDataReference.name || ''" /></b>
          | ID: {{ structuredDataReference.id }} |
          <span v-if="structuredDataReference.structuredDataContainerId != -1">
            <b-link
              :to="{
                name: 'StructuredData',
                params: {
                  structuredDataId:
                    structuredDataReference.structuredDataContainerId,
                },
              }"
            >
              Container: {{ structuredDataReference.structuredDataContainerId }}
            </b-link>
          </span>
          <span v-else class="text-danger">Container: Deleted</span>
        </div>
        <CreatedByLine
          :created-by="structuredDataReference.createdBy"
          :created-at="structuredDataReference.createdAt"
        />
      </b-list-group-item>
    </b-list-group>

    <StructuredDataReferenceModal
      modal-id="view-structured-data-modal"
      :modal-name="currentStructuredDataReference?.name || undefined"
      :current-collection-id="currentCollectionId"
      :current-data-object-id="currentDataObjectId"
      :structured-data-reference="currentStructuredDataReference"
      @reference-deleted="handleReferenceDelete()"
      @create="create($event)"
    />
  </div>
</template>
