<script setup lang="ts">
import CreatedByLine from "@/components/generic/CreatedByLine.vue";
import GenericName from "@/components/generic/GenericName.vue";
import Loading from "@/components/generic/Loading.vue";
import BasicReferenceModal from "@/components/references/BasicReferenceModal.vue";
import BasicReferenceModal_URI from "@/components/references/BasicReferenceModal_URI.vue";
import CreateUriReferenceModal from "@/components/references/CreateUriReferenceModal.vue";
import UriReferenceService from "@/services/uriReferenceService";
import { handleError } from "@/utils/error-handling";
import { getQueryParam } from "@/utils/helpers";
import type { ResponseError, URIReference } from "@dlr-shepard/shepard-client";
import { getCurrentInstance, onMounted, ref } from "vue";

const props = defineProps({
  currentCollectionId: {
    type: Number,
    required: true,
  },
  currentDataObjectId: {
    type: Number,
    required: true,
  },
});

const vm = getCurrentInstance();

const emit = defineEmits(["reference-count-changed"]);

const uriList = ref<URIReference[]>();
const currentUriReference = ref<URIReference>();
const createdAlert = ref(false);
const deletedAlert = ref(false);

function retrieveReferences() {
  UriReferenceService.getAllUriReferences({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
  })
    .then(response => {
      uriList.value = response;
      emit("reference-count-changed", uriList.value.length);
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching URI references");
    })
    .finally(() => {
      currentUriReference.value = uriList.value?.find(e => {
        return e.id === Number(getQueryParam("referenceId"));
      });
      if (currentUriReference.value) vm?.proxy.$bvModal.show("view-uri-modal");
    });
}

function createReference(newReference: URIReference) {
  UriReferenceService.createUriReference({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
    uRIReference: newReference,
  })
    .then(response => {
      createdAlert.value = true;
      const temp = uriList.value || [];
      uriList.value = [...temp, response];
      emit("reference-count-changed", uriList.value.length);
    })
    .catch(e => {
      handleError(e as ResponseError, "creating URI reference");
    });
}

function deleteReference() {
  if (!currentUriReference.value?.id) return;
  UriReferenceService.deleteUriReference({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
    uriReferenceId: currentUriReference.value.id,
  })
    .then(() => {
      const temp = uriList.value || [];
      uriList.value = temp.filter(e => {
        return e.id != currentUriReference.value?.id;
      });
      emit("reference-count-changed", uriList.value.length);
      deletedAlert.value = true;
    })
    .catch(e => {
      handleError(e as ResponseError, "deleting URI reference");
    });
}

onMounted(() => {
  retrieveReferences();
});
</script>

<template>
  <div>
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

    <b-button v-b-modal.create-uri-ref-modal class="mb-3" variant="primary">
      Create new Reference
    </b-button>

    <CreateUriReferenceModal
      modal-id="create-uri-ref-modal"
      modal-name="Create URI Reference"
      @create="createReference($event)"
    />

    <div v-if="uriList == undefined"><Loading /></div>
    <b-list-group v-else>
      <b-list-group-item
        v-for="(uriItem, index) in uriList"
        :key="index"
        v-b-modal.view-uri-modal
        button
        @click="currentUriReference = uriItem"
      >
        <div>
          <b><GenericName :name="uriItem.name || ''" /></b> | ID:
          {{ uriItem.id }}
        </div>
        <CreatedByLine
          :created-by="uriItem.createdBy"
          :created-at="uriItem.createdAt"
        />
        <b-link :href="uriItem.uri" target="_blank">{{ uriItem.uri }}</b-link>
      </b-list-group-item>
    </b-list-group>

    <BasicReferenceModal
      v-if="currentUriReference"
      modal-id="view-uri-modal"
      :modal-name="currentUriReference?.name || undefined"
      :current-collection-id="currentCollectionId"
      :current-data-object-id="currentDataObjectId"
      :reference="currentUriReference"
      @delete-reference="deleteReference()"
    >
      <BasicReferenceModal_URI :uri-reference="currentUriReference" />
    </BasicReferenceModal>
  </div>
</template>
