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
              v-b-modal.edit-structured-data-modal
              v-b-tooltip.hover
              title="not yet implemented"
              variant="light"
              disabled
            >
              <EditIcon />
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

          <small>
            <b-link @click="toggleReadMore(structuredData.oid)">
              <span v-if="readMore[structuredData.oid]"><CollapsIcon /></span>
              <span v-else><ExtendIcon /></span>
            </b-link>
            <b>Payload:</b>
            <b-link
              v-if="payloadMap[structuredData.oid]"
              title="Copy"
              class="ml-1"
              @click="copyPayload(structuredData.oid)"
            >
              <CopyIcon :size="15" />
            </b-link>
            <span v-if="payloadMap[structuredData.oid]">
              <span v-if="readMore[structuredData.oid]">
                <pre class="payload">{{
                  payloadMap[structuredData.oid] | pretty
                }}</pre>
              </span>
            </span>
          </small>
        </b-list-group-item>
      </b-list-group>
    </div>
    <CreateStructuredDataModal
      modal-id="create-structured-data-modal"
      modal-name="Create Structured Data"
      @created="createStructuredData($event)"
    />
    <CreateStructuredDataModal
      modal-id="edit-structured-data-modal"
      modal-name="Edit Structured Data"
      @created="editStructuredData($event)"
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
import PermissionsModal from "@/components/PermissionsModal.vue";
import { StructuredDataVue } from "@/utils/api-mixin";
import { emitter } from "@/utils/event-bus";
import {
  Permissions,
  StructuredData,
  StructuredDataContainer,
  StructuredDataPayload,
} from "@dlr-shepard/shepard-client";
import Vue, { VueConstructor } from "vue";

interface StructuredDataData {
  currentStructuredDataContainer?: StructuredDataContainer;
  currentStructuredData?: StructuredData;
  permissions?: Permissions;
  structuredDataList: StructuredData[];
  payloadMap: { [key: string]: string };
  managerAccess: boolean;
  readMore: { [key: string]: boolean };
  deletedAlert: boolean;
}

export default (
  Vue as VueConstructor<Vue & InstanceType<typeof StructuredDataVue>>
).extend({
  components: {
    CreatedByLine,
    DeleteConfirmationModal,
    PermissionsModal,
    GenericName,
    CreateStructuredDataModal,
  },
  filters: {
    pretty: function (value: string) {
      return JSON.stringify(JSON.parse(value), null, 2);
    },
  },
  mixins: [StructuredDataVue],
  data() {
    return {
      currentStructuredDataContainer: undefined,
      currentStructuredData: undefined,
      permissions: undefined,
      structuredDataList: [],
      payloadMap: {},
      managerAccess: false,
      readMore: {},
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
      this.structuredDataApi
        ?.getStructuredDataContainer({
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
      this.structuredDataApi
        ?.getAllStructuredDatas({
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
    retrievePayload(oid: string) {
      this.structuredDataApi
        ?.getStructuredData({
          structureddataContainerId: this.currentStructuredDataContainerId,
          oid: oid,
        })
        .then(response => {
          if (response.payload) this.payloadMap[oid] = response.payload;
          this.payloadMap = { ...this.payloadMap };
        })
        .catch(e => {
          const error =
            "Error while fetching structured data payload: " + e.statusText;
          console.log(error);
          emitter.emit("error", error);
        });
    },
    createStructuredData(newStructuredDataPayload: StructuredDataPayload) {
      if (this.currentStructuredDataContainer?.id)
        this.structuredDataApi
          ?.createStructuredData({
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
          })
          .finally();
    },
    editStructuredData() {
      console.log("noch nicht implementiert");
    },

    handleDeleteStructuredDataContainer() {
      this.structuredDataApi
        ?.deleteStructuredDataContainer({
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
        this.structuredDataApi
          ?.deleteStructuredData({
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
      this.structuredDataApi
        ?.getStructuredDataPermissions({
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
      this.structuredDataApi
        ?.editStructuredDataPermissions({
          structureddataContainerId: this.currentStructuredDataContainerId,
          permissions: perms,
        })
        .then(response => {
          this.permissions = response;
        })
        .catch(e => {
          const error = "Error while editing permissons: " + e.statusText;
          console.log(error);
        });
    },
    copyPayload(oid: string) {
      const payload = this.payloadMap[oid];
      if (payload) navigator.clipboard.writeText(payload);
    },
    toggleReadMore(oid: string) {
      this.readMore[oid] = !this.readMore[oid];
      this.readMore = { ...this.readMore };
      if (!this.payloadMap[oid]) this.retrievePayload(oid);
    },
  },
});
</script>

<style scoped>
.payload {
  color: #e83e8c;
}
</style>
