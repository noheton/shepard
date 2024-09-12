<script setup lang="ts">
import DeleteConfirmationModal from "@/components/DeleteConfirmationModal.vue";
import GenericName from "@/components/generic/GenericName.vue";
import ApiKeyModal from "@/components/user/ApiKeyModal.vue";
import ApiKeyService from "@/services/apiKeyService";
import { handleError } from "@/utils/error-handling";
import type { ApiKey, ResponseError } from "@dlr-shepard/backend-client";
import { onMounted, ref } from "vue";

const props = defineProps({
  currentUsername: {
    type: String,
    required: true,
  },
});

const apiKeys = ref<ApiKey[]>([]);
const newName = ref("");
const createdApiKey = ref<ApiKey>();
const currentApiKey = ref<ApiKey>();

function retrieveApiKeys() {
  if (!props.currentUsername) return;
  ApiKeyService.getAllApiKeys({ username: props.currentUsername })
    .then(response => {
      apiKeys.value = response;
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching api keys");
    });
}

function handleCreate() {
  ApiKeyService.createApiKey({
    username: props.currentUsername,
    apiKey: { name: newName.value },
  })
    .then(response => {
      createdApiKey.value = response;
    })
    .catch(e => {
      handleError(e as ResponseError, "creating api key");
    })
    .finally(() => {
      retrieveApiKeys();
      newName.value = "";
    });
}

function handleDelete() {
  if (!currentApiKey.value?.uid) return;
  ApiKeyService.deleteApiKey({
    username: props.currentUsername,
    apikeyUid: currentApiKey.value.uid,
  })
    .catch(e => {
      handleError(e as ResponseError, "deleting api key");
    })
    .finally(() => {
      currentApiKey.value = undefined;
      retrieveApiKeys();
    });
}
onMounted(() => {
  retrieveApiKeys();
});
</script>

<template>
  <div>
    <b-input-group class="mb-2">
      <b-form-input
        v-model="newName"
        class="fixed-height"
        placeholder="Please enter a name for the api key you want to create"
        @keyup.enter="handleCreate()"
      ></b-form-input>
      <b-input-group-append>
        <b-button variant="primary" @click="handleCreate()"> Create </b-button>
      </b-input-group-append>
    </b-input-group>
    <div>
      <b-list-group>
        <b-list-group-item v-for="(apiKey, index) in apiKeys" :key="index">
          <b><GenericName :name="apiKey.name || ''" :word-count="60" /></b>
          UID: {{ apiKey.uid }}
          <b-button
            v-b-modal.delete-confirmation-modal
            v-b-tooltip.hover
            title="Delete"
            class="float-right"
            size="sm"
            variant="info"
            @click="currentApiKey = apiKey"
          >
            <DeleteIcon />
          </b-button>
        </b-list-group-item>
      </b-list-group>
    </div>
    <ApiKeyModal
      :created-api-key="createdApiKey"
      @ok="createdApiKey = undefined"
    />
    <DeleteConfirmationModal
      v-if="currentApiKey"
      modal-id="delete-confirmation-modal"
      modal-name="Confirm to delete api key"
      :modal-text="
        'Do you really want do delete the api key with name ' +
        currentApiKey.name +
        '?'
      "
      @confirmation="handleDelete()"
    />
  </div>
</template>
