<script setup lang="ts">
import type { SemanticAnnotation } from "@dlr-shepard/backend-client";

defineProps<{
  annotation: SemanticAnnotation;
  annotatedType: Annotated;
  canDelete: boolean;
}>();
const showDeleteDialog = ref(false);
</script>

<template>
  <li>
    <v-chip
      color="primary"
      variant="outlined"
      rounded="lg"
      class="semantic-key"
    >
      <a target="_blank" :href="annotation.propertyIRI">
        {{ annotation.propertyName }}
      </a>
    </v-chip>
    <v-chip color="primary" variant="flat" rounded="lg" class="semantic-value">
      <a target="_blank" :href="annotation.valueIRI">
        {{ annotation.valueName }}
      </a>
      <template v-if="canDelete" #close>
        <v-icon icon="mdi-close-circle" @click.stop="showDeleteDialog = true" />
      </template>
    </v-chip>
  </li>
  <DeleteSemanticAnnotationDialog
    v-if="showDeleteDialog"
    v-model:show-dialog="showDeleteDialog"
    :annotated-element="annotatedType"
    :to-delete="annotation"
  />
</template>

<style lang="scss" scoped>
li {
  list-style-type: none;
  display: flex;
}
.semantic-key {
  border-top-right-radius: 0 !important;
  border-bottom-right-radius: 0 !important;
}
.semantic-value {
  border-top-left-radius: 0 !important;
  border-bottom-left-radius: 0 !important;
}
a {
  color: inherit;
  text-decoration: none;
  &:hover {
    text-decoration: underline;
  }
}
</style>
