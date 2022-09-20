<script setup lang="ts">
import DeleteConfirmationModal from "@/components/DeleteConfirmationModal.vue";
import CreatedByLine from "@/components/generic/CreatedByLine.vue";
import PermissionsModal from "@/components/PermissionsModal.vue";
import TimeseriesService from "@/services/timeseriesService";
import { handleError, logError } from "@/utils/error-handling";
import type {
  Permissions,
  ResponseError,
  Roles,
  Timeseries,
  TimeseriesContainer,
} from "@dlr-shepard/shepard-client";
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue2-helpers/vue-router";
import CurrentRoleIcon from "../components/generic/CurrentRoleIcon.vue";

const route = useRoute();
const router = useRouter();

const currentTimeseriesContainer = ref<TimeseriesContainer>();
const timeseriesAvailable = ref<Array<Timeseries>>([]);
const timeseriesFields = ref<Array<string>>([
  "measurement",
  "device",
  "location",
  "symbolicName",
]);
const permissions = ref<Permissions>();

const currentTimeseriesContainerId = computed<string>(
  () => route.params.timeseriesId,
);

function retrieveTimeseriesContainer() {
  TimeseriesService.getTimeseriesContainer({
    timeseriesContainerId: +currentTimeseriesContainerId.value,
  })
    .then(response => {
      currentTimeseriesContainer.value = response;
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching timeseries container");
    });
}

function retrieveTimeseriesAvailable() {
  TimeseriesService.getTimeseriesAvailable({
    timeseriesContainerId: +currentTimeseriesContainerId.value,
  })
    .then(response => {
      timeseriesAvailable.value = response;
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching timeseries available");
    });
}

function handleDelete() {
  TimeseriesService.deleteTimeseriesContainer({
    timeseriesContainerId: +currentTimeseriesContainerId.value,
  })
    .then(() => {
      router.push({ name: "TimeseriesList" });
    })
    .catch(e => {
      handleError(e as ResponseError, "deleting timeseries container");
    });
}

function retrievePermissions() {
  TimeseriesService.getTimeseriesPermissions({
    timeseriesContainerId: +currentTimeseriesContainerId.value,
  })
    .then(response => {
      permissions.value = response;
    })
    .catch(e => {
      logError(e as ResponseError, "fetching permissions");
    });
}

function updatePermissions(perms: Permissions) {
  TimeseriesService.editTimeseriesPermissions({
    timeseriesContainerId: +currentTimeseriesContainerId.value,
    permissions: perms,
  })
    .then(response => {
      permissions.value = response;
    })
    .catch(e => {
      handleError(e as ResponseError, "updating permissions");
    });
}

const roles = ref<Roles | undefined>();
function retrieveRoles() {
  TimeseriesService.getTimeseriesRoles({
    timeseriesContainerId: +currentTimeseriesContainerId.value,
  })
    .then(response => {
      roles.value = response;
    })
    .catch(e => {
      logError(e as ResponseError, "fetching roles");
    });
}

onMounted(() => {
  retrieveTimeseriesContainer();
  retrieveTimeseriesAvailable();
  retrieveRoles();
  retrievePermissions();
});
</script>

<template>
  <div v-if="currentTimeseriesContainer" class="timeseries-container">
    <div class="component">
      <b-button-group
        v-if="!roles || roles.owner || roles.writer"
        class="float-right"
      >
        <b-button
          v-if="!roles || roles.owner || roles.manager"
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
      <h3>
        {{ currentTimeseriesContainer.name }}
        <CurrentRoleIcon :roles="roles" />
      </h3>
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
      :entity-id="+currentTimeseriesContainerId"
      :old-permissions="permissions"
      @update="updatePermissions($event)"
    />
  </div>
</template>
