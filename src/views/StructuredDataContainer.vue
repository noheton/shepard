<template>
  <div v-if="currentStructuredDataContainer" class="structured-data-container">
    <div class="component">
      <b-alert
        :show="deletedAlert"
        dismissible
        variant="danger"
        @dismissed="deletedAlert = false"
      >
        Successfully deleted
      </b-alert>
      <b-button-group class="float-right">
        <b-button
          v-b-modal.create-structured-data-modal
          v-b-tooltip.hover
          title="Create Structured Data"
          variant="primary"
        >
          <CreateIcon />
        </b-button>
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
          v-b-modal.delete-structured-data-container-confirmation-modal
          v-b-tooltip.hover
          title="Delete"
          variant="dark"
        >
          <DeleteIcon />
        </b-button>
      </b-button-group>
      <h3>{{ currentStructuredDataContainer.name }}</h3>
      <p>
        <b>ID:</b> {{ currentStructuredDataContainer.id }}<br />
        <b>Oid:</b> {{ currentStructuredDataContainer.oid }}<br />
        <CreatedByLine
          :created-at="currentStructuredDataContainer.createdAt"
          :created-by="currentStructuredDataContainer.createdBy"
          tooltip
        />
      </p>
      <b-list-group>
        <b-list-group-item
          v-for="(structuredData, index) in structuredDataList"
          :key="index"
        >
          <b-button-group class="float-right">
            <b-button
              v-b-modal.json-editor-modal
              v-b-tooltip.hover
              title="Show Editor"
              variant="light"
              @click="currentStructuredData = structuredData"
            >
              <EyeIcon />
            </b-button>
            <b-button
              v-b-modal.delete-structured-data-confirmation-modal
              v-b-tooltip.hover
              title="Delete"
              variant="dark"
              @click="currentStructuredData = structuredData"
            >
              <DeleteIcon />
            </b-button>
          </b-button-group>

          <div>
            <b><GenericName :name="structuredData.name" :word-count="40" /></b>
            | {{ structuredData.oid }} |
            {{ new Date(structuredData.createdAt).toLocaleString() }}
          </div>
        </b-list-group-item>
      </b-list-group>
    </div>
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
      @confirmation="handleDeleteStructuredData(currentStructuredData.oid)"
    />
    <JsonEditorModal
      v-if="currentStructuredData"
      modal-id="json-editor-modal"
      modal-name="Structured Data"
      :container-id="currentStructuredDataContainerId"
      :oid="currentStructuredData.oid"
    />
    <PermissionsModal
      modal-id="permissions-modal"
      modal-name="Edit Permissions"
      :entity-id="currentStructuredDataContainerId"
      :old-permissions="permissions"
      @update="updatePermissions($event)"
    />
  </div>
</template>

<script lang="ts">
import CreateStructuredDataModal from "@/components/containers/CreateStructuredDataModal.vue";
import DeleteConfirmationModal from "@/components/DeleteConfirmationModal.vue";
import CreatedByLine from "@/components/generic/CreatedByLine.vue";
import GenericName from "@/components/generic/GenericName.vue";
import JsonEditorModal from "@/components/generic/JsonEditorModal.vue";
import PermissionsModal from "@/components/PermissionsModal.vue";
import StructuredDataService from "@/services/structuredDataService";
import { emitter } from "@/utils/event-bus";
import {
  Permissions,
  StructuredData,
  StructuredDataContainer,
  StructuredDataPayload,
} from "@dlr-shepard/shepard-client";
import { defineComponent } from "vue";

interface StructuredDataData {
  currentStructuredDataContainer?: StructuredDataContainer;
  currentStructuredData?: StructuredData;
  permissions?: Permissions;
  structuredDataList: StructuredData[];
  managerAccess: boolean;
  deletedAlert: boolean;
}

export default defineComponent({
  components: {
    CreatedByLine,
    DeleteConfirmationModal,
    PermissionsModal,
    GenericName,
    CreateStructuredDataModal,
    JsonEditorModal,
  },
  data() {
    return {
      currentStructuredDataContainer: undefined,
      currentStructuredData: undefined,
      permissions: undefined,
      structuredDataList: [],
      managerAccess: false,
      deletedAlert: false,
    } as StructuredDataData;
  },
  computed: {
    currentStructuredDataContainerId(): number {
      return Number(this.$router.currentRoute.params.structuredDataId);
    },
  },
  mounted() {
    this.retrieveStructuredDataContainer();
    this.retrieveStructuredDataList();
    this.retrievePermissions();
  },
  methods: {
    retrieveStructuredDataContainer() {
      StructuredDataService.getStructuredDataContainer({
        structureddataContainerId: this.currentStructuredDataContainerId,
      })
        .then(response => {
          this.currentStructuredDataContainer = response;
        })
        .catch(e => {
          const error =
            "Error while fetching structured data container: " + e.statusText;
          console.log(error);
          emitter.emit("error", error);
        });
    },
    retrieveStructuredDataList() {
      StructuredDataService.getAllStructuredDatas({
        structureddataContainerId: this.currentStructuredDataContainerId,
      })
        .then(response => {
          this.structuredDataList = response;
        })
        .catch(e => {
          const error =
            "Error while fetching structured data payload: " + e.statusText;
          console.log(error);
        });
    },
    createStructuredData(newStructuredDataPayload: StructuredDataPayload) {
      if (this.currentStructuredDataContainer?.id)
        StructuredDataService.createStructuredData({
          structureddataContainerId: this.currentStructuredDataContainer.id,
          structuredDataPayload: newStructuredDataPayload,
        })
          .then(() => {
            this.retrieveStructuredDataList();
          })
          .catch(e => {
            const error =
              "Error while creating Structured Data: " + e.statusText;
            console.log(error);
            emitter.emit("error", error);
          });
    },
    handleDeleteStructuredDataContainer() {
      StructuredDataService.deleteStructuredDataContainer({
        structureddataContainerId: this.currentStructuredDataContainerId,
      })
        .then(() => {
          this.deletedAlert = true;
          this.$router.push({ name: "StructuredDatasList" });
        })
        .catch(e => {
          const error =
            "Error while deleting structured data container: " + e.statusText;
          console.log(error);
          emitter.emit("error", error);
        });
    },
    handleDeleteStructuredData(oid: string) {
      if (this.currentStructuredDataContainer?.id)
        StructuredDataService.deleteStructuredData({
          structureddataContainerId: this.currentStructuredDataContainer?.id,
          oid: oid,
        })
          .then(() => {
            this.deletedAlert = true;
            this.retrieveStructuredDataList();
          })
          .catch(e => {
            const error =
              "Error while deleting Structured Data: " + e.statusText;
            console.log(error);
            emitter.emit("error", error);
          });
    },
    retrievePermissions() {
      StructuredDataService.getStructuredDataPermissions({
        structureddataContainerId: this.currentStructuredDataContainerId,
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
      StructuredDataService.editStructuredDataPermissions({
        structureddataContainerId: this.currentStructuredDataContainerId,
        permissions: perms,
      })
        .then(response => {
          this.permissions = response;
        })
        .catch(e => {
          const error = "Error while updating permissons: " + e.statusText;
          console.log(error);
        });
    },
  },
});
</script>
