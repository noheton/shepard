<script setup lang="ts">
const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});
const newKeyName = ref("");
const emit = defineEmits<{
  (e: "submit", name: string): void;
}>();
</script>

<template>
  <FormDialog
    v-if="showDialog"
    v-model:show-dialog="showDialog"
    title="Create Api Key"
    :submit-disabled="!newKeyName"
    save-button-text="Create"
    :close-on-submit="true"
    @submit="emit('submit', newKeyName)"
  >
    <template #form><NameInput v-model:name="newKeyName" /></template>
  </FormDialog>
</template>

<style scoped lang="scss"></style>
