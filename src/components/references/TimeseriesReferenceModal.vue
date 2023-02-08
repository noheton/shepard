<script setup lang="ts">
import SemanticAnnotationModal from "@/components/dataobjects/SemanticAnnotationModal.vue";
import DeleteConfirmationModal from "@/components/DeleteConfirmationModal.vue";
import CreatedByLine from "@/components/generic/CreatedByLine.vue";
import SemanticBadge from "@/components/generic/SemanticBadge.vue";
import TimeseriesPlottingModal from "@/components/payload/TimeseriesPlottingModal.vue";
import ProcessAlert from "@/components/ProcessAlert.vue";
import SemanticAnnotationService from "@/services/semanticAnnotationService";
import TimeseriesReferenceService from "@/services/timeseriesReferenceService";
import { downloadFile } from "@/utils/download";
import { handleError, logError } from "@/utils/error-handling";
import { convertDate } from "@/utils/helpers";
import type {
  ResponseError,
  SemanticAnnotation,
  TimeseriesPayload,
  TimeseriesReference,
} from "@dlr-shepard/shepard-client";
import { getCurrentInstance, reactive, ref, watch, type PropType } from "vue";

const props = defineProps({
  modalId: {
    type: String,
    default: "timeseries-reference-modal",
  },
  modalName: {
    type: String,
    default: "Timeseries Reference",
  },
  currentCollectionId: {
    type: Number,
    required: true,
  },
  currentDataObjectId: {
    type: Number,
    required: true,
  },
  timeseriesReference: {
    type: Object as PropType<TimeseriesReference>,
    default: undefined,
  },
});

const vm = getCurrentInstance();

const emit = defineEmits(["reference-deleted", "hidden"]);

const getInitialState = () => ({
  active: false,
  finished: false,
  error: false,
  errorMessage: "",
  plottingError: false,
  plottingErrorMessage: "",
});

const currentTimeseriesPayload = ref<TimeseriesPayload[]>();
const internalState = reactive(getInitialState());

function reset() {
  Object.assign(internalState, getInitialState());
}

watch(
  () => props.timeseriesReference,
  () => {
    fetchTimeseriesPayload();
    getAllTimeseriesReferenceAnnotations();
  },
);

function fetchTimeseriesPayload() {
  if (!props.timeseriesReference?.id) return;
  TimeseriesReferenceService.getTimeseriesPayload({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
    timeseriesReferenceId: props.timeseriesReference.id,
  })
    .then(response => {
      currentTimeseriesPayload.value = response;
    })
    .catch(e => {
      currentTimeseriesPayload.value = [];
      logError(e as ResponseError, "fetching timeseries payload");
      internalState.plottingError = true;
      if (e.response.status == 403) {
        internalState.plottingErrorMessage =
          "Authentication Error: No permission to access this timeseries container";
      }
    });
}

function downloadCsv(referenceId: number, referenceName: string) {
  internalState.active = true;
  TimeseriesReferenceService.exportTimeseriesPayload({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
    timeseriesReferenceId: referenceId,
  })
    .then(response => {
      downloadFile(response, referenceName + ".csv");
      internalState.finished = true;
    })
    .catch(e => {
      logError(e as ResponseError, "fetching timeseries payload");
      internalState.error = true;
      if (e.response.status == 403) {
        internalState.errorMessage =
          "Authentication Error: No permission to access this timeseries container";
      }
    })
    .finally(() => (internalState.active = false));
}

function handleDelete() {
  if (!props.timeseriesReference?.id) return;
  TimeseriesReferenceService.deleteTimeseriesReference({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
    timeseriesReferenceId: props.timeseriesReference.id,
  })
    .then(() => {
      emit("reference-deleted");
      vm?.proxy.$bvModal.hide(props.modalId);
    })
    .catch(e => {
      handleError(e as ResponseError, "deleting timeseries reference");
    });
}

const timeseriesReferenceAnnotationList = ref<SemanticAnnotation[]>([]);
function getAllTimeseriesReferenceAnnotations() {
  if (!props.timeseriesReference?.id) return;
  SemanticAnnotationService.getAllReferenceAnnotations({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
    referenceId: +props.timeseriesReference.id,
  })
    .then(annotationList => {
      timeseriesReferenceAnnotationList.value = annotationList;
    })
    .catch(e => {
      handleError(
        e as ResponseError,
        "get all semantic timeseries reference annotations",
      );
    });
}
function createTimeseriesReferenceAnnotation(
  semanticAnnotation: SemanticAnnotation,
) {
  if (!props.timeseriesReference?.id) return;
  SemanticAnnotationService.createReferenceAnnotation({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
    referenceId: +props.timeseriesReference.id,
    semanticAnnotation: semanticAnnotation,
  })
    .then(newAnnotation => {
      const temp = [...timeseriesReferenceAnnotationList.value, newAnnotation];
      timeseriesReferenceAnnotationList.value = temp;
    })
    .catch(e => {
      handleError(
        e as ResponseError,
        "creating semantic timeseries reference annotation",
      );
    });
}

function deleteTimeseriesReferenceAnnotation(semanticAnnotationId: number) {
  if (!props.timeseriesReference?.id) return;
  SemanticAnnotationService.deleteReferenceAnnotation({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
    referenceId: +props.timeseriesReference.id,
    semanticAnnotationId: semanticAnnotationId,
  })
    .then(() => {
      const temp = timeseriesReferenceAnnotationList.value.filter(a => {
        return a.id != semanticAnnotationId;
      });
      timeseriesReferenceAnnotationList.value = temp;
    })
    .catch(e => {
      handleError(
        e as ResponseError,
        "deleting semantic timeseries reference annotation",
      );
    });
}
</script>

<template>
  <b-modal
    :id="modalId"
    :title="modalName"
    size="lg"
    lazy
    ok-only
    ok-variant="secondary"
    ok-title="Close"
    @show="reset()"
    @hidden="emit('hidden')"
  >
    <ProcessAlert
      process-name="Plotting"
      :process-error="internalState.plottingError"
      :process-error-message="internalState.plottingErrorMessage"
      @error-message-dismissed="internalState.plottingError = false"
    />

    <ProcessAlert
      process-name="Download"
      :process-active="internalState.active"
      :process-started="internalState.finished"
      :process-error="internalState.error"
      :process-error-message="internalState.errorMessage"
      @success-message-dismissed="internalState.finished = false"
      @error-message-dismissed="internalState.error = false"
    />

    <div v-if="props.timeseriesReference" class="mb-4">
      <b-button-group class="float-right">
        <b-button
          v-b-modal.plotting_modal
          v-b-tooltip.hover
          title="Plotting"
          variant="primary"
          :disabled="props.timeseriesReference.timeseriesContainerId == -1"
        >
          <PlottingIcon />
        </b-button>

        <b-button
          v-b-tooltip.hover
          title="Download"
          variant="secondary"
          :disabled="
            internalState.active ||
            props.timeseriesReference.timeseriesContainerId == -1
          "
          @click="
            if (props.timeseriesReference.id)
              downloadCsv(
                props.timeseriesReference.id,
                props.timeseriesReference.name || '',
              );
          "
        >
          <DownloadIcon />
        </b-button>
        <b-button
          v-b-modal.edit-timeseries-reference-semantic-modal
          v-b-tooltip.hover
          title="Edit Semantic Annotation"
          variant="secondary"
        >
          <SemanticIcon />
        </b-button>
        <b-button
          v-b-modal.timeseries-reference-delete-confirmation-modal
          v-b-tooltip.hover
          title="Delete"
          variant="info"
        >
          <DeleteIcon />
        </b-button>
      </b-button-group>

      ID: {{ props.timeseriesReference?.id }} |
      <span v-if="props.timeseriesReference?.timeseriesContainerId != -1">
        <b-link
          :to="{
            name: 'Files',
            params: {
              fileId: props.timeseriesReference?.timeseriesContainerId,
            },
          }"
        >
          Container: {{ props.timeseriesReference?.timeseriesContainerId }}
        </b-link>
      </span>
      <span v-else class="text-danger">Container: Deleted</span>

      <CreatedByLine
        :created-by="props.timeseriesReference?.createdBy"
        :created-at="props.timeseriesReference?.createdAt"
      />
      <small>
        <b>start:</b>
        {{ convertDate(new Date(props.timeseriesReference.start / 1e6)) }}
        |
        <b>end:</b>
        {{ convertDate(new Date(props.timeseriesReference.start / 1e6)) }}
      </small>

      <SemanticBadge :annotation-list="timeseriesReferenceAnnotationList" />

      <b-table
        striped
        hover
        small
        :items="props.timeseriesReference.timeseries"
      >
      </b-table>
    </div>

    <TimeseriesPlottingModal
      v-if="props.timeseriesReference && currentTimeseriesPayload"
      modal-id="plotting_modal"
      :modal-name="props.timeseriesReference.name || undefined"
      :timeseries-payload-list="currentTimeseriesPayload"
      :timeseries-start-time="props.timeseriesReference.start"
    />

    <SemanticAnnotationModal
      v-if="props.timeseriesReference"
      modal-id="edit-timeseries-reference-semantic-modal"
      modal-name="Add Semantic"
      :annotation-list="timeseriesReferenceAnnotationList"
      @create="createTimeseriesReferenceAnnotation($event)"
      @delete="deleteTimeseriesReferenceAnnotation($event)"
    />

    <DeleteConfirmationModal
      v-if="props.timeseriesReference"
      modal-id="timeseries-reference-delete-confirmation-modal"
      modal-name="Confirm to delete Timeseries Reference"
      :modal-text="
        'Do you really want do delete the Timeseries Reference with name ' +
        props.timeseriesReference.name +
        '?'
      "
      @confirmation="handleDelete()"
    />
  </b-modal>
</template>
