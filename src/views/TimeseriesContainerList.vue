<script setup lang="ts">
import FilterListLine from "@/components/generic/FilterListLine.vue";
import GenericCreateModal from "@/components/generic/GenericCreateModal.vue";
import GenericEntityList from "@/components/generic/GenericEntityList.vue";
import TimeseriesService from "@/services/timeseriesService";
import { handleError } from "@/utils/error-handling";
import { getTotalRows, type FilterChangedData } from "@/utils/helpers";
import type {
  GetAllTimeseriesContainersOrderByEnum,
  PermissionsPermissionTypeEnum,
  ResponseError,
  StructuredDataContainer,
} from "@dlr-shepard/shepard-client";
import { useTitle } from "@vueuse/core";
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue2-helpers/vue-router";

const router = useRouter();

const containers = ref<StructuredDataContainer[]>();
const perPage = ref(10);
const currentPage = ref(1);
const orderBy = ref("createdAt");
const descending = ref(false);

const totalRows = computed(() => {
  if (containers.value)
    return getTotalRows(
      containers.value.length,
      perPage.value,
      currentPage.value,
    );
  else return 0;
});

function filterChanged(options: FilterChangedData) {
  currentPage.value = options.currentPage;
  perPage.value = options.currentSize;
  descending.value = options.descending;
  orderBy.value = options.orderBy;
  retrieveContainers();
}

function retrieveContainers(page?: number) {
  const nextPage = page || currentPage.value;
  const nextOrderBy =
    orderBy.value as keyof typeof GetAllTimeseriesContainersOrderByEnum as GetAllTimeseriesContainersOrderByEnum;
  TimeseriesService.getAllTimeseriesContainers({
    size: perPage.value,
    page: nextPage - 1,
    orderBy: nextOrderBy,
    orderDesc: descending.value,
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

      <FilterListLine
        :max-objects="totalRows"
        :default-page="currentPage"
        :default-size="perPage"
        :default-descending="descending"
        :default-order-by="orderBy"
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
        :per-page="perPage"
        align="center"
        size="sm"
        @change="retrieveContainers($event)"
      ></b-pagination>
    </div>
  </div>
</template>
