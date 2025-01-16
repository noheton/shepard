<script setup lang="ts">
defineProps<{
  deleteItem: () => Promise<void>;
}>();
</script>

<template>
  <CommonConfirmationDialog
    prompt-text="Are you sure you want to delete this item?"
    confirm-button-text="Delete"
    @confirmed="deleteItem()"
  >
    <v-btn
      class="text-textbody1 text-body-1"
      prepend-icon="mdi-delete-outline"
      text="Delete"
      color="canvas"
    />
  </CommonConfirmationDialog>
</template>
