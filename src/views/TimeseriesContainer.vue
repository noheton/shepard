<template>
  <div v-if="currentTimeseriesContainer" class="timeseries-container">
    <div class="component">
      <b-button-group class="float-right">
        <b-button
          v-if="managerAccess"
          v-b-modal.permissions-modal
          v-b-tooltip.hover
          title="Edit Permissions"
          variant="light"
        >
          <PermissionsIcon />
        </b-button>
        <b-button
          v-b-modal.delete-confirmation-modal
          v-b-tooltip.hover
          title="Delete"
          variant="dark"
        >
          <DeleteIcon />
        </b-button>
      </b-button-group>
      <h3>{{ currentTimeseriesContainer.name }}</h3>
      <div class="mb-3">
        <b>ID:</b> {{ currentTimeseriesContainer.id }}<br />
        <b>Database:</b> {{ currentTimeseriesContainer.database }}<br />
        <CreatedByLine
          :created-at="currentTimeseriesContainer.createdAt"
          :created-by="currentTimeseriesContainer.createdBy"
          tooltip
        />
      </div>
      <b-table
        striped
        hover
        small
        :items="timeseriesAvailable"
        :fields="timeseriesFields"
      >
      </b-table>
    </div>
    <DeleteConfirmationModal
      modal-id="delete-confirmation-modal"
      modal-name="Confirm to delete timeseries container"
      :modal-text="
        'Do you really want do delete the timeseries container with name ' +
        currentTimeseriesContainer.name +
        '?'
      "
      @confirmation="handleDelete()"
    />
    <PermissionsModal
      modal-id="permissions-modal"
      modal-name="Edit Permissions"
      :entity-id="currentTimeseriesContainerId"
      :old-permissions="permissions"
      @update="updatePermissions($event)"
    />
  </div>
</template>

<script lang="ts">
import DeleteConfirmationModal from "@/components/DeleteConfirmationModal.vue";
import CreatedByLine from "@/components/generic/CreatedByLine.vue";
import PermissionsModal from "@/components/PermissionsModal.vue";
import TimeseriesService from "@/services/timeseriesService";
import { handleError, logError } from "@/utils/error-handling";
import type {
  Permissions,
  ResponseError,
  Timeseries,
  TimeseriesContainer,
} from "@dlr-shepard/shepard-client";
import { defineComponent } from "vue";

interface TimeseriesData {
  currentTimeseriesContainer?: TimeseriesContainer;
  timeseriesAvailable: Timeseries[];
  timeseriesFields: string[];
  permissions?: Permissions;
  managerAccess: boolean;
}

export default defineComponent({
  components: { CreatedByLine, DeleteConfirmationModal, PermissionsModal },
  data() {
    return {
      currentTimeseriesContainer: undefined,
      timeseriesAvailable: [],
      timeseriesFields: ["measurement", "device", "location", "symbolicName"],
      permissions: undefined,
      managerAccess: false,
    } as TimeseriesData;
  },
  computed: {
    currentTimeseriesContainerId(): number {
      return Number(this.$router.currentRoute.params.timeseriesId);
    },
  },
  mounted() {
    this.retrieveTimeseriesContainer();
    this.retrievePermissions();
    this.retrieveTimeseriesAvailable();
  },
  methods: {
    retrieveTimeseriesContainer() {
      TimeseriesService.getTimeseriesContainer({
        timeseriesContainerId: this.currentTimeseriesContainerId,
      })
        .then(response => {
          this.currentTimeseriesContainer = response;
        })
        .catch(e => {
          handleError(e as ResponseError, "fetching timeseries container");
        });
    },
    retrieveTimeseriesAvailable() {
      TimeseriesService.getTimeseriesAvailable({
        timeseriesContainerId: this.currentTimeseriesContainerId,
      })
        .then(response => {
          this.timeseriesAvailable = response;
        })
        .catch(e => {
          handleError(e as ResponseError, "fetching timeseries available");
        });
    },
    handleDelete() {
      TimeseriesService.deleteTimeseriesContainer({
        timeseriesContainerId: this.currentTimeseriesContainerId,
      })
        .then(() => {
          this.$router.push({ name: "TimeseriesList" });
        })
        .catch(e => {
          handleError(e as ResponseError, "deleting timeseries container");
        });
    },
    retrievePermissions() {
      TimeseriesService.getTimeseriesPermissions({
        timeseriesContainerId: this.currentTimeseriesContainerId,
      })
        .then(response => {
          this.permissions = response;
          this.managerAccess = true;
        })
        .catch(e => {
          logError(e as ResponseError, "fetching permissions");
          this.managerAccess = e.status != 403;
        });
    },
    updatePermissions(perms: Permissions) {
      TimeseriesService.editTimeseriesPermissions({
        timeseriesContainerId: this.currentTimeseriesContainerId,
        permissions: perms,
      })
        .then(response => {
          this.permissions = response;
        })
        .catch(e => {
          handleError(e as ResponseError, "updating permissions");
        });
    },
  },
});
</script>
