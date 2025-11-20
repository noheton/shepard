<script lang="ts" setup>
import type {
  ResponseError,
  SemanticAnnotation,
} from "@dlr-shepard/backend-client";

const emit = defineEmits<{
  (e: "annotations", value: SemanticAnnotation[]): void;
}>();

const props = defineProps<{ annotated: Annotated; canDelete: boolean }>();

const annotations = ref<SemanticAnnotation[]>([]);
const isLoading = ref<boolean>(true);

async function fetchSemanticAnnotations() {
  isLoading.value = true;
  try {
    annotations.value = await props.annotated.fetchAnnotations();
    emit("annotations", annotations.value);
  } catch (e) {
    handleError(e as ResponseError, "fetching semantic annotations");
  } finally {
    isLoading.value = false;
  }
}

fetchSemanticAnnotations();

onAnnotationsUpdated(fetchSemanticAnnotations);
</script>

<template>
  <div v-if="!isLoading">
    <ul v-if="annotations.length > 0">
      <SemanticAnnotationChip
        v-for="annotation in annotations"
        :key="annotation.id"
        :annotated-type="annotated"
        :annotation="annotation"
        :can-delete="canDelete"
      />
    </ul>
  </div>
  <CenteredLoadingSpinner v-else />
</template>

<style lang="scss">
ul {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
}
</style>
