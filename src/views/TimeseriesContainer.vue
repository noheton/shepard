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
      <p>
        <b>ID:</b> {{ currentTimeseriesContainer.id }}<br />
        <b>Database:</b> {{ currentTimeseriesContainer.database }}<br />
        <CreatedByLine
          :created-at="currentTimeseriesContainer.createdAt"
          :created-by="currentTimeseriesContainer.createdBy"
          tooltip
        />
      </p>
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
import { TimeseriesVue } from "@/utils/api-mixin";
import { emitter } from "@/utils/event-bus";
import { Permissions, TimeseriesContainer } from "@dlr-shepard/shepard-client";
import Vue, { VueConstructor } from "vue";

interface TimeseriesData {
  currentTimeseriesContainer?: TimeseriesContainer;
  permissions?: Permissions;
  managerAccess: boolean;
}

export default (
  Vue as VueConstructor<Vue & InstanceType<typeof TimeseriesVue>>
).extend({
  components: { CreatedByLine, DeleteConfirmationModal, PermissionsModal },
  mixins: [TimeseriesVue],
  data() {
    return {
      currentTimeseriesContainer: undefined,
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
  },
  methods: {
    retrieveTimeseriesContainer() {
      this.timeseriesApi
        ?.getTimeseriesContainer({
          timeseriesContainerId: this.currentTimeseriesContainerId,
        })
        .then(response => {
          this.currentTimeseriesContainer = response;
        })
        .catch(e => {
          const error =
            "Error while fetching timeseries container: " + e.statusText;
          console.log(error);
          emitter.emit("error", error);
        });
    },
    handleDelete() {
      this.timeseriesApi
        ?.deleteTimeseriesContainer({
          timeseriesContainerId: this.currentTimeseriesContainerId,
        })
        .then(() => {
          this.$router.push({ name: "TimeseriesList" });
        })
        .catch(e => {
          const error =
            "Error while deleting timeseries container: " + e.statusText;
          console.log(error);
          emitter.emit("error", error);
        });
    },
    retrievePermissions() {
      this.timeseriesApi
        ?.getTimeseriesPermissions({
          timeseriesContainerId: this.currentTimeseriesContainerId,
        })
        .then(response => {
          this.permissions = response;
          this.managerAccess = true;
        })
        .catch(e => {
          const error = "Error while fetching permissons: " + e.statusText;
          console.log(error);
          this.managerAccess = e.status != 403;
        });
    },
    updatePermissions(perms: Permissions) {
      this.timeseriesApi
        ?.editTimeseriesPermissions({
          timeseriesContainerId: this.currentTimeseriesContainerId,
          permissions: perms,
        })
        .then(response => {
          this.permissions = response;
        })
        .catch(e => {
          const error = "Error while edit permissons: " + e.statusText;
          console.log(error);
        });
    },
  },
});
</script>
