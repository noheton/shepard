<script setup lang="ts">
import GenericName from "@/components/generic/GenericName.vue";
import JsonStructruedDataModal from "@/components/references/JsonStructruedDataModal.vue";
import StructuredDataReferenceService from "@/services/structuredDataReferenceService";
import { logError } from "@/utils/error-handling";
import { convertDate } from "@/utils/helpers";
import type {
  ResponseError,
  StructuredDataPayload,
  StructuredDataReference,
} from "@dlr-shepard/shepard-client";
import { onMounted, ref, type PropType } from "vue";

const props = defineProps({
  currentCollectionId: {
    type: Number,
    required: true,
  },
  currentDataObjectId: {
    type: Number,
    required: true,
  },
  structuredDataReference: {
    type: Object as PropType<StructuredDataReference>,
    required: true,
  },
});

const currentStructuredDataOid = ref<string>();
const structuredDatas = ref<{ [key: string]: StructuredDataPayload }>({});

function getStructuredDataPayload() {
  if (!props.structuredDataReference.id) return;
  StructuredDataReferenceService.getStructuredDataPayload({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
    structureddataReferenceId: props.structuredDataReference.id,
  })
    .then(response => {
      const temp: { [key: string]: StructuredDataPayload } = {};
      response.forEach(payload => {
        if (payload?.structuredData?.oid) {
          temp[payload.structuredData.oid] = payload;
        }
      });
      structuredDatas.value = { ...structuredDatas.value, ...temp };
    })
    .catch(e => {
      logError(e as ResponseError, "fetching structured data");
    });
}

onMounted(() => {
  getStructuredDataPayload();
  currentStructuredDataOid.value = undefined;
});
</script>

<template>
  <div>
    <span v-if="structuredDataReference.structuredDataContainerId != -1">
      <b-link
        :to="{
          name: 'Files',
          params: {
            fileId: structuredDataReference.structuredDataContainerId,
          },
        }"
      >
        Container: {{ structuredDataReference.structuredDataContainerId }}
      </b-link>
    </span>
    <span v-else class="text-danger">Container: Deleted</span>

    <b-list-group class="list">
      <b-list-group-item
        v-for="(oid, index) in structuredDataReference?.structuredDataOids"
        :key="index"
      >
        <div v-if="structuredDatas[oid]">
          <b>
            <GenericName
              :word-count="30"
              :name="structuredDatas[oid]?.structuredData?.name || ''"
            />
          </b>
          | Oid:
          {{ oid }}
          <span
            v-if="
              structuredDataReference?.structuredDataContainerId == -1 ||
              !structuredDatas[oid]?.payload
            "
          >
            | <span class="text-danger"> Deleted </span>
          </span>

          <!-- Container deleted -->
          <b-button
            v-if="structuredDataReference.structuredDataContainerId == -1"
            class="float-right"
            variant="primary"
            :disabled="true"
          >
            <EyeIcon />
          </b-button>
          <!-- Payload deleted -->
          <b-button
            v-else-if="!structuredDatas[oid]?.payload"
            class="float-right"
            variant="primary"
            :disabled="true"
          >
            <EyeIcon />
          </b-button>
          <!-- Nothing deleted -->
          <b-button
            v-else
            v-b-modal.json-structured-data-modal
            v-b-tooltip.hover
            class="float-right"
            variant="primary"
            title="Show Viewer"
            @click="currentStructuredDataOid = oid"
          >
            <EyeIcon />
          </b-button>
        </div>
        <div v-if="structuredDatas[oid]?.structuredData?.createdAt">
          created at:
          {{ convertDate(structuredDatas[oid]?.structuredData?.createdAt) }}
        </div>
      </b-list-group-item>
    </b-list-group>
    <JsonStructruedDataModal
      v-if="structuredDataReference && currentStructuredDataOid"
      modal-id="json-structured-data-modal"
      modal-name="Structured Data Reference"
      :container-id="structuredDataReference.structuredDataContainerId"
      :oid="currentStructuredDataOid"
    />
  </div>
</template>
