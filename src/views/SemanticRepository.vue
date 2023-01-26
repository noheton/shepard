<script setup lang="ts">
import DeleteConfirmationModal from "@/components/DeleteConfirmationModal.vue";
import CreatedByLine from "@/components/generic/CreatedByLine.vue";
import SemanticRepositoryService from "@/services/semanticRepositoriesService";
import { handleError } from "@/utils/error-handling";
import type {
  ResponseError,
  SemanticRepository,
} from "@dlr-shepard/shepard-client";
import { useTitle } from "@vueuse/core";
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue2-helpers/vue-router";

const route = useRoute();
const router = useRouter();

const currentSemanticRepository = ref<SemanticRepository>();

const deletedAlert = ref<boolean>(false);

const currentSemanticRepositoryId = computed<string>(
  () => route.params.semanticRepositoryId,
);

function retrieveSemanticRepository() {
  SemanticRepositoryService.getSemanticRepository({
    semanticRepositoryId: +currentSemanticRepositoryId.value,
  })
    .then(response => {
      currentSemanticRepository.value = response;
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching semantic repository");
    });
}

function handleDeleteSemanticRepository() {
  SemanticRepositoryService.deleteSemanticRepository({
    semanticRepositoryId: +currentSemanticRepositoryId.value,
  })
    .then(() => {
      router.push({ name: "SemanticRepositoriesList" });
    })
    .catch(e => {
      handleError(e as ResponseError, "deleting semantic repository");
    });
}

const title = computed(() => {
  return currentSemanticRepository.value?.name || "Semantic Repository";
});
function updateTitle() {
  useTitle(title, {
    titleTemplate: "%s | shepard",
  });
}

onMounted(() => {
  retrieveSemanticRepository();
  updateTitle();
});
</script>

<template>
  <div v-if="currentSemanticRepository" class="semantic-repository">
    <div class="component">
      <b-alert
        :show="deletedAlert"
        dismissible
        variant="info"
        @dismissed="deletedAlert = false"
      >
        Successfully deleted
      </b-alert>
      <b-button-group class="float-right">
        <b-button
          v-b-modal.delete-semantic-repository-confirmation-modal
          v-b-tooltip.hover
          title="Delete"
          variant="info"
        >
          <DeleteIcon />
        </b-button>
      </b-button-group>
      <h3>
        {{ currentSemanticRepository?.name }}
      </h3>
      <div class="mb-3">
        <b>ID:</b> {{ currentSemanticRepository.id }}<br />
        <b>Type:</b> {{ currentSemanticRepository.type }}<br />
        <b>Endpoint:</b>
        <b-link :href="currentSemanticRepository.endpoint">
          {{ currentSemanticRepository.endpoint }}
        </b-link>
        <br />

        <CreatedByLine
          :created-at="currentSemanticRepository.createdAt"
          :created-by="currentSemanticRepository.createdBy"
          tooltip
        />
      </div>
    </div>
    <DeleteConfirmationModal
      modal-id="delete-semantic-repository-confirmation-modal"
      modal-name="Confirm to delete semantic repository"
      :modal-text="
        'Do you really want do delete the semantic repository with name ' +
        currentSemanticRepository.name +
        '?'
      "
      @confirmation="handleDeleteSemanticRepository()"
    />
  </div>
</template>
