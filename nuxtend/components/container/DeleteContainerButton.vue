<script lang="ts" setup>
defineProps<{ entityName: string }>();
const showDialog = ref(false);
const emit = defineEmits(["delete"]);
</script>

<template>
  <v-btn
    class="ml-3"
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
  <ConfirmSafeDeleteDialog
    v-if="showDialog"
    v-model:show-dialog="showDialog"
    :target-name="entityName"
    entity-type="container"
    @confirmed="emit('delete')"
  />
</template>

<style lang="scss" scoped></style>
