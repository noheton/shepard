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

      <b-alert
        :show="deletedAlert"
        dismissible
        variant="dark"
        @dismissed="deletedAlert = false"
      >
        Successfully deleted
      </b-alert>

      <FilterListLine
        :max-objects="totalRows"
        :default-page="currentPage"
        :default-size="perPage"
        :default-descending="descending"
        :default-order-by="orderBy"
        @filterChanged="filterChanged($event)"
      />
      <GenericEntityList
        :entities="containers"
        @createEntity="createContainer($event)"
        @deleteEntity="deleteContainer($event)"
      />
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
  FilterChangedData,
} from "@/components/generic/FilterListLine.vue";
import GenericCreateModal from "@/components/generic/GenericCreateModal.vue";
import GenericEntityList from "@/components/generic/GenericEntityList.vue";
import { TimeseriesVue } from "@/utils/api-mixin";
import { emitter } from "@/utils/event-bus";
import {
  GetAllTimeseriesContainersOrderByEnum,
  TimeseriesContainer,
} from "@dlr-shepard/shepard-client";
import Vue, { VueConstructor } from "vue";

interface TimeseriesListData {
  containers: TimeseriesContainer[];
  perPage: number;
  currentPage: number;
  orderBy: string;
  descending: boolean;
  deletedAlert: boolean;
}

export default (
  Vue as VueConstructor<Vue & InstanceType<typeof TimeseriesVue>>
).extend({
  components: { GenericEntityList, FilterListLine, GenericCreateModal },
  mixins: [TimeseriesVue],
  data() {
    return {
      containers: [],
      perPage: 10,
      currentPage: 1,
      orderBy: "createdAt",
      descending: false,
      deletedAlert: false,
    } as TimeseriesListData;
  },
  computed: {
    totalRows(): number {
      if (this.containers.length < this.perPage) {
        return this.currentPage * this.perPage;
      }
      return (this.currentPage + 1) * this.perPage;
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
      this.timeseriesApi
        ?.getAllTimeseriesContainers({
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
    createContainer(newName: string) {
      this.timeseriesApi
        ?.createTimeseriesContainer({
          timeseriesContainer: { name: newName } as TimeseriesContainer,
        })
        .then(response => {
          this.$router.push({
            name: "Timeseries",
            params: {
              timeseriesId: String(response.id),
            },
          });
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
