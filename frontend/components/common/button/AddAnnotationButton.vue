<script lang="ts" setup>
defineProps<{
  annotated: Annotated;
  /** Forwarded to AddAnnotationDialog to pre-fill the property search. */
  propertyHint?: string;
}>();
const showCreateAnnotationDialog = ref(false);
</script>

<template>
  <ExpansionPanelTitleButton
    icon="mdi-plus-circle"
    text="ADD"
    @click="showCreateAnnotationDialog = true"
  />
  <AddAnnotationDialog
    v-if="showCreateAnnotationDialog"
    v-model:show-dialog="showCreateAnnotationDialog"
    :annotated="annotated"
    :property-hint="propertyHint"
  />
</template>

<style lang="scss" scoped></style>
