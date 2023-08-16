<script setup lang="ts">
import CreateStructuredDataModal from "@/components/containers/CreateStructuredDataModal.vue";
import DeleteConfirmationModal from "@/components/DeleteConfirmationModal.vue";
import CreatedByLine from "@/components/generic/CreatedByLine.vue";
import GenericName from "@/components/generic/GenericName.vue";
import PermissionsModal from "@/components/PermissionsModal.vue";
import JsonStructuredDataModal from "@/components/references/JsonStructuredDataModal.vue";
import StructuredDataService from "@/services/structuredDataService";
import { handleError, logError } from "@/utils/error-handling";
import type {
  Permissions,
  ResponseError,
  Roles,
  StructuredData,
  StructuredDataContainer,
  StructuredDataPayload,
} from "@dlr-shepard/shepard-client";
import { useTitle } from "@vueuse/core";
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue2-helpers/vue-router";
import CurrentRoleIcon from "../components/generic/CurrentRoleIcon.vue";

const route = useRoute();
const router = useRouter();

const currentStructuredDataContainer = ref<StructuredDataContainer>();
const currentStructuredData = ref<StructuredData>();
const permissions = ref<Permissions>();
const structuredDataList = ref<Array<StructuredData>>([]);

const deletedAlert = ref<boolean>(false);

const currentStructuredDataContainerId = computed<string>(
  () => route.params.structuredDataId,
);

function retrieveStructuredDataContainer() {
  StructuredDataService.getStructuredDataContainer({
    structureddataContainerId: +currentStructuredDataContainerId.value,
  })
    .then(response => {
      currentStructuredDataContainer.value = response;
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching structured data container");
    });
}

function retrieveStructuredDataList() {
  StructuredDataService.getAllStructuredDatas({
    structureddataContainerId: +currentStructuredDataContainerId.value,
  })
    .then(response => {
      // it was possible to create structured data that is null
      structuredDataList.value = response.filter(e => e);
    })
    .catch(e => {
      logError(e as ResponseError, "fetching structured data payload");
    });
}

function createStructuredData(newStructuredDataPayload: StructuredDataPayload) {
  if (currentStructuredDataContainer.value?.id)
    StructuredDataService.createStructuredData({
      structureddataContainerId: currentStructuredDataContainer.value.id,
      structuredDataPayload: newStructuredDataPayload,
    })
      .then(() => {
        retrieveStructuredDataList();
      })
      .catch(e => {
        handleError(e as ResponseError, "creating Structured Data");
      });
}

function handleDeleteStructuredDataContainer() {
  StructuredDataService.deleteStructuredDataContainer({
    structureddataContainerId: +currentStructuredDataContainerId.value,
  })
    .then(() => {
      router.push({ name: "StructuredDatasList" });
    })
    .catch(e => {
      handleError(e as ResponseError, "deleting structured data container");
    });
}

function handleDeleteStructuredData() {
  if (
    !currentStructuredDataContainer.value?.id ||
    !currentStructuredData.value?.oid
  )
    return;
  StructuredDataService.deleteStructuredData({
    structureddataContainerId: +currentStructuredDataContainer.value.id,
    oid: currentStructuredData.value.oid,
  })
    .then(() => {
      deletedAlert.value = true;
      retrieveStructuredDataList();
    })
    .catch(e => {
      handleError(e as ResponseError, "deleting structured data");
    });
}

function retrievePermissions() {
  StructuredDataService.getStructuredDataPermissions({
    structureddataContainerId: +currentStructuredDataContainerId.value,
  })
    .then(response => {
      permissions.value = response;
    })
    .catch(e => {
      logError(e as ResponseError, "fetching permissions");
    });
}

function updatePermissions(perms: Permissions) {
  StructuredDataService.editStructuredDataPermissions({
    structureddataContainerId: +currentStructuredDataContainerId.value,
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
  StructuredDataService.getStructuredDataRoles({
    structureddataContainerId: +currentStructuredDataContainerId.value,
  })
    .then(response => {
      roles.value = response;
    })
    .catch(e => {
      logError(e as ResponseError, "fetching roles");
    });
}

const title = computed(() => {
  return (
    currentStructuredDataContainer.value?.name || "Structured Data Container"
  );
});
function updateTitle() {
  useTitle(title, {
    titleTemplate: "%s | shepard",
  });
}

onMounted(() => {
  retrieveStructuredDataContainer();
  retrieveStructuredDataList();
  retrieveRoles();
  updateTitle();
});
</script>

<template>
  <div v-if="currentStructuredDataContainer" class="view">
    <b-alert
      :show="deletedAlert"
      dismissible
      variant="info"
      @dismissed="deletedAlert = false"
    >
      Successfully deleted
    </b-alert>
    <b-button-group v-if="roles?.owner || roles?.writer" class="float-right">
      <b-button
        v-b-modal.create-structured-data-modal
        v-b-tooltip.hover
        title="Create Structured Data"
        variant="primary"
      >
        <CreateIcon />
      </b-button>
      <b-button
        v-if="roles?.owner || roles?.manager"
        v-b-modal.permissions-modal
        v-b-tooltip.hover
        title="Edit Permissions"
        variant="secondary"
        @click="retrievePermissions()"
      >
        <PermissionsIcon />
      </b-button>
      <b-button
        v-b-modal.delete-structured-data-container-confirmation-modal
        v-b-tooltip.hover
        title="Delete"
        variant="info"
      >
        <DeleteIcon />
      </b-button>
    </b-button-group>
    <h3 class="title">
      {{ currentStructuredDataContainer.name }}
      <CurrentRoleIcon :roles="roles" />
    </h3>
    <div class="mb-3">
      <b>ID:</b> {{ currentStructuredDataContainer.id }}<br />
      <b>Oid:</b> {{ currentStructuredDataContainer.oid }}<br />
      <CreatedByLine
        :created-at="currentStructuredDataContainer.createdAt"
        :created-by="currentStructuredDataContainer.createdBy"
        tooltip
      />
    </div>
    <b-list-group>
      <b-list-group-item
        v-for="(structuredData, index) in structuredDataList"
        :key="index"
      >
        <b-button-group class="float-right">
          <b-button
            v-b-modal.json-structured-data-modal
            v-b-tooltip.hover
            title="Show Editor"
            variant="secondary"
            @click="currentStructuredData = structuredData"
          >
            <EyeIcon />
          </b-button>
          <b-button
            v-b-modal.delete-structured-data-confirmation-modal
            v-b-tooltip.hover
            title="Delete"
            variant="info"
            @click="currentStructuredData = structuredData"
          >
            <DeleteIcon />
          </b-button>
        </b-button-group>

        <div>
          <b>
            <GenericName :name="structuredData.name || ''" :word-count="40" />
          </b>
          | {{ structuredData.oid }} |
          {{ new Date(structuredData.createdAt || "").toLocaleString() }}
        </div>
      </b-list-group-item>
    </b-list-group>
    <CreateStructuredDataModal
      modal-id="create-structured-data-modal"
      modal-name="Create Structured Data"
      @created="createStructuredData($event)"
    />
    <DeleteConfirmationModal
      modal-id="delete-structured-data-container-confirmation-modal"
      modal-name="Confirm to delete structured data container"
      :modal-text="
        'Do you really want do delete the structured data container with name ' +
        currentStructuredDataContainer.name +
        '?'
      "
      @confirmation="handleDeleteStructuredDataContainer()"
    />
    <DeleteConfirmationModal
      v-if="currentStructuredData"
      modal-id="delete-structured-data-confirmation-modal"
      modal-name="Confirm to delete Structured Data"
      :modal-text="
        'Do you really want do delete the Structured Data with name ' +
        currentStructuredData.name +
        '?'
      "
      @confirmation="handleDeleteStructuredData()"
    />
    <JsonStructuredDataModal
      v-if="currentStructuredData && currentStructuredData.oid"
      modal-id="json-structured-data-modal"
      modal-name="Structured Data"
      :container-id="+currentStructuredDataContainerId"
      :oid="currentStructuredData.oid"
    />
    <PermissionsModal
      modal-id="permissions-modal"
      modal-name="Edit Permissions"
      :entity-id="+currentStructuredDataContainerId"
      :old-permissions="permissions"
      @update="updatePermissions($event)"
    />
  </div>
</template>
