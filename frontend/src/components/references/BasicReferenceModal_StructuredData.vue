<script setup lang="ts">
import GenericName from "@/components/generic/GenericName.vue";
import JsonStructuredDataModal from "@/components/references/JsonStructuredDataModal.vue";
import type {
  ResponseError,
  StructuredDataPayload,
  StructuredDataReference,
} from "@/generated/openapi";
import StructuredDataReferenceService from "@/services/structuredDataReferenceService";
import { logError } from "@/utils/error-handling";
import { convertDate } from "@/utils/helpers";
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
const structuredDatas = ref(new Map<string, StructuredDataPayload>());

function getStructuredDataPayload() {
  if (!props.structuredDataReference.id) return;
  StructuredDataReferenceService.getStructuredDataPayload({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
    structuredDataReferenceId: props.structuredDataReference.id,
  })
    .then(response => {
      response.forEach(payload => {
        if (payload?.structuredData?.oid) {
          structuredDatas.value.set(payload.structuredData.oid, payload);
        }
      });
      structuredDatas.value = new Map([...structuredDatas.value.entries()]);
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
          name: 'StructuredData',
          params: {
            structuredDataId: structuredDataReference.structuredDataContainerId,
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
        <div v-if="structuredDatas.get(oid)">
          <b>
            <GenericName
              :word-count="30"
              :name="structuredDatas.get(oid)?.structuredData?.name || ''"
            />
          </b>
          | Oid:
          {{ oid }}
          <span
            v-if="
              structuredDataReference?.structuredDataContainerId == -1 ||
              !structuredDatas.get(oid)?.payload
            "
          >
            | <span class="text-danger"> Unavailable </span>
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
            v-else-if="!structuredDatas.get(oid)?.payload"
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
        <div v-if="structuredDatas.get(oid)?.structuredData?.createdAt">
          created at:
          {{ convertDate(structuredDatas.get(oid)?.structuredData?.createdAt) }}
        </div>
      </b-list-group-item>
    </b-list-group>
    <JsonStructuredDataModal
      v-if="structuredDataReference && currentStructuredDataOid"
      modal-id="json-structured-data-modal"
      modal-name="Structured Data Reference"
      :container-id="structuredDataReference.structuredDataContainerId"
      :oid="currentStructuredDataOid"
    />
  </div>
</template>
