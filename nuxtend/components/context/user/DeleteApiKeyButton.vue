<script setup lang="ts">
import { ApikeyApi } from "@dlr-shepard/backend-client";

const props = defineProps<{
  username: string;
  apikeyUid: string;
}>();
const showDeleteDialog = ref(false);
const emit = defineEmits(["deleted"]);

const apikeyApi = createApiInstance(ApikeyApi);

async function deleteApiKey() {
  await apikeyApi.deleteApiKey({
    username: props.username,
    apikeyUid: props.apikeyUid,
  });
  emit("deleted");
}
</script>

<template>
  <ActionButton icon="mdi-delete-outline" @click="showDeleteDialog = true" />
  <ConfirmDeleteDialog
    v-if="showDeleteDialog"
    v-model:show-dialog="showDeleteDialog"
    @confirmed="deleteApiKey"
  />
</template>

<style scoped lang="scss"></style>
