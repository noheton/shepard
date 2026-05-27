<script lang="ts" setup>
defineProps<{
  annotated: Annotated;
  /** Optional pre-fill hint for the annotation search field. Pass a channel's
   *  symbolicName (e.g. "compaction_force") and the dialog will derive the
   *  best search token automatically. */
  prefill?: string;
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
    :prefill="prefill"
  />
</template>

<style lang="scss" scoped></style>
