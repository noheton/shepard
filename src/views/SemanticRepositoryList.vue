<script setup lang="ts">
import CreateSemanticRepositoryModal from "@/components/containers/CreateSemanticRepositoryModal.vue";
import FilterListLine from "@/components/generic/FilterListLine.vue";
import GenericEntityList from "@/components/generic/GenericEntityList.vue";
import SemanticRepositoryService from "@/services/semanticRepositoriesService";
import { handleError } from "@/utils/error-handling";
import {
  getTotalRows,
  type FilterChangedData,
  type FilterOptions,
} from "@/utils/helpers";
import type {
  GetAllSemanticRepositoriesOrderByEnum,
  ResponseError,
  SemanticRepository,
  SemanticRepositoryTypeEnum,
} from "@dlr-shepard/shepard-client";
import { useStorage, useTitle } from "@vueuse/core";
import { computed, onMounted, ref } from "vue";

const repositories = ref<SemanticRepository[]>();

const filterOptions = useStorage<FilterOptions>("files-filter-options", {
  perPage: 10,
  orderBy: "createdAt",
  descending: false,
});
const currentPage = ref(1);
const totalRows = computed(() => {
  if (repositories.value)
    return getTotalRows(
      repositories.value.length,
      filterOptions.value.perPage,
      currentPage.value,
    );
  else return 0;
});

function filterChanged(options: FilterChangedData) {
  currentPage.value = options.currentPage;
  filterOptions.value.perPage = options.perPage;
  filterOptions.value.descending = options.descending;
  filterOptions.value.orderBy = options.orderBy;
  retrieveRepositories();
}

function retrieveRepositories(page?: number) {
  const nextPage = page || currentPage.value;
  const nextOrderBy = filterOptions.value
    .orderBy as keyof typeof GetAllSemanticRepositoriesOrderByEnum as GetAllSemanticRepositoriesOrderByEnum;
  SemanticRepositoryService.getAllSemanticRepositories({
    size: filterOptions.value.perPage,
    page: nextPage - 1,
    orderBy: nextOrderBy,
    orderDesc: filterOptions.value.descending,
  })
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
  <div class="view">
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

    <FilterListLine
      :max-objects="totalRows"
      :current-page="currentPage"
      :filter-options="filterOptions"
      @filter-changed="filterChanged($event)"
    />
    <GenericEntityList :entities="repositories" />
    <CreateSemanticRepositoryModal
      modal-id="create-semantic-repository-modal"
      modal-name="Create Semantic Repository"
      @create="createRepository($event)"
    />
    <b-pagination
      v-model="currentPage"
      :total-rows="totalRows"
      :per-page="filterOptions.perPage"
      align="center"
      size="sm"
      @change="retrieveRepositories($event)"
    ></b-pagination>
  </div>
</template>
