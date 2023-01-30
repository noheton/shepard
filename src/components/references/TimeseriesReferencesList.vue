<script setup lang="ts">
import CreatedByLine from "@/components/generic/CreatedByLine.vue";
import GenericName from "@/components/generic/GenericName.vue";
import Loading from "@/components/generic/Loading.vue";
import CreateTimeseriesReferenceModal from "@/components/references/CreateTimeseriesReferenceModal.vue";
import TimeseriesReferenceModal from "@/components/references/TimeseriesReferenceModal.vue";
import TimeseriesReferenceService from "@/services/timeseriesReferenceService";
import { handleError } from "@/utils/error-handling";
import {
  convertDate,
  getQueryParam,
  removeQueryParam,
  setQueryParam,
} from "@/utils/helpers";
import type {
  ResponseError,
  TimeseriesReference,
} from "@dlr-shepard/shepard-client";
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

const timeseriesList = ref<TimeseriesReference[]>();
const currentTimeseriesReference = ref<TimeseriesReference>();

const createdAlert = ref(false);
const deletedAlert = ref(false);

watch(currentTimeseriesReference, to => {
  setQueryParam("referenceId", String(to?.id));
});

function retrieveReferences() {
  TimeseriesReferenceService.getAllTimeseriesReferences({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
  })
    .then(response => {
      timeseriesList.value = response;
      emit("reference-count-changed", timeseriesList.value.length);
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching timeseries references");
    })
    .finally(() => {
      currentTimeseriesReference.value = timeseriesList.value?.find(e => {
        return e.id === Number(getQueryParam("referenceId"));
      });
      if (currentTimeseriesReference.value)
        vm?.proxy.$bvModal.show("view-timeseries-modal");
    });
}

function create(timeseriesReference: TimeseriesReference) {
  TimeseriesReferenceService.createTimeseriesReference({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
    timeseriesReference: timeseriesReference,
  })
    .then(response => {
      createdAlert.value = true;
      timeseriesList.value = [response].concat(timeseriesList.value || []);
      emit("reference-count-changed", timeseriesList.value.length);
    })
    .catch(e => {
      handleError(e as ResponseError, "creating timeseries reference");
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

    <b-button v-b-modal.create-time-ref-modal class="mb-3" variant="primary">
      Create new Reference
    </b-button>

    <CreateTimeseriesReferenceModal
      modal-id="create-time-ref-modal"
      modal-name="Create Time Reference"
      @create="create($event)"
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

    <TimeseriesReferenceModal
      modal-id="view-timeseries-modal"
      :modal-name="currentTimeseriesReference?.name || undefined"
      :current-collection-id="currentCollectionId"
      :current-data-object-id="currentDataObjectId"
      :timeseries-reference="currentTimeseriesReference"
      @reference-deleted="handleReferenceDelete()"
      @hidden="removeQueryParam('referenceId')"
    />
  </div>
</template>
