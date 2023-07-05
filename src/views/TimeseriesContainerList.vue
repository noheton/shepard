<script setup lang="ts">
import FilterListLine from "@/components/generic/FilterListLine.vue";
import GenericCreateModal from "@/components/generic/GenericCreateModal.vue";
import GenericEntityList from "@/components/generic/GenericEntityList.vue";
import { useInlineSearch } from "@/components/search/SearchContainers";
import TimeseriesService from "@/services/timeseriesService";
import { handleError } from "@/utils/error-handling";
import {
  getTotalRows,
  type FilterChangedData,
  type FilterOptions,
} from "@/utils/helpers";
import type {
  GetAllTimeseriesContainersOrderByEnum,
  PermissionsPermissionTypeEnum,
  ResponseError,
  TimeseriesContainer,
} from "@dlr-shepard/shepard-client";
import { refDebounced, useStorage, useTitle } from "@vueuse/core";
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue2-helpers/vue-router";

const router = useRouter();

const containers = ref<TimeseriesContainer[]>();

const filterOptions = useStorage<FilterOptions>("timeseries-filter-options", {
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
    .orderBy as keyof typeof GetAllTimeseriesContainersOrderByEnum as GetAllTimeseriesContainersOrderByEnum;
  TimeseriesService.getAllTimeseriesContainers({
    size: filterOptions.value.perPage,
    page: nextPage - 1,
    orderBy: nextOrderBy,
    orderDesc: filterOptions.value.descending,
  })
    .then(response => {
      containers.value = response;
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching timeseries containers");
    });
}

function createContainer(options: {
  name: string;
  perms: PermissionsPermissionTypeEnum;
}) {
  TimeseriesService.createTimeseriesContainer({
    timeseriesContainer: { name: options.name },
  })
    .then(async response => {
      if (response.id) {
        const perms = await TimeseriesService.getTimeseriesPermissions({
          timeseriesContainerId: response.id,
        });
        perms.permissionType = options.perms;
        await TimeseriesService.editTimeseriesPermissions({
          timeseriesContainerId: response.id,
          permissions: perms,
        });

        router.push({
          name: "Timeseries",
          params: {
            timeseriesId: String(response.id),
          },
        });
      }
    })
    .catch(e => {
      handleError(e as ResponseError, "creating timeseries container");
    });
}

const userInput = ref("");
const userInputDebounced = refDebounced(userInput, 700);

const { resultSet, totalResults, searchQuery } = useInlineSearch(
  userInputDebounced,
  "TIMESERIES",
);

const searchRoute = computed(() => {
  const route = router.resolve("Search").route;
  route.query.queryType = "TIMESERIES";
  route.query.searchQuery = searchQuery.value;
  return route;
});

onMounted(() => {
  retrieveContainers();
  useTitle("Timeseries Containers | shepard");
});
</script>

<template>
  <div class="timeseries-container-list">
    <div class="component">
      <b-button-group class="float-right">
        <b-button
          v-b-modal.create-timeseries-container-modal
          v-b-tooltip.hover
          title="Create Timeseries Container"
          variant="primary"
        >
          <CreateIcon />
        </b-button>
      </b-button-group>
      <h4>Timeseries Containers</h4>
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
        boundary="scrollParent"
      >
        <template #title>
          Result Set ({{ totalResults }} total)
          <b-link class="float-right font-weight-normal" :to="searchRoute">
            Advanced Search
          </b-link>
        </template>
        <GenericEntityList :entities="resultSet" />
      </b-popover>

      <FilterListLine
        :max-objects="totalRows"
        :current-page="currentPage"
        :filter-options="filterOptions"
        @filter-changed="filterChanged($event)"
      />
      <GenericEntityList :entities="containers" />
      <GenericCreateModal
        modal-id="create-timeseries-container-modal"
        modal-name="Create Timeseries Container"
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
