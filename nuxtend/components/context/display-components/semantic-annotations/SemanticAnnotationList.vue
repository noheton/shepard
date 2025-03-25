<script setup lang="ts">
import type {
  ResponseError,
  SemanticAnnotation,
} from "@dlr-shepard/backend-client";

const props = defineProps<{ annotated: Annotated }>();

const annotations = ref(new Array<SemanticAnnotation>());

async function fetchSemanticAnnotations() {
  try {
    const anns = await props.annotated.fetchAnnotations();
    annotations.value = annotations.value.concat(anns);
  } catch (e) {
    handleError(e as ResponseError, "fetching semantic annotations");
  }
}

onMounted(fetchSemanticAnnotations);
</script>

<template>
  <ul>
    <SemanticAnnotationChip
      v-for="annotation in annotations"
      :key="annotation.propertyName"
      :property="annotation.propertyName"
      :property-iri="annotation.propertyIRI"
      :value="annotation.valueName"
      :value-iri="annotation.valueIRI"
    />
  </ul>
</template>

<style lang="scss" scoped>
ul > li {
  margin-right: 1em;
}
</style>
