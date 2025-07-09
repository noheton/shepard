<script lang="ts" setup>
defineProps<{ annotated: Annotated }>();
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
  />
</template>

<style lang="scss" scoped></style>
