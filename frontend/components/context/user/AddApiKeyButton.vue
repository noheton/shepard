<script setup lang="ts">
import AddApiKeyDialog from "~/components/context/user/AddApiKeyDialog.vue";

defineProps<{ username: string }>();

const showCreateDialog = ref(false);
const emit = defineEmits(["created"]);
</script>

<template>
  <ExpansionPanelTitleButton
    icon="mdi-plus-circle"
    text="ADD"
    @click="showCreateDialog = true"
  />
  <AddApiKeyDialog
    v-if="showCreateDialog"
    v-model:show-dialog="showCreateDialog"
    :username="username"
    @created="emit('created')"
  />
</template>

<style scoped lang="scss"></style>
