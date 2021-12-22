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
      variant="danger"
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

    <b-list-group>
      <b-list-group-item
        v-for="(structuredDataReference, index) in structuredDataList"
        :key="index"
      >
        <div>
          <b><GenericName :name="structuredDataReference.name" /></b>
          | ID: {{ structuredDataReference.id }} |
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
          <b-button
            v-b-modal.structured-data-reference-delete-confirmation-modal
            v-b-tooltip.hover
            class="float-right"
            title="Delete"
            variant="dark"
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
          <small v-if="structuredDatas[oid]">
            <div v-if="structuredDatas[oid].structuredData.createdAt">
              <b>Oid:</b> {{ oid }} | <b>Name:</b>
              {{ structuredDatas[oid].structuredData.name }} |
              <b>Created at:</b>
              {{
                new Date(
                  structuredDatas[oid].structuredData.createdAt,
                ).toLocaleString()
              }}
            </div>
            <div v-else><b>Oid:</b> {{ oid }}</div>

            <b-link @click="toggleReadMore(oid)">
              <span v-if="readMore[oid]"><CollapsIcon /></span>
              <span v-else><ExtendIcon /></span>
            </b-link>
            <b>Payload:</b>
            <b-link title="Copy" class="ml-1" @click="copyPayload(oid)">
              <CopyIcon :size="15" />
            </b-link>

            <span v-if="structuredDatas[oid].payload">
              <span v-if="readMore[oid]">
                <pre class="payload">{{
                  structuredDatas[oid].payload | pretty
                }}</pre>
              </span>
            </span>
            <span v-else>Payload is missing</span>
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
      @confirmation="handleDelete(currentStructuredDataReference.id)"
    />
  </div>
</template>

<script lang="ts">
import DeleteConfirmationModal from "@/components/DeleteConfirmationModal.vue";
import CreatedByLine from "@/components/generic/CreatedByLine.vue";
import GenericName from "@/components/generic/GenericName.vue";
import StructuredDataReferenceModal from "@/components/references/StructuredDataReferenceModal.vue";
import { StructuredDataReferenceVue } from "@/utils/api-mixin";
import { emitter } from "@/utils/event-bus";
import {
  StructuredDataPayload,
  StructuredDataReference,
} from "@dlr-shepard/shepard-client";
import Vue, { VueConstructor } from "vue";

interface StructuredDataListData {
  structuredDataList: StructuredDataReference[];
  structuredDatas: { [key: string]: StructuredDataPayload };
  currentStructuredDataReference?: StructuredDataReference;
  createdAlert: boolean;
  deletedAlert: boolean;
  readMore: { [key: string]: boolean };
}

export default (
  Vue as VueConstructor<Vue & InstanceType<typeof StructuredDataReferenceVue>>
).extend({
  components: {
    CreatedByLine,
    StructuredDataReferenceModal,
    DeleteConfirmationModal,
    GenericName,
  },
  filters: {
    pretty: function (value: string) {
      return JSON.stringify(JSON.parse(value), null, 2);
    },
  },

  mixins: [StructuredDataReferenceVue],
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
      structuredDataList: [],
      structuredDatas: {},
      currentStructuredDataReference: undefined,
      createdAlert: false,
      deletedAlert: false,
      readMore: {},
    } as StructuredDataListData;
  },
  mounted() {
    this.retrieveReferences();
  },
  methods: {
    retrieveReferences() {
      this.structuredDataReferenceApi
        ?.getAllStructuredDataReferences({
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
          const error =
            "Error while fetching structured data references: " + e.statusText;
          console.log(error);
        });
    },

    retrieveStructuredDatas(id: number) {
      this.structuredDataReferenceApi
        ?.getStructuredDataPayload({
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
          const error =
            "Error while fetching structured data payload: " + e.statusText;
          console.log(error);
        });
    },

    create(newReference: StructuredDataReference) {
      this.structuredDataReferenceApi
        ?.createStructuredDataReference({
          collectionId: this.currentCollectionId,
          dataObjectId: this.currentDataObjectId,
          structuredDataReference: newReference,
        })
        .then(response => {
          this.createdAlert = true;
          this.structuredDataList = [response].concat(this.structuredDataList);
          if (response.id) this.retrieveStructuredDatas(response.id);
        })
        .catch(e => {
          const error =
            "Error while creating structured data reference: " + e.statusText;
          console.log(error);
          emitter.emit("error", error);
        });
    },

    handleDelete(structureddataReferenceId: number) {
      this.structuredDataReferenceApi
        ?.deleteStructuredDataReference({
          collectionId: this.currentCollectionId,
          dataObjectId: this.currentDataObjectId,
          structureddataReferenceId: structureddataReferenceId,
        })
        .then(() => {
          this.deletedAlert = true;
          this.retrieveReferences();
        })
        .catch(e => {
          const error =
            "Error while deleting structured data reference: " + e.statusText;
          console.log(error);
          emitter.emit("error", error);
        });
    },
    copyPayload(oid: string) {
      const payload = this.structuredDatas[oid].payload;
      if (payload) navigator.clipboard.writeText(payload);
    },
    toggleReadMore(oid: string) {
      this.readMore[oid] = !this.readMore[oid];
      this.readMore = { ...this.readMore };
    },
  },
});
</script>

<style scoped>
.payload {
  color: #e83e8c;
}
</style>
