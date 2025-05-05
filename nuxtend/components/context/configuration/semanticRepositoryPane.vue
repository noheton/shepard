<script setup lang="ts">
import type { SemanticRepository } from "@dlr-shepard/backend-client";
import { useFetchSemanticRepositories } from "~/composables/context/useFetchSemanticRepositories";
import { ConfigurationFragments } from "./configurationMenuItems";

const headers = [
  { title: "ID", key: "id", sortable: true, width: "20%" },
  { title: "Name", key: "name", sortable: true, width: "40%" },
  { title: "Created at", key: "createdAt", sortable: true },
  {
    title: "",
    value: "actions",
  },
];

const { repositories: repositoryList, isLoading } =
  useFetchSemanticRepositories();
</script>

<template>
  <div
    :id="ConfigurationFragments.SEMANTIC_REPOSITORIES"
    class="d-flex flex-column"
  >
    <div class="d-flex align-center ga-4">
      <h4 class="text-h4">Semantic Repositories</h4>
      <Tooltip>
        <p>
          A semantic repository provides terms from ontologies (i.e. an ontology
          SPARQL endpoint).
          <br />
          These terms are used for semantic annotations to build up semantic
          knowledge in Shepard.
        </p>
      </Tooltip>
    </div>

    <DataTable
      class="pt-8"
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
      hover
    >
      <template #[`item.name`]="{ item }: { item: SemanticRepository }">
        <span class="text-textbody">{{ item.name }}</span>
      </template>
      <template #[`item.id`]="{ item }: { item: SemanticRepository }">
        <span class="text-textbody">#{{ item.id }}</span>
      </template>
      <template #[`item.createdAt`]="{ item }: { item: SemanticRepository }">
        <div class="d-flex flex-column">
          <span class="text-textbody">
            {{ item.createdAt ? toShortDateString(item.createdAt) : "-" }}
          </span>
          <span v-if="item.createdBy" class="text-textbody2">
            by {{ item.createdBy }}
          </span>
        </div>
      </template>

      <template #bottom>
        <v-divider :thickness="8" color="divider2" opacity="1" />
      </template>
    </DataTable>
  </div>
</template>
