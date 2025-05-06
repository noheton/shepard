<script setup lang="ts">
import { ApikeyApi } from "@dlr-shepard/backend-client";
import AddApiKeyDialog from "~/components/context/user/AddApiKeyDialog.vue";

const props = defineProps<{ username: string }>();

const showCreateDialog = ref(false);
const apikeyApi = createApiInstance(ApikeyApi);
const emit = defineEmits(["created"]);

async function createApiKey(name: string) {
  await apikeyApi.createApiKey({
    username: props.username,
    apiKey: { name: name },
  });
  emit("created");
}
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
    @submit="createApiKey"
  />
</template>

<style scoped lang="scss"></style>
