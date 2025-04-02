<script setup lang="ts">
import type {
  ResponseError,
  SemanticAnnotation,
} from "@dlr-shepard/backend-client";

const props = defineProps<{ annotated: Annotated }>();

const annotations = ref<SemanticAnnotation[]>([]);

async function fetchSemanticAnnotations() {
  try {
    annotations.value = await props.annotated.fetchAnnotations();
  } catch (e) {
    handleError(e as ResponseError, "fetching semantic annotations");
  }
}

fetchSemanticAnnotations();

onAnnotationsUpdated(fetchSemanticAnnotations);
</script>

<template>
  <ul>
    <SemanticAnnotationChip
      v-for="annotation in annotations"
      :key="annotation.id"
      :annotated-type="annotated"
      :annotation="annotation"
    />
  </ul>
</template>
