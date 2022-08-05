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
        modal-id="create-structured-data-container-modal"
        modal-name="Create Structured Data Container"
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
import StructuredDataService from "@/services/structuredDataService";
import { emitter } from "@/utils/event-bus";
import { getTotalRows } from "@/utils/helpers";
import type {
  GetAllStructuredDataContainersOrderByEnum,
  PermissionsPermissionTypeEnum,
  StructuredDataContainer,
} from "@dlr-shepard/shepard-client";
import { defineComponent } from "vue";

interface StructuredDatasListData {
  containers?: StructuredDataContainer[];
  perPage: number;
  currentPage: number;
  orderBy: string;
  descending: boolean;
}

export default defineComponent({
  components: { GenericEntityList, FilterListLine, GenericCreateModal },
  data() {
    return {
      containers: undefined,
      perPage: 10,
      currentPage: 1,
      orderBy: "createdAt",
      descending: false,
    } as StructuredDatasListData;
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
        .orderBy as keyof typeof GetAllStructuredDataContainersOrderByEnum as GetAllStructuredDataContainersOrderByEnum;
      StructuredDataService.getAllStructuredDataContainers({
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
            "Error while fetching structured data containers: " + e.statusText;
          console.log(error);
          emitter.emit("error", error);
        });
    },
    createContainer(options: {
      name: string;
      perms: PermissionsPermissionTypeEnum;
    }) {
      StructuredDataService.createStructuredDataContainer({
        structuredDataContainer: {
          name: options.name,
        } as StructuredDataContainer,
      })
        .then(async response => {
          if (response.id) {
            const perms =
              await StructuredDataService.getStructuredDataPermissions({
                structureddataContainerId: response.id,
              });
            perms.permissionType = options.perms;
            await StructuredDataService.editStructuredDataPermissions({
              structureddataContainerId: response.id,
              permissions: perms,
            });
            this.$router.push({
              name: "StructuredData",
              params: {
                structuredDataId: String(response.id),
              },
            });
          }
        })
        .catch(e => {
          const error =
            "Error while creating structured data container: " + e.statusText;
          console.log(error);
          emitter.emit("error", error);
        });
    },
  },
});
</script>
