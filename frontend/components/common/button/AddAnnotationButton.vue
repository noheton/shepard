<script lang="ts" setup>
const props = defineProps<{
  annotated: Annotated;
  /** Optional pre-fill hint for the annotation search field. Pass a channel's
   *  symbolicName (e.g. "compaction_force") and the dialog will derive the
   *  best search token automatically. */
  prefill?: string;
  /** Optional vocabulary filter passed through to AddAnnotationDialog. When
   *  set, term suggestions are narrowed to those whose URI contains this
   *  string (e.g. "qudt" restricts to QUDT unit terms). */
  filterVocab?: string;
  /** Override the trigger button icon (default: mdi-plus-circle). */
  buttonIcon?: string;
  /** Override the trigger button label text (default: ADD). */
  buttonText?: string;
}>();
const showCreateAnnotationDialog = ref(false);
</script>

<template>
  <ExpansionPanelTitleButton
    :icon="props.buttonIcon ?? 'mdi-plus-circle'"
    :text="props.buttonText ?? 'ADD'"
    @click="showCreateAnnotationDialog = true"
  />
  <AddAnnotationDialog
    v-if="showCreateAnnotationDialog"
    v-model:show-dialog="showCreateAnnotationDialog"
    :annotated="annotated"
    :prefill="prefill"
    :filter-vocab="filterVocab"
  />
</template>

<style lang="scss" scoped></style>
