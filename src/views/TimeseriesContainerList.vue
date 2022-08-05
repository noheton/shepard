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

<script lang="ts">
import FilterListLine, {
  type FilterChangedData,
} from "@/components/generic/FilterListLine.vue";
import GenericCreateModal from "@/components/generic/GenericCreateModal.vue";
import GenericEntityList from "@/components/generic/GenericEntityList.vue";
import TimeseriesService from "@/services/timeseriesService";
import { emitter } from "@/utils/event-bus";
import { getTotalRows } from "@/utils/helpers";
import type {
  GetAllTimeseriesContainersOrderByEnum,
  PermissionsPermissionTypeEnum,
  TimeseriesContainer,
} from "@dlr-shepard/shepard-client";
import { defineComponent } from "vue";

interface TimeseriesListData {
  containers: TimeseriesContainer[];
  perPage: number;
  currentPage: number;
  orderBy: string;
  descending: boolean;
}

export default defineComponent({
  components: { GenericEntityList, FilterListLine, GenericCreateModal },
  data() {
    return {
      containers: [],
      perPage: 10,
      currentPage: 1,
      orderBy: "createdAt",
      descending: false,
    } as TimeseriesListData;
  },
  computed: {
    totalRows(): number {
      if (this.containers)
        return getTotalRows(
          this.containers.length,
          this.perPage,
          this.currentPage,
        );
      else return 0;
    },
  },
  mounted() {
    this.retrieveContainers();
  },
  methods: {
    filterChanged(options: FilterChangedData) {
      this.currentPage = options.currentPage;
      this.perPage = options.currentSize;
      this.descending = options.descending;
      this.orderBy = options.orderBy;
      this.retrieveContainers();
    },
    retrieveContainers(page?: number) {
      const nextPage = page || this.currentPage;
      const nextOrderBy = this
        .orderBy as keyof typeof GetAllTimeseriesContainersOrderByEnum as GetAllTimeseriesContainersOrderByEnum;
      TimeseriesService.getAllTimeseriesContainers({
        size: this.perPage,
        page: nextPage - 1,
        orderBy: nextOrderBy,
        orderDesc: this.descending,
      })
        .then(response => {
          this.containers = response;
        })
        .catch(e => {
          const error =
            "Error while fetching timeseries containers: " + e.statusText;
          console.log(error);
          emitter.emit("error", error);
        });
    },
    createContainer(options: {
      name: string;
      perms: PermissionsPermissionTypeEnum;
    }) {
      TimeseriesService.createTimeseriesContainer({
        timeseriesContainer: { name: options.name } as TimeseriesContainer,
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

            this.$router.push({
              name: "Timeseries",
              params: {
                timeseriesId: String(response.id),
              },
            });
          }
        })
        .catch(e => {
          const error =
            "Error while creating timeseries container: " + e.statusText;
          console.log(error);
          emitter.emit("error", error);
        });
    },
  },
});
</script>
