<script setup lang="ts">
import {
  ApikeyApi,
  type ApiKeyWithJWT,
  type ResponseError,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "~/composables/common/api/useShepardApi";

const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});

const emit = defineEmits(["created"]);

const props = defineProps<{ username: string }>();

const newKeyName = ref("");

const title = "Create Api Key";
const currentStep = ref(1);
const steps = ["Create Api Key", "View Api key"];
const createButtonDisabled = computed(() => !newKeyName.value);

const apikeyApi = useShepardApi(ApikeyApi);

const createdKey = ref<ApiKeyWithJWT>();
const dialogDisabled = ref(false);

async function createApiKey() {
  try {
    dialogDisabled.value = true;
    createdKey.value = await apikeyApi.value.createApiKey({
      username: props.username,
      apiKey: { name: newKeyName.value },
    });
    emit("created");
    currentStep.value = 2;
  } catch (e) {
    handleError(e as ResponseError, "creating api key");
  } finally {
    dialogDisabled.value = false;
  }
}

function copyToClipboard() {
  navigator.clipboard.writeText(createdKey.value!.jwt);
  emitSuccess(`Copied api key to clipboard!`);
}
</script>

<template>
  <v-dialog v-model="showDialog" persistent max-width="800">
    <v-card color="canvas">
      <template #title>
        <div class="d-flex justify-space-between align-baseline">
          <div class="text-h4">{{ title }}</div>
          <v-btn variant="plain" icon="mdi-close" @click="showDialog = false" />
        </div>
      </template>
      <template #text>
        <v-stepper
          v-model:model-value="currentStep"
          class="d-flex flex-column"
          min-height="426px"
          :items="steps"
          selected-class="selected-step"
          bg-color="canvas"
          flat
        >
          <template #[`item.1`]>
            <NameInput class="mt-8" v-model:name="newKeyName" />
          </template>

          <template #[`item.2`]>
            <div>
              Api key has been created. Note that for security reasons the api
              key will not be accessible later on so note it down!
            </div>
            <div class="mt-6 api-key">
              <code>
                {{ createdKey!.jwt }}
              </code>
            </div>
          </template>
          <v-spacer />
          <template #actions>
            <div class="d-flex px-6 mt-12">
              <v-spacer />
              <v-btn
                v-if="currentStep === 1"
                :disabled="dialogDisabled"
                variant="flat"
                color="treeview"
                class="mr-4"
                @click="showDialog = false"
              >
                Cancel
              </v-btn>
              <v-btn
                v-if="currentStep === 1"
                :disabled="createButtonDisabled || dialogDisabled"
                variant="flat"
                color="primary"
                @click="createApiKey"
              >
                Create
              </v-btn>
              <div v-else>
                <v-btn
                  class="mr-4"
                  prepend-icon="mdi-clipboard-multiple-outline"
                  variant="flat"
                  color="primary"
                  @click="copyToClipboard"
                >
                  <template #prepend><v-icon /></template>
                  Clipboard
                </v-btn>
                <v-btn
                  color="primary"
                  variant="flat"
                  @click="showDialog = false"
                >
                  Close
                </v-btn>
              </div>
            </div>
          </template>
        </v-stepper>
      </template>
    </v-card>
  </v-dialog>
</template>

<style lang="scss" scoped>
.v-dialog {
  :deep(.v-stepper-header) {
    padding-left: 24px;
    padding-right: 24px;
    box-shadow: unset;
    background-color: rgb(var(--v-theme-divider2));
  }
  :deep(.v-divider) {
    opacity: 1;
    color: rgb(var(--v-theme-divider1));
  }

  :deep(.v-overlay__content > .v-card > .v-card-text) {
    padding-left: 0;
    padding-right: 0;
  }

  :deep(.selected-step) {
    opacity: 1;
    color: rgb(var(--v-theme-textbody1));
    .v-stepper-item__avatar {
      background-color: rgb(var(--v-theme-primary));
    }
  }

  :deep(.v-btn--disabled.back-btn) {
    .v-btn__content {
      color: rgb(var(--v-theme-low-emphasis));
    }
    .v-btn__overlay {
      color: rgb(var(--v-theme-divider2));
    }
  }
}

.api-key {
  background-color: rgb(var(--v-theme-divider2));
}
</style>
