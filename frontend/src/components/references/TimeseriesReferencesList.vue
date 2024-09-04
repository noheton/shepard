<script setup lang="ts">
import CreatedByLine from "@/components/generic/CreatedByLine.vue";
import GenericName from "@/components/generic/GenericName.vue";
import Loading from "@/components/generic/Loading.vue";
import BasicReferenceModal from "@/components/references/BasicReferenceModal.vue";
import BasicReferenceModal_Timeseries from "@/components/references/BasicReferenceModal_Timeseries.vue";
import CreateTimeseriesReferenceModal from "@/components/references/CreateTimeseriesReferenceModal.vue";
import type { ResponseError, TimeseriesReference } from "@/generated/openapi";
import TimeseriesReferenceService from "@/services/timeseriesReferenceService";
import { handleError } from "@/utils/error-handling";
import { convertDate, getQueryParam } from "@/utils/helpers";
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

const timeseriesList = ref<TimeseriesReference[]>();
const currentTimeseriesReference = ref<TimeseriesReference>();

const createdAlert = ref(false);
const deletedAlert = ref(false);

function retrieveReferences() {
  TimeseriesReferenceService.getAllTimeseriesReferences({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
  })
    .then(response => {
      timeseriesList.value = response;
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching timeseries references");
    })
    .finally(() => {
      currentTimeseriesReference.value = timeseriesList.value?.find(e => {
        return e.id === Number(getQueryParam("referenceId"));
      });
      nextTick(() => {
        if (currentTimeseriesReference.value) {
          vm?.proxy.$bvModal.show("view-timeseries-modal");
        }
      });
    });
}

function createReference(timeseriesReference: TimeseriesReference) {
  TimeseriesReferenceService.createTimeseriesReference({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
    timeseriesReference: timeseriesReference,
  })
    .then(response => {
      createdAlert.value = true;
      const temp = timeseriesList.value || [];
      timeseriesList.value = [...temp, response];
      emit("reference-count-changed", timeseriesList.value.length);
    })
    .catch(e => {
      handleError(e as ResponseError, "creating timeseries reference");
    });
}

function deleteReference() {
  if (!currentTimeseriesReference.value?.id) return;
  TimeseriesReferenceService.deleteTimeseriesReference({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
    timeseriesReferenceId: currentTimeseriesReference.value.id,
  })
    .then(() => {
      const temp = timeseriesList.value || [];
      timeseriesList.value = temp.filter(e => {
        return e.id != currentTimeseriesReference.value?.id;
      });
      emit("reference-count-changed", timeseriesList.value.length);
      deletedAlert.value = true;
    })
    .catch(e => {
      handleError(e as ResponseError, "deleting timeseries reference");
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

    <b-button v-b-modal.create-time-ref-modal class="mb-3" variant="primary">
      Create new Reference
    </b-button>

    <CreateTimeseriesReferenceModal
      modal-id="create-time-ref-modal"
      modal-name="Create Time Reference"
      @create="createReference($event)"
    />

    <div v-if="timeseriesList == undefined"><Loading /></div>
    <b-list-group v-else>
      <b-list-group-item
        v-for="(timeseriesItem, index) in timeseriesList"
        :key="index"
        v-b-modal.view-timeseries-modal
        button
        @click="currentTimeseriesReference = timeseriesItem"
      >
        <div>
          <b><GenericName :name="timeseriesItem.name || ''" /></b> | ID:
          {{ timeseriesItem.id }} |
          <span v-if="timeseriesItem.timeseriesContainerId != -1">
            <b-link
              :to="{
                name: 'Timeseries',
                params: { timeseriesId: timeseriesItem.timeseriesContainerId },
              }"
            >
              Container: {{ timeseriesItem.timeseriesContainerId }}
            </b-link>
          </span>
          <span v-else class="text-danger">Container: Deleted</span>
        </div>
        <CreatedByLine
          :created-by="timeseriesItem.createdBy"
          :created-at="timeseriesItem.createdAt"
        />
        <small>
          <b>start:</b>
          {{ convertDate(new Date(timeseriesItem.start / 1e6)) }}
          |
          <b>end:</b>
          {{ convertDate(new Date(timeseriesItem.end / 1e6)) }}
        </small>
      </b-list-group-item>
    </b-list-group>

    <BasicReferenceModal
      v-if="currentTimeseriesReference"
      modal-id="view-timeseries-modal"
      :current-collection-id="currentCollectionId"
      :current-data-object-id="currentDataObjectId"
      :reference="currentTimeseriesReference"
      @delete-reference="deleteReference()"
    >
      <BasicReferenceModal_Timeseries
        :current-collection-id="currentCollectionId"
        :current-data-object-id="currentDataObjectId"
        :timeseries-reference="currentTimeseriesReference"
      />
    </BasicReferenceModal>
  </div>
</template>
