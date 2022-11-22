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
    <ApiKeyModal :created-api-key="createdApiKey" @ok="handleCreated()" />
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

<script lang="ts">
import DeleteConfirmationModal from "@/components/DeleteConfirmationModal.vue";
import GenericName from "@/components/generic/GenericName.vue";
import ApiKeyModal from "@/components/user/ApiKeyModal.vue";
import ApiKeyService from "@/services/apiKeyService";
import { handleError } from "@/utils/error-handling";
import type {
  ApiKey,
  ApiKeyWithJWT,
  ResponseError,
} from "@dlr-shepard/shepard-client";
import { defineComponent } from "vue";

interface ApiKeyListData {
  apiKeys: ApiKey[];
  newName: string;
  createdApiKey?: ApiKeyWithJWT;
  currentApiKey?: ApiKey;
}

export default defineComponent({
  components: {
    DeleteConfirmationModal,
    ApiKeyModal,
    GenericName,
  },
  props: {
    currentUsername: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      apiKeys: new Array<ApiKey>(),
      newName: "",
      createdApiKey: undefined,
      currentApiKey: undefined,
    } as ApiKeyListData;
  },
  mounted() {
    this.retrieveApiKeys();
  },
  methods: {
    retrieveApiKeys() {
      if (!this.currentUsername) return;
      ApiKeyService.getAllApiKeys({ username: this.currentUsername })
        .then(response => {
          this.apiKeys = response;
        })
        .catch(e => {
          handleError(e as ResponseError, "fetching api keys");
        });
    },
    handleCreate() {
      ApiKeyService.createApiKey({
        username: this.currentUsername,
        apiKey: { name: this.newName },
      })
        .then(response => {
          this.createdApiKey = response;
        })
        .catch(e => {
          handleError(e as ResponseError, "creating api key");
        })
        .finally(() => {
          this.retrieveApiKeys();
          this.newName = "";
        });
    },
    handleCreated() {
      this.createdApiKey = undefined;
    },
    handleDelete() {
      if (!this.currentApiKey?.uid) return;
      ApiKeyService.deleteApiKey({
        username: this.currentUsername,
        apikeyUid: this.currentApiKey.uid,
      })
        .catch(e => {
          handleError(e as ResponseError, "deleting api key");
        })
        .finally(() => {
          this.currentApiKey = undefined;
          this.retrieveApiKeys();
        });
    },
  },
});
</script>

<style scoped>
.fixed-height {
  height: 40px;
}
</style>
