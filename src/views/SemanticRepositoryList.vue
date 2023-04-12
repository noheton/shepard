<script setup lang="ts">
import CreateSemanticRepositoryModal from "@/components/containers/CreateSemanticRepositoryModal.vue";
import GenericEntityList from "@/components/generic/GenericEntityList.vue";
import SemanticRepositoryService from "@/services/semanticRepositoriesService";
import { handleError } from "@/utils/error-handling";
import type {
  ResponseError,
  SemanticRepository,
  SemanticRepositoryTypeEnum,
} from "@dlr-shepard/shepard-client";
import { useTitle } from "@vueuse/core";
import { onMounted, ref } from "vue";

const repositories = ref<SemanticRepository[]>();

function retrieveRepositories() {
  SemanticRepositoryService.getAllSemanticRepositories()
    .then(response => {
      repositories.value = response;
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching semantic repositories");
    });
}

function createRepository(options: {
  name: string | null;
  type: SemanticRepositoryTypeEnum;
  endpoint: string;
}) {
  SemanticRepositoryService.createSemanticRepository({
    semanticRepository: {
      name: options.name,
      type: options.type,
      endpoint: options.endpoint,
    },
  })
    .then(() => {
      retrieveRepositories();
    })
    .catch(e => {
      handleError(e as ResponseError, "creating semantic repositories");
    });
}

onMounted(() => {
  retrieveRepositories();
  useTitle("Semantic Repositories | shepard");
});
</script>

<template>
  <div class="semantic-repository-list">
    <div class="component">
      <b-button-group class="float-right">
        <b-button
          v-b-modal.create-semantic-repository-modal
          v-b-tooltip.hover
          title="Create Semantic Repository"
          variant="primary"
        >
          <CreateIcon />
        </b-button>
      </b-button-group>
      <h4>Semantic Repositories</h4>
      <br />

      <GenericEntityList :entities="repositories" />
      <CreateSemanticRepositoryModal
        modal-id="create-semantic-repository-modal"
        modal-name="Create Semantic Repository"
        @create="createRepository($event)"
      />
    </div>
  </div>
</template>
