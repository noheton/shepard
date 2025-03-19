<script setup lang="ts">
import {
  SemanticAnnotationApi,
  type ResponseError,
  type SemanticAnnotation,
} from "@dlr-shepard/backend-client";

const props = defineProps<{ collectionId: number }>();

const api = createApiInstance(SemanticAnnotationApi);

const annotations = ref(new Array<SemanticAnnotation>());

async function fetchSemanticAnnotations(collectionId: number) {
  try {
    const anns = await api.getAllCollectionAnnotations({
      collectionId: collectionId,
    });
    annotations.value = annotations.value.concat(anns);
  } catch (e) {
    handleError(e as ResponseError, "fetching collection roles");
  }
}

onMounted(() => fetchSemanticAnnotations(props.collectionId));
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
