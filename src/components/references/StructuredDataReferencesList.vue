<template>
  <div class="list">
    <b-alert
      :show="createdAlert"
      dismissible
      variant="success"
      @dismissed="createdAlert = false"
    >
      Successfully created
    </b-alert>
    <b-alert
      :show="deletedAlert"
      dismissible
      variant="info"
      @dismissed="deletedAlert = false"
    >
      Successfully deleted
    </b-alert>

    <b-button
      v-b-modal.create-structured-data-ref-modal
      class="mb-3"
      variant="primary"
    >
      Create new Reference
    </b-button>

    <StructuredDataReferenceModal
      modal-id="create-structured-data-ref-modal"
      modal-name="Create StructuredData Reference"
      @create="create($event)"
    />

    <div v-if="structuredDataList == undefined"><Loading /></div>
    <b-list-group v-else>
      <b-list-group-item
        v-for="(structuredDataReference, index) in structuredDataList"
        :key="index"
      >
        <div>
          <b><GenericName :name="structuredDataReference.name || ''" /></b>
          | ID: {{ structuredDataReference.id }} |
          <span v-if="structuredDataReference.structuredDataContainerId != -1">
            <b-link
              :to="{
                name: 'StructuredData',
                params: {
                  structuredDataId:
                    structuredDataReference.structuredDataContainerId,
                },
              }"
            >
              Container: {{ structuredDataReference.structuredDataContainerId }}
            </b-link>
          </span>
          <span v-else class="text-danger">Container: Deleted</span>
          <b-button
            v-b-modal.structured-data-reference-delete-confirmation-modal
            v-b-tooltip.hover
            class="float-right"
            title="Delete"
            variant="info"
            @click="currentStructuredDataReference = structuredDataReference"
          >
            <DeleteIcon />
          </b-button>
        </div>
        <CreatedByLine
          :created-by="structuredDataReference.createdBy"
          :created-at="structuredDataReference.createdAt"
        />
        <div
          v-for="(oid, i) in structuredDataReference.structuredDataOids"
          :key="i"
        >
          <small>
            <span v-if="structuredDataReference.structuredDataContainerId == -1"
              ><EyeIcon variant="danger"
            /></span>
            <span v-else-if="!structuredDatas[oid]?.payload"
              ><EyeIcon variant="danger"
            /></span>
            <b-link
              v-else
              v-b-modal.json-editor-modal
              v-b-tooltip.hover
              title="Show Viewer"
              @click="
                (currentStructuredDataReference = structuredDataReference),
                  (currentStructuredDataOid = oid)
              "
            >
              <EyeIcon />
            </b-link>

            <b> Oid:</b> <tt>{{ oid }}</tt>
            <span v-if="structuredDatas[oid]?.structuredData?.createdAt">
              | <b>Created at: </b>
              {{ convertDate(structuredDatas[oid].structuredData?.createdAt) }}
            </span>

            <span v-if="structuredDatas[oid]?.structuredData?.name">
              | <b>Name: </b>
              {{ structuredDatas[oid].structuredData?.name }}
            </span>
          </small>
        </div>
      </b-list-group-item>
    </b-list-group>

    <DeleteConfirmationModal
      v-if="currentStructuredDataReference"
      modal-id="structured-data-reference-delete-confirmation-modal"
      modal-name="Confirm to delete Structured Data Reference"
      :modal-text="
        'Do you really want do delete the Structured Data Reference with name ' +
        currentStructuredDataReference.name +
        '?'
      "
      @confirmation="handleDelete()"
    />
    <JsonEditorModal
      v-if="currentStructuredDataReference && currentStructuredDataOid"
      modal-id="json-editor-modal"
      modal-name="Structured Data Reference"
      :container-id="currentStructuredDataReference.structuredDataContainerId"
      :oid="currentStructuredDataOid"
    />
  </div>
</template>

<script lang="ts">
import DeleteConfirmationModal from "@/components/DeleteConfirmationModal.vue";
import CreatedByLine from "@/components/generic/CreatedByLine.vue";
import GenericName from "@/components/generic/GenericName.vue";
import JsonEditorModal from "@/components/generic/JsonEditorModal.vue";
import Loading from "@/components/generic/Loading.vue";
import StructuredDataReferenceModal from "@/components/references/StructuredDataReferenceModal.vue";
import StructuredDataReferenceService from "@/services/structuredDataReferenceService";
import { handleError, logError } from "@/utils/error-handling";
import { dateFormat } from "@/utils/helpers";
import type {
  ResponseError,
  StructuredDataPayload,
  StructuredDataReference,
} from "@dlr-shepard/shepard-client";
import { defineComponent } from "vue";

interface StructuredDataListData {
  structuredDataList?: StructuredDataReference[];
  structuredDatas: { [key: string]: StructuredDataPayload };
  currentStructuredDataReference?: StructuredDataReference;
  currentStructuredDataOid?: string;
  createdAlert: boolean;
  deletedAlert: boolean;
}

export default defineComponent({
  components: {
    CreatedByLine,
    StructuredDataReferenceModal,
    DeleteConfirmationModal,
    GenericName,
    JsonEditorModal,
    Loading,
  },

  props: {
    currentCollectionId: {
      type: Number,
      required: true,
    },
    currentDataObjectId: {
      type: Number,
      required: true,
    },
  },
  data() {
    return {
      structuredDataList: undefined,
      structuredDatas: {},
      currentStructuredDataReference: undefined,
      currentStructuredDataOid: undefined,
      createdAlert: false,
      deletedAlert: false,
    } as StructuredDataListData;
  },
  mounted() {
    this.retrieveReferences();
  },
  methods: {
    retrieveReferences() {
      StructuredDataReferenceService.getAllStructuredDataReferences({
        collectionId: this.currentCollectionId,
        dataObjectId: this.currentDataObjectId,
      })
        .then(response => {
          this.structuredDataList = response;
          this.structuredDataList.forEach(reference => {
            if (reference.id) this.retrieveStructuredDatas(reference.id);
          });
        })
        .catch(e => {
          handleError(
            e as ResponseError,
            "fetching structured data references",
          );
        });
    },

    retrieveStructuredDatas(id: number) {
      StructuredDataReferenceService.getStructuredDataPayload({
        collectionId: this.currentCollectionId,
        dataObjectId: this.currentDataObjectId,
        structureddataReferenceId: id,
      })
        .then(response => {
          const temp: { [key: string]: StructuredDataPayload } = {};
          response.forEach(payload => {
            if (payload?.structuredData?.oid) {
              temp[payload.structuredData.oid] = payload;
            }
          });
          this.structuredDatas = { ...this.structuredDatas, ...temp };
        })
        .catch(e => {
          logError(e as ResponseError, "fetching structured data payload");
        });
    },

    create(newReference: StructuredDataReference) {
      StructuredDataReferenceService.createStructuredDataReference({
        collectionId: this.currentCollectionId,
        dataObjectId: this.currentDataObjectId,
        structuredDataReference: newReference,
      })
        .then(response => {
          this.createdAlert = true;
          this.structuredDataList = [response].concat(
            this.structuredDataList || [],
          );
          if (response.id) this.retrieveStructuredDatas(response.id);
        })
        .catch(e => {
          handleError(e as ResponseError, "creating structured data reference");
        });
    },

    handleDelete() {
      if (!this.currentStructuredDataReference?.id) return;
      StructuredDataReferenceService.deleteStructuredDataReference({
        collectionId: this.currentCollectionId,
        dataObjectId: this.currentDataObjectId,
        structureddataReferenceId: this.currentStructuredDataReference.id,
      })
        .then(() => {
          this.deletedAlert = true;
          this.retrieveReferences();
        })
        .catch(e => {
          handleError(e as ResponseError, "deleting structured data reference");
        });
    },
    convertDate(date: Date | undefined | null) {
      if (date) return new Date(date).toLocaleString("en-GB", dateFormat);
    },
  },
});
</script>
