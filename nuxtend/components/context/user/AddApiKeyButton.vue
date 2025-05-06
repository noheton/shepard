<script setup lang="ts">
import { ApikeyApi, type ResponseError } from "@dlr-shepard/backend-client";
import AddApiKeyDialog from "~/components/context/user/AddApiKeyDialog.vue";

const props = defineProps<{ username: string }>();

const showCreateDialog = ref(false);
const apikeyApi = createApiInstance(ApikeyApi);
const emit = defineEmits(["created"]);

async function createApiKey(name: string) {
  try {
    await apikeyApi.createApiKey({
      username: props.username,
      apiKey: { name: name },
    });
    emit("created");
  } catch (e) {
    handleError(e as ResponseError, "creating api key");
  }
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
