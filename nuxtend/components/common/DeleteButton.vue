<script lang="ts" setup>
const showDialog = ref(false);
const emit = defineEmits(["delete"]);
</script>

<template>
  <v-btn
    class="mx-2"
    color="treeview"
    icon
    rounded="lg"
    size="small"
    variant="flat"
    @click="showDialog = true"
  >
    <v-icon>mdi-delete-outline</v-icon>
    <v-tooltip activator="parent" location="top">Delete Container</v-tooltip>
  </v-btn>
  <ConfirmDeleteDialog
    v-if="showDialog"
    v-model:show-dialog="showDialog"
    @confirmed="emit('delete')"
  />
</template>

<style lang="scss" scoped></style>
