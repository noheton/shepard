<script setup lang="ts">
import {
  SemanticRepositoryApi,
  SemanticRepositoryType,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "~/composables/common/api/useShepardApi";

const isValid = ref(true);
const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});
const semanticRepositoryName = ref<string>("");
const endpoint = ref<string>("");
const semanticRepositoryType = ref<SemanticRepositoryType>(
  SemanticRepositoryType.Sparql,
);
const emit = defineEmits(["semantic-repository-created"]);

async function createSemanticRepository() {
  useShepardApi(SemanticRepositoryApi)
    .value.createSemanticRepository({
      semanticRepository: {
        name: semanticRepositoryName.value,
        type: semanticRepositoryType.value,
        endpoint: endpoint.value,
      },
    })
    .then(response => {
      emitSuccess(
        `Successfully created semantic repository "${response.name}"`,
      );
      emit("semantic-repository-created");
      showDialog.value = false;
    })
    .catch(error => {
      handleError(error, "createSemanticRepository");
    });
}
</script>

<template>
  <FormDialog
    v-model:show-dialog="showDialog"
    :max-width="800"
    title="Create Semantic Repository"
    :submit-disabled="!isValid"
    save-button-text="Save"
    @submit="createSemanticRepository"
  >
    <template #form>
      <v-form ref="form" v-model="isValid">
        <v-row class="pt-9 pb-1">
          <v-col>
            <NameInput v-model:name="semanticRepositoryName" />
          </v-col>
        </v-row>
        <v-row>
          <v-col>
            <SemanticRepositoryTypeSelect
              v-model:semantic-repository-type="semanticRepositoryType"
            />
          </v-col>
        </v-row>
        <v-row>
          <v-col>
            <EndpointInput v-model:endpoint="endpoint" />
          </v-col>
        </v-row>
      </v-form>
    </template>
  </FormDialog>
</template>
