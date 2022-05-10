<template>
  <div class="file-container-list">
    <div class="component">
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
import FileService from "@/services/fileService";
import { emitter } from "@/utils/event-bus";
import { totalRows } from "@/utils/helpers";
import {
  FileContainer,
  GetAllFileContainersOrderByEnum,
} from "@dlr-shepard/shepard-client";
import Vue from "vue";

interface FilesListData {
  containers?: FileContainer[];
  perPage: number;
  currentPage: number;
  orderBy: string;
  descending: boolean;
  deletedAlert: boolean;
}

export default Vue.extend({
  components: { GenericEntityList, FilterListLine, GenericCreateModal },
  data() {
    return {
      containers: undefined,
      perPage: 10,
      currentPage: 1,
      orderBy: "createdAt",
      descending: false,
      deletedAlert: false,
    } as FilesListData;
  },
  computed: {
    totalRows(): number {
      if (this.containers)
        return totalRows(
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
        .orderBy as keyof typeof GetAllFileContainersOrderByEnum as GetAllFileContainersOrderByEnum;
      FileService.getAllFileContainers({
        size: this.perPage,
        page: nextPage - 1,
        orderBy: nextOrderBy,
        orderDesc: this.descending,
      })
        .then(response => {
          this.containers = response;
        })
        .catch(e => {
          const error = "Error while fetching file containers: " + e.statusText;
          console.log(error);
          emitter.emit("error", error);
        });
    },
    createContainer(newName: string) {
      FileService.createFileContainer({
        fileContainer: { name: newName } as FileContainer,
      })
        .then(response => {
          this.$router.push({
            name: "Files",
            params: {
              fileId: String(response.id),
            },
          });
        })
        .catch(e => {
          const error = "Error while creating file container: " + e.statusText;
          console.log(error);
          emitter.emit("error", error);
        });
    },
  },
});
</script>
