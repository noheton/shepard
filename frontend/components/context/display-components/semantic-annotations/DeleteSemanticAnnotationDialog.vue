<script setup lang="ts">
import type {
  ResponseError,
  SemanticAnnotation,
} from "@dlr-shepard/backend-client";

const props = defineProps<{
  annotatedElement: Annotated;
  toDelete: SemanticAnnotation;
}>();

const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});

async function deleteSemanticAnnotation() {
  try {
    await props.annotatedElement.deleteAnnotation(props.toDelete.id);
    showDialog.value = false;
    emitSuccess(
      `Successfully deleted semantic annotation "${formatSemanticAnnotation(props.toDelete.propertyName, props.toDelete.valueName)}."`,
    );
    handleAnnotationListUpdate();
  } catch (error) {
    handleError(error as ResponseError, "delete semantic annotation");
  }
}
</script>

<template>
  <ConfirmDeleteDialog
    v-model:show-dialog="showDialog"
    @confirmed="deleteSemanticAnnotation"
  />
</template>
