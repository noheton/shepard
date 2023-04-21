<script setup lang="ts">
import FilterListLine from "@/components/generic/FilterListLine.vue";
import GenericCreateModal from "@/components/generic/GenericCreateModal.vue";
import GenericEntityList from "@/components/generic/GenericEntityList.vue";
import SearchService from "@/services/searchService";
import StructuredDataService from "@/services/structuredDataService";
import { handleError } from "@/utils/error-handling";
import {
  getTotalRows,
  type FilterChangedData,
  type FilterOptions,
} from "@/utils/helpers";
import {
  ContainerSearchParamsQueryTypeEnum,
  type GetAllStructuredDataContainersOrderByEnum,
  type PermissionsPermissionTypeEnum,
  type ResponseError,
  type StructuredDataContainer,
} from "@dlr-shepard/shepard-client";
import { refDebounced, useStorage, useTitle } from "@vueuse/core";
import { computed, onMounted, ref, watch } from "vue";
import { useRouter } from "vue2-helpers/vue-router";

const router = useRouter();

const containers = ref<StructuredDataContainer[]>();

const filterOptions = useStorage<FilterOptions>("sd-filter-options", {
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
    .orderBy as keyof typeof GetAllStructuredDataContainersOrderByEnum as GetAllStructuredDataContainersOrderByEnum;
  StructuredDataService.getAllStructuredDataContainers({
    size: filterOptions.value.perPage,
    page: nextPage - 1,
    orderBy: nextOrderBy,
    orderDesc: filterOptions.value.descending,
  })
    .then(response => {
      containers.value = response;
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching structured data containers");
    });
}
function createContainer(options: {
  name: string;
  perms: PermissionsPermissionTypeEnum;
}) {
  StructuredDataService.createStructuredDataContainer({
    structuredDataContainer: {
      name: options.name,
    },
  })
    .then(async response => {
      if (response.id) {
        const perms = await StructuredDataService.getStructuredDataPermissions({
          structureddataContainerId: response.id,
        });
        perms.permissionType = options.perms;
        await StructuredDataService.editStructuredDataPermissions({
          structureddataContainerId: response.id,
          permissions: perms,
        });
        router.push({
          name: "StructuredData",
          params: {
            structuredDataId: String(response.id),
          },
        });
      }
    })
    .catch(e => {
      handleError(e as ResponseError, "creating structured data container");
    });
}

const userInput = ref("");
const userInputDebounced = refDebounced(userInput, 700);
const structuredDataContainerResultSet = ref<StructuredDataContainer[]>([]);
const totalResults = ref(0);

watch(userInputDebounced, () => {
  if (
    userInputDebounced.value.length != 0 &&
    (userInputDebounced.value.length >= 3 ||
      !isNaN(Number(userInputDebounced.value)))
  ) {
    inlineSearch();
  } else {
    structuredDataContainerResultSet.value = [];
  }
});

function inlineSearch() {
  const searchQuery = {
    OR: [
      {
        property: "name",
        value: userInput.value,
        operator: "contains",
      },
      {
        property: "createdBy",
        value: userInput.value,
        operator: "contains",
      },
      {
        property: "id",
        value: Number(userInput.value),
        operator: "eq",
      },
    ],
  };
  SearchService.searchContainers({
    containerSearchBody: {
      searchParams: {
        query: JSON.stringify(searchQuery),
        queryType: ContainerSearchParamsQueryTypeEnum.Structureddata,
      },
    },
  })
    .then(response => {
      structuredDataContainerResultSet.value = [];
      totalResults.value = response.structuredDataContainers?.length || 0;
      response.structuredDataContainers?.slice(0, 10).forEach(result => {
        if (result.id) {
          retrieveStructuredDataContainerById(result.id);
        }
      });
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching search data");
    });
}

function retrieveStructuredDataContainerById(
  structuredDataContainerId: number,
) {
  StructuredDataService.getStructuredDataContainer({
    structureddataContainerId: structuredDataContainerId,
  })
    .then(response => {
      structuredDataContainerResultSet.value = [
        ...structuredDataContainerResultSet.value,
        response,
      ];
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching structured data container");
    });
}

onMounted(() => {
  retrieveContainers();
  useTitle("Structured Data Containers | shepard");
});
</script>

<template>
  <div class="structured-data-container-list">
    <div class="component">
      <b-button-group class="float-right">
        <b-button
          v-b-modal.create-structured-data-container-modal
          v-b-tooltip.hover
          title="Create Structured Data Container"
          variant="primary"
        >
          <CreateIcon />
        </b-button>
      </b-button-group>

      <h4>Structured Data Containers</h4>
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
        <template #title>Result Set ({{ totalResults }} total)</template>
        <GenericEntityList :entities="structuredDataContainerResultSet" />
      </b-popover>

      <FilterListLine
        :max-objects="totalRows"
        :current-page="currentPage"
        :filter-options="filterOptions"
        @filter-changed="filterChanged($event)"
      />
      <GenericEntityList :entities="containers" />
      <GenericCreateModal
        modal-id="create-structured-data-container-modal"
        modal-name="Create Structured Data Container"
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
  </div>
</template>
