<script setup lang="ts">
import CreatedByLine from "@/components/generic/CreatedByLine.vue";
import GenericName from "@/components/generic/GenericName.vue";
import Loading from "@/components/generic/Loading.vue";
import BasicReferenceModal from "@/components/references/BasicReferenceModal.vue";
import BasicReferenceModal_Collection from "@/components/references/BasicReferenceModal_Collection.vue";
import CreateCollectionReferenceModal from "@/components/references/CreateCollectionReferenceModal.vue";
import CollectionReferenceService from "@/services/collectionReferenceService";
import { handleError, logError } from "@/utils/error-handling";
import { getQueryParam } from "@/utils/helpers";
import type {
  Collection,
  CollectionReference,
  ResponseError,
} from "@dlr-shepard/shepard-client";
import { getCurrentInstance, nextTick, onMounted, ref } from "vue";

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

const collectionList = ref<CollectionReference[]>();
const referencedList = ref(new Map<number, Collection>());
const currentCollectionReference = ref<CollectionReference>();
const createdAlert = ref(false);
const deletedAlert = ref(false);

function retrieveReferences() {
  CollectionReferenceService.getAllCollectionReferences({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
  })
    .then(response => {
      collectionList.value = response;
      response.forEach(reference => {
        if (reference.id) retrieveCollection(reference.id);
      });
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching collection references");
    })
    .finally(() => {
      currentCollectionReference.value = collectionList.value?.find(e => {
        return e.id === Number(getQueryParam("referenceId"));
      });
      nextTick(() => {
        if (currentCollectionReference.value)
          vm?.proxy.$bvModal.show("view-collection-modal");
      });
    });
}

function retrieveCollection(referenceId: number) {
  CollectionReferenceService.getCollectionReferencePayload({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
    collectionReferenceId: referenceId,
  })
    .then(response => {
      referencedList.value.set(referenceId, response);
      referencedList.value = new Map([...referencedList.value.entries()]);
    })
    .catch(e => {
      logError(e as ResponseError, "fetching collection reference payload");
    });
}

function createReference(newReference: CollectionReference) {
  CollectionReferenceService.createCollectionReference({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
    collectionReference: newReference,
  })
    .then(response => {
      createdAlert.value = true;
      const temp = collectionList.value || [];
      collectionList.value = [...temp, response];
      emit("reference-count-changed", collectionList.value.length);
    })
    .catch(e => {
      handleError(e as ResponseError, "creating collection reference");
    });
}

function deleteReference() {
  if (!currentCollectionReference.value?.id) return;
  CollectionReferenceService.deleteCollectionReference({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
    collectionReferenceId: currentCollectionReference.value.id,
  })
    .then(() => {
      const temp = collectionList.value || [];
      collectionList.value = temp.filter(e => {
        return e.id != currentCollectionReference.value?.id;
      });
      emit("reference-count-changed", collectionList.value.length);
      deletedAlert.value = true;
    })
    .catch(e => {
      handleError(e as ResponseError, "deleting collection reference");
    });
}

onMounted(() => {
  retrieveReferences();
});
</script>

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
      v-b-modal.create-collection-ref-modal
      class="mb-3"
      variant="primary"
    >
      Create new Reference
    </b-button>

    <CreateCollectionReferenceModal
      modal-id="create-collection-ref-modal"
      modal-name="Create Collection Reference"
      @create="createReference($event)"
    />

    <div v-if="collectionList == undefined"><Loading /></div>
    <b-list-group v-else>
      <b-list-group-item
        v-for="(collectionItem, index) in collectionList"
        :key="index"
        v-b-modal.view-collection-modal
        button
        @click="currentCollectionReference = collectionItem"
      >
        <div>
          <b><GenericName :name="collectionItem.name || ''" /></b> | ID:
          {{ collectionItem.id }}
        </div>
        <CreatedByLine
          :created-by="collectionItem.createdBy"
          :created-at="collectionItem.createdAt"
        />
        <small>
          {{ collectionItem.relationship }}:

          <span
            v-if="
              collectionItem.id && collectionItem.referencedCollectionId != -1
            "
          >
            <b-link
              v-if="referencedList.has(collectionItem.id)"
              :to="{
                name: 'Collection',
                params: {
                  collectionId: collectionItem.referencedCollectionId,
                },
              }"
            >
              <b>{{ referencedList.get(collectionItem.id)?.name }}</b> | ID:
              {{ referencedList.get(collectionItem.id)?.id }}
            </b-link>
          </span>
          <span v-else class="text-danger">Collection Deleted</span>
        </small>
      </b-list-group-item>
    </b-list-group>

    <BasicReferenceModal
      v-if="currentCollectionReference?.id"
      modal-id="view-collection-modal"
      :current-collection-id="currentCollectionId"
      :current-data-object-id="currentDataObjectId"
      :reference="currentCollectionReference"
      @delete-reference="deleteReference()"
    >
      <BasicReferenceModal_Collection
        :collection-reference="currentCollectionReference"
        :referenced-collection="
          referencedList.get(currentCollectionReference.id)
        "
      />
    </BasicReferenceModal>
  </div>
</template>
