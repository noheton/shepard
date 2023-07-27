<script setup lang="ts">
import FilterListLine from "@/components/generic/FilterListLine.vue";
import GenericCreateModal from "@/components/generic/GenericCreateModal.vue";
import GenericEntityList from "@/components/generic/GenericEntityList.vue";
import { useSearchContainers } from "@/components/search/InlineSearchContainers";
import FileService from "@/services/fileService";
import { handleError } from "@/utils/error-handling";
import {
  getTotalRows,
  type FilterChangedData,
  type FilterOptions,
} from "@/utils/helpers";
import type {
  FileContainer,
  GetAllFileContainersOrderByEnum,
  PermissionsPermissionTypeEnum,
  ResponseError,
} from "@dlr-shepard/shepard-client";
import { refDebounced, useStorage, useTitle } from "@vueuse/core";
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue2-helpers/vue-router";

const router = useRouter();

const containers = ref<FileContainer[] | undefined>();

const filterOptions = useStorage<FilterOptions>("files-filter-options", {
  perPage: 10,
  orderBy: "createdAt",
  descending: false,
});
const currentPage = ref(1);
const totalRows = computed(() => {
  if (containers.value)
    return getTotalRows(
      containers.value.length,
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
  retrieveContainers();
}

function retrieveContainers(page?: number) {
  const nextPage = page || currentPage.value;
  const nextOrderBy = filterOptions.value
    .orderBy as keyof typeof GetAllFileContainersOrderByEnum as GetAllFileContainersOrderByEnum;
  FileService.getAllFileContainers({
    size: filterOptions.value.perPage,
    page: nextPage - 1,
    orderBy: nextOrderBy,
    orderDesc: filterOptions.value.descending,
  })
    .then(response => {
      containers.value = response;
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching file containers");
    });
}

function createContainer(options: {
  name: string;
  perms: PermissionsPermissionTypeEnum;
}) {
  FileService.createFileContainer({
    fileContainer: { name: options.name },
  })
    .then(async response => {
      if (response.id) {
        const perms = await FileService.getFilePermissions({
          fileContainerId: response.id,
        });
        perms.permissionType = options.perms;
        await FileService.editFilePermissions({
          fileContainerId: response.id,
          permissions: perms,
        });
        router.push({
          name: "Files",
          params: {
            fileId: String(response.id),
          },
        });
      }
    })
    .catch(e => {
      handleError(e as ResponseError, "creating file container");
    });
}

const userInput = ref("");
const userInputDebounced = refDebounced(userInput, 700);

const { results, totalResults, searchQuery } = useSearchContainers(
  userInputDebounced,
  "FILE",
);

const searchRoute = computed(() => {
  const route = router.resolve("Search").route;
  route.query.queryType = "FILE";
  route.query.searchQuery = searchQuery.value;
  return route;
});

onMounted(() => {
  retrieveContainers();
  useTitle("File Containers | shepard");
});
</script>

<template>
  <div class="view">
    <b-button-group class="float-right">
      <b-button
        v-b-modal.create-file-container-modal
        v-b-tooltip.hover
        title="Create File Container"
        variant="primary"
      >
        <CreateIcon />
      </b-button>
    </b-button-group>
    <h4>File Containers</h4>
    <br />

    <b-form-input
      id="userFormInput"
      v-model="userInput"
      class="mb-3"
      placeholder="Name, Username, ID or Description"
    ></b-form-input>

    <b-popover
      custom-class="wide-popover"
      target="userFormInput"
      triggers="focus"
      placement="bottom"
    >
      <template #title>
        Result Set ({{ totalResults }} total)
        <b-link class="float-right font-weight-normal" :to="searchRoute">
          Advanced Search
        </b-link>
      </template>
      <GenericEntityList :entities="results" />
    </b-popover>

    <FilterListLine
      :max-objects="totalRows"
      :current-page="currentPage"
      :filter-options="filterOptions"
      @filter-changed="filterChanged($event)"
    />
    <GenericEntityList :entities="containers" />
    <GenericCreateModal
      modal-id="create-file-container-modal"
      modal-name="Create File Container"
      @create="createContainer($event)"
    />
    <b-pagination
      v-model="currentPage"
      :total-rows="totalRows"
      :per-page="filterOptions.perPage"
      align="center"
      size="sm"
      @change="retrieveContainers($event)"
    ></b-pagination>
  </div>
</template>
