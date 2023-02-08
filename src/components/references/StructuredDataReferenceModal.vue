<script setup lang="ts">
import SemanticAnnotationModal from "@/components/dataobjects/SemanticAnnotationModal.vue";
import DeleteConfirmationModal from "@/components/DeleteConfirmationModal.vue";
import CreatedByLine from "@/components/generic/CreatedByLine.vue";
import GenericName from "@/components/generic/GenericName.vue";
import JsonEditorModal from "@/components/generic/JsonEditorModal.vue";
import SemanticBadge from "@/components/generic/SemanticBadge.vue";
import SemanticAnnotationService from "@/services/semanticAnnotationService";
import StructuredDataReferenceService from "@/services/structuredDataReferenceService";
import { handleError, logError } from "@/utils/error-handling";
import { convertDate } from "@/utils/helpers";
import type {
  ResponseError,
  SemanticAnnotation,
  StructuredDataPayload,
  StructuredDataReference,
} from "@dlr-shepard/shepard-client";
import { getCurrentInstance, ref, watch, type PropType } from "vue";

const props = defineProps({
  modalId: {
    type: String,
    default: "structured-data-reference-modal",
  },
  modalName: {
    type: String,
    default: "Structured Data Reference",
  },
  currentCollectionId: {
    type: Number,
    required: true,
  },
  currentDataObjectId: {
    type: Number,
    required: true,
  },
  structuredDataReference: {
    type: Object as PropType<StructuredDataReference>,
    default: undefined,
  },
});

const vm = getCurrentInstance();

const emit = defineEmits(["reference-deleted", "hidden"]);

const currentStructuredDataOid = ref<string>();
const structuredDatas = ref<{ [key: string]: StructuredDataPayload }>({});

function reset() {
  currentStructuredDataOid.value = undefined;
}

watch(
  () => props.structuredDataReference,
  () => {
    getStructuredDataPayload();
    getAllStructureDataReferenceAnnotations();
  },
);

function getStructuredDataPayload() {
  if (!props.structuredDataReference.id) return;
  StructuredDataReferenceService.getStructuredDataPayload({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
    structureddataReferenceId: props.structuredDataReference.id,
  })
    .then(response => {
      const temp: { [key: string]: StructuredDataPayload } = {};
      response.forEach(payload => {
        if (payload?.structuredData?.oid) {
          temp[payload.structuredData.oid] = payload;
        }
      });
      structuredDatas.value = { ...structuredDatas.value, ...temp };
    })
    .catch(e => {
      logError(e as ResponseError, "fetching structured data");
    });
}

function handleDelete() {
  if (!props.structuredDataReference.id) return;
  StructuredDataReferenceService.deleteStructuredDataReference({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
    structureddataReferenceId: props.structuredDataReference.id,
  })
    .then(() => {
      emit("reference-deleted");
      vm?.proxy.$bvModal.hide(props.modalId);
    })
    .catch(e => {
      handleError(e as ResponseError, "deleting structured data reference");
    });
}

const structuredDataReferenceAnnotationList = ref<SemanticAnnotation[]>([]);
function getAllStructureDataReferenceAnnotations() {
  if (!props.structuredDataReference?.id) return;
  SemanticAnnotationService.getAllReferenceAnnotations({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
    referenceId: +props.structuredDataReference.id,
  })
    .then(annotationList => {
      structuredDataReferenceAnnotationList.value = annotationList;
    })
    .catch(e => {
      handleError(
        e as ResponseError,
        "get all semantic structured data reference annotations",
      );
    });
}
function createStructuredDataReferenceAnnotation(
  semanticAnnotation: SemanticAnnotation,
) {
  if (!props.structuredDataReference?.id) return;
  SemanticAnnotationService.createReferenceAnnotation({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
    referenceId: +props.structuredDataReference.id,
    semanticAnnotation: semanticAnnotation,
  })
    .then(newAnnotation => {
      const temp = [
        ...structuredDataReferenceAnnotationList.value,
        newAnnotation,
      ];
      structuredDataReferenceAnnotationList.value = temp;
    })
    .catch(e => {
      handleError(
        e as ResponseError,
        "creating semantic structured data reference annotation",
      );
    });
}

function deleteStructuredDataReferenceAnnotation(semanticAnnotationId: number) {
  if (!props.structuredDataReference?.id) return;
  SemanticAnnotationService.deleteReferenceAnnotation({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
    referenceId: +props.structuredDataReference.id,
    semanticAnnotationId: semanticAnnotationId,
  })
    .then(() => {
      const temp = structuredDataReferenceAnnotationList.value.filter(a => {
        return a.id != semanticAnnotationId;
      });
      structuredDataReferenceAnnotationList.value = temp;
    })
    .catch(e => {
      handleError(
        e as ResponseError,
        "deleting semantic structured data reference annotation",
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
    <div class="mb-4">
      <b-button-group class="float-right">
        <b-button
          v-b-modal.edit-structured-data-reference-semantic-modal
          v-b-tooltip.hover
          title="Edit Semantic Annotation"
          variant="secondary"
        >
          <SemanticIcon />
        </b-button>
        <b-button
          v-b-modal.structured-data-reference-delete-confirmation-modal
          v-b-tooltip.hover
          class="float-right"
          title="Delete"
          variant="info"
        >
          <DeleteIcon />
        </b-button>
      </b-button-group>

      ID: {{ structuredDataReference?.id }} |
      <span v-if="structuredDataReference?.structuredDataContainerId != -1">
        <b-link
          :to="{
            name: 'Files',
            params: {
              fileId: structuredDataReference?.structuredDataContainerId,
            },
          }"
        >
          Container: {{ structuredDataReference?.structuredDataContainerId }}
        </b-link>
      </span>
      <span v-else class="text-danger">Container: Deleted</span>
      <CreatedByLine
        :created-by="structuredDataReference?.createdBy"
        :created-at="structuredDataReference?.createdAt"
      />
    </div>

    <SemanticBadge :annotation-list="structuredDataReferenceAnnotationList" />

    <b-list-group class="list">
      <b-list-group-item
        v-for="(oid, index) in structuredDataReference?.structuredDataOids"
        :key="index"
      >
        <div v-if="structuredDatas[oid]">
          <b>
            <GenericName
              :word-count="30"
              :name="structuredDatas[oid]?.structuredData?.name || ''"
            />
          </b>
          | Oid:
          {{ oid }}
          <span
            v-if="
              structuredDataReference?.structuredDataContainerId == -1 ||
              !structuredDatas[oid]?.payload
            "
          >
            | <span class="text-danger"> Deleted </span>
          </span>

          <!-- Container deleted -->
          <b-button
            v-if="structuredDataReference.structuredDataContainerId == -1"
            class="float-right"
            variant="primary"
            :disabled="true"
          >
            <EyeIcon />
          </b-button>
          <!-- Payload deleted -->
          <b-button
            v-else-if="!structuredDatas[oid]?.payload"
            class="float-right"
            variant="primary"
            :disabled="true"
          >
            <EyeIcon />
          </b-button>
          <!-- Nothing deleted -->
          <b-button
            v-else
            v-b-modal.json-editor-modal
            v-b-tooltip.hover
            class="float-right"
            variant="primary"
            title="Show Viewer"
            @click="currentStructuredDataOid = oid"
          >
            <EyeIcon />
          </b-button>
        </div>
        <div v-if="structuredDatas[oid]?.structuredData?.createdAt">
          created at:
          {{ convertDate(structuredDatas[oid]?.structuredData?.createdAt) }}
        </div>
      </b-list-group-item>
    </b-list-group>

    <JsonEditorModal
      v-if="structuredDataReference && currentStructuredDataOid"
      modal-id="json-editor-modal"
      modal-name="Structured Data Reference"
      :container-id="structuredDataReference.structuredDataContainerId"
      :oid="currentStructuredDataOid"
    />

    <SemanticAnnotationModal
      v-if="props.structuredDataReference"
      modal-id="edit-structured-data-reference-semantic-modal"
      modal-name="Add Semantic"
      :annotation-list="structuredDataReferenceAnnotationList"
      @create="createStructuredDataReferenceAnnotation($event)"
      @delete="deleteStructuredDataReferenceAnnotation($event)"
    />

    <DeleteConfirmationModal
      v-if="props.structuredDataReference"
      modal-id="structured-data-reference-delete-confirmation-modal"
      modal-name="Confirm to delete Structured Data Reference"
      :modal-text="
        'Do you really want do delete the Structured Data Reference with name ' +
        props.structuredDataReference.name +
        '?'
      "
      @confirmation="handleDelete()"
    />
  </b-modal>
</template>
