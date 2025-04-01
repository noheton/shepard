<script setup lang="ts">
import type { SemanticAnnotation } from "@dlr-shepard/backend-client";

interface SemanticAnnotationChipProps {
  annotation: SemanticAnnotation;
  annotatedType: Annotated;
}
const props = defineProps<SemanticAnnotationChipProps>();
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
      <a target="_blank" :href="props.annotation.propertyIRI">
        {{ props.annotation.propertyName }}
      </a>
    </v-chip>
    <v-chip color="primary" variant="flat" rounded="lg" class="semantic-value">
      <a target="_blank" :href="props.annotation.valueIRI">
        {{ props.annotation.valueName }}
      </a>
      <template #close>
        <v-icon
          icon="mdi-close-circle"
          @click.stop="
            () => {
              showDeleteDialog = true;
            }
          "
        />
      </template>
    </v-chip>
  </li>
  <DeleteSemanticAnnotationDialog
    v-model:show-dialog="showDeleteDialog"
    :annotated-element="annotatedType"
    :to-delete="annotation"
  />
</template>

<style lang="scss" scoped>
li {
  display: inline-block;
  margin-right: 16px;
  margin-bottom: 8px;
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
