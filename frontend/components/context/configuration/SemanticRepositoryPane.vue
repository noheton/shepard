<script setup lang="ts">
import {
  SemanticRepositoryApi,
  type SemanticRepository,
} from "@dlr-shepard/backend-client";
import { ConfigurationFragments } from "~/components/context/configuration/configurationMenuItems";
import { useShepardApi } from "~/composables/common/api/useShepardApi";
import { useFetchSemanticRepositories } from "~/composables/context/useFetchSemanticRepositories";
import { useTermSearch } from "~/composables/context/useTermSearch";
import { handleSemanticRepositoryListUpdate } from "~/utils/resourceUpdateBus";

const showCreateDialog = ref(false);

const headers = [
  { title: "ID", key: "id", sortable: true, width: "20%" },
  { title: "Name", key: "name", sortable: true },
  { title: "URL", key: "endpoint", sortable: true },
  { title: "Created at", key: "createdAt", sortable: true },
  {
    title: "",
    value: "actions",
  },
];

const { repositories: repositoryList, isLoading } =
  useFetchSemanticRepositories();

const { search } = useTermSearch();
const noLabelsWarning = ref(false);

watch(
  [repositoryList, isLoading],
  async ([repos, loading]) => {
    if (loading || repos.length === 0) {
      noLabelsWarning.value = false;
      return;
    }
    // Only probe when an INTERNAL repository is present.
    // The TypeScript client predates the INTERNAL type, so cast to any.
    if (!repos.some(r => (r as any).type === "INTERNAL")) return;
    const results = await search("aa", 1);
    noLabelsWarning.value = results.length === 0;
  },
  { immediate: true },
);

const semanticRepositoryToDelete = ref<SemanticRepository | undefined>(
  undefined,
);
const showSemanticRepositoryDeleteConfirmDialog = ref<boolean>(false);

const deleteSemanticRepository = (semanticRepository: SemanticRepository) => {
  semanticRepositoryToDelete.value = semanticRepository;
  showSemanticRepositoryDeleteConfirmDialog.value = true;
};

async function onDelete() {
  if (!semanticRepositoryToDelete.value) return;
  useShepardApi(SemanticRepositoryApi)
    .value.deleteSemanticRepository({
      semanticRepositoryId: semanticRepositoryToDelete.value.id,
    })
    .then(_ => {
      emitSuccess(
        `Successfully deleted semantic repository "${semanticRepositoryToDelete.value?.id}"`,
      );
      showSemanticRepositoryDeleteConfirmDialog.value = false;
      semanticRepositoryToDelete.value = undefined;
      handleSemanticRepositoryListUpdate();
    })
    .catch(error => {
      handleError(error, "deleteSemanticRepository");
    });
}
</script>

<template>
  <div
    :id="ConfigurationFragments.SEMANTIC_REPOSITORIES"
    class="d-flex flex-column"
  >
    <ConfigurationPane
      title="Semantic Repositories"
      add-button-text="Create Sematic Repository"
      show-tooltip
      @show-create-dialog="showCreateDialog = true"
    >
      <template #tooltip-content>
        <p>
          A semantic repository provides terms from ontologies (i.e. an ontology
          SPARQL endpoint).
          <br />
          These terms are used for semantic annotations to build up semantic
          knowledge in Shepard.
        </p>
      </template>

      <template #create-dialog>
        <CreateSemanticRepositoryDialog
          v-if="showCreateDialog"
          v-model:show-dialog="showCreateDialog"
          @semantic-repository-created="handleSemanticRepositoryListUpdate"
        />
      </template>
      <template #table>
        <v-alert
          v-if="noLabelsWarning"
          type="warning"
          density="compact"
          variant="tonal"
          class="mx-4 mb-3"
          icon="mdi-label-off-outline"
          title="No searchable labels found"
          text="The internal semantic repository has no human-readable labels indexed. Ontology data may not be loaded, or the imported RDF lacks rdfs:label / skos:prefLabel properties. Annotation term search will return empty results until this is resolved."
        />
        <DataTable
          class="pt-4"
          :cell-props="{
            class: 'text-textbody1',
          }"
          :header-props="{
            class: 'text-subtitle-2 text-textbody1',
            style: 'background-color: rgb(var(--v-theme-divider2))',
          }"
          :headers="headers"
          :items="repositoryList"
          :loading="isLoading"
          :items-per-page="-1"
          :hide-default-footer="true"
        >
          <template #[`item.name`]="{ item }: { item: SemanticRepository }">
            <span class="text-textbody">{{ item.name }}</span>
          </template>
          <template #[`item.id`]="{ item }: { item: SemanticRepository }">
            <span class="text-textbody">#{{ item.id }}</span>
          </template>
          <template #[`item.url`]="{ item }: { item: SemanticRepository }">
            <TextLink
              :text="item.endpoint"
              :to="item.endpoint"
              target="_blank"
            />
          </template>
          <template
            #[`item.createdAt`]="{ item }: { item: SemanticRepository }"
          >
            <div class="d-flex flex-column">
              <span class="text-textbody">
                {{ item.createdAt ? toShortDateString(item.createdAt) : "-" }}
              </span>
              <span v-if="item.createdBy" class="text-textbody2">
                by {{ item.createdBy }}
              </span>
            </div>
          </template>
          <template #[`item.actions`]="{ item }: { item: SemanticRepository }">
            <ActionContainer>
              <ActionButton
                icon="mdi-delete-outline"
                @click="deleteSemanticRepository(item)"
              />
            </ActionContainer>
          </template>
          <template #bottom>
            <v-divider :thickness="8" color="divider2" opacity="1" />
          </template>
        </DataTable>
      </template>
      <template #confirmation-dialog>
        <ConfirmDeleteDialog
          v-if="
            showSemanticRepositoryDeleteConfirmDialog &&
            semanticRepositoryToDelete
          "
          v-model:show-dialog="showSemanticRepositoryDeleteConfirmDialog"
          @confirmed="onDelete"
        />
      </template>
    </ConfigurationPane>
  </div>
</template>
