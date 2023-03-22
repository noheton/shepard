<script setup lang="ts">
import SemanticAnnotationModal from "@/components/dataobjects/SemanticAnnotationModal.vue";
import DeleteConfirmationModal from "@/components/DeleteConfirmationModal.vue";
import CreatedByLine from "@/components/generic/CreatedByLine.vue";
import SemanticBadge from "@/components/generic/SemanticBadge.vue";
import SemanticAnnotationService from "@/services/semanticAnnotationService";
import { handleError } from "@/utils/error-handling";
import { removeQueryParam, setQueryParam } from "@/utils/helpers";
import type {
  BasicReference,
  ResponseError,
  SemanticAnnotation,
} from "@dlr-shepard/shepard-client";
import { getCurrentInstance, onMounted, ref, type PropType } from "vue";

const props = defineProps({
  modalId: {
    type: String,
    default: "basic-reference-modal",
  },
  currentCollectionId: {
    type: Number,
    required: true,
  },
  currentDataObjectId: {
    type: Number,
    required: true,
  },
  reference: {
    type: Object as PropType<BasicReference>,
    required: true,
  },
});

const vm = getCurrentInstance();

const emit = defineEmits(["delete-reference"]);

const referenceAnnotationList = ref<SemanticAnnotation[]>();
function getAllReferenceAnnotations() {
  if (!props.reference?.id) return;
  SemanticAnnotationService.getAllReferenceAnnotations({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
    referenceId: props.reference.id,
  })
    .then(annotationList => {
      referenceAnnotationList.value = annotationList;
    })
    .catch(e => {
      handleError(e as ResponseError, "get all semantic reference annotations");
    });
}

function createReferenceAnnotation(semanticAnnotation: SemanticAnnotation) {
  if (!props.reference.id) return;
  SemanticAnnotationService.createReferenceAnnotation({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
    referenceId: props.reference.id,
    semanticAnnotation: semanticAnnotation,
  })
    .then(newAnnotation => {
      const temp = [...(referenceAnnotationList.value || []), newAnnotation];
      referenceAnnotationList.value = temp;
    })
    .catch(e => {
      handleError(e as ResponseError, "creating semantic reference annotation");
    });
}

function deleteReferenceAnnotation(semanticAnnotationId: number) {
  if (!props.reference.id) return;
  SemanticAnnotationService.deleteReferenceAnnotation({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
    referenceId: props.reference.id,
    semanticAnnotationId: semanticAnnotationId,
  })
    .then(() => {
      if (!referenceAnnotationList.value) return;
      const temp = referenceAnnotationList.value.filter(a => {
        return a.id != semanticAnnotationId;
      });
      referenceAnnotationList.value = temp;
    })
    .catch(e => {
      handleError(e as ResponseError, "deleting semantic reference annotation");
    });
}

function handleDelete() {
  emit("delete-reference");
  vm?.proxy.$bvModal.hide(props.modalId);
}

onMounted(() => {
  getAllReferenceAnnotations();
});
</script>

<template>
  <b-modal
    :id="modalId"
    :title="reference.type"
    size="lg"
    lazy
    ok-only
    ok-variant="secondary"
    ok-title="Close"
    @hidden="removeQueryParam('referenceId')"
    @show="setQueryParam('referenceId', String(props.reference.id))"
  >
    <div class="mb-4">
      <b-button-group class="float-right">
        <b-button
          v-b-modal.edit-semantic-reference-modal
          v-b-tooltip.hover
          title="Edit Semantic Annotation"
          variant="secondary"
        >
          <SemanticIcon />
        </b-button>
        <b-button
          v-b-modal.reference-delete-confirmation-modal
          v-b-tooltip.hover
          class="float-right"
          title="Delete"
          variant="info"
        >
          <DeleteIcon />
        </b-button>
      </b-button-group>

      <b>{{ reference?.name }}</b>
      | ID: {{ reference?.id }}
      <CreatedByLine
        :created-by="reference?.createdBy"
        :created-at="reference?.createdAt"
      />
    </div>

    <SemanticBadge
      v-if="referenceAnnotationList"
      :annotation-list="referenceAnnotationList"
    />
    <slot></slot>

    <SemanticAnnotationModal
      v-if="props.reference"
      modal-id="edit-semantic-reference-modal"
      modal-name="Edit semantic annotations"
      :annotation-list="referenceAnnotationList"
      @create="createReferenceAnnotation($event)"
      @delete="deleteReferenceAnnotation($event)"
    />

    <DeleteConfirmationModal
      v-if="props.reference"
      modal-id="reference-delete-confirmation-modal"
      modal-name="Confirm to delete the reference"
      :modal-text="
        'Do you really want do delete the reference with name ' +
        props.reference.name +
        '?'
      "
      @confirmation="handleDelete()"
    />
  </b-modal>
</template>
