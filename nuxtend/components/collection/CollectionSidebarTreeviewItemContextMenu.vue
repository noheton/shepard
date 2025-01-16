<script setup lang="ts">
defineProps<{
  deleteItem: () => Promise<void>;
}>();
</script>

<template>
  <v-menu class="pa-0">
    <template #activator="{ props }">
      <v-btn
        icon="mdi-dots-horizontal"
        v-bind="props"
        variant="plain"
        density="compact"
        color="primary"
      />
    </template>
    <v-list>
      <v-list-item color="canvas" density="compact">
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
      </v-list-item>
    </v-list>
  </v-menu>
</template>

<style lang="scss" scoped>
.v-list {
  padding-top: 0;
  padding-bottom: 0;

  :deep(.v-list-item) {
    padding: 0;
  }
}
</style>
