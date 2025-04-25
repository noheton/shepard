<script lang="ts" setup>
import type {
  ResponseError,
  SemanticAnnotation,
} from "@dlr-shepard/backend-client";

const emit = defineEmits<{
  (e: "annotations", value: SemanticAnnotation[]): void;
}>();

const props = defineProps<{ annotated: Annotated }>();

const annotations = ref<SemanticAnnotation[]>([]);

async function fetchSemanticAnnotations() {
  try {
    annotations.value = await props.annotated.fetchAnnotations();
    emit("annotations", annotations.value);
  } catch (e) {
    handleError(e as ResponseError, "fetching semantic annotations");
  }
}

fetchSemanticAnnotations();

onAnnotationsUpdated(fetchSemanticAnnotations);
</script>

<template>
  <ul v-if="annotations.length > 0">
    <SemanticAnnotationChip
      v-for="annotation in annotations"
      :key="annotation.id"
      :annotated-type="annotated"
      :annotation="annotation"
    />
  </ul>
</template>

<style lang="scss">
ul {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
}
</style>
