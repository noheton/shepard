<script setup lang="ts">
import CollectionModal from "@/components/dataobjects/CollectionModal.vue";
import DataObjectList from "@/components/dataobjects/DataObjectList.vue";
import DataObjectModal from "@/components/dataobjects/DataObjectModal.vue";
import SemanticAnnotationModal from "@/components/dataobjects/SemanticAnnotationModal.vue";
import DeleteConfirmationModal from "@/components/DeleteConfirmationModal.vue";
import CreatedByLine from "@/components/generic/CreatedByLine.vue";
import GenericCollapse from "@/components/generic/GenericCollapse.vue";
import GenericDescription from "@/components/generic/GenericDescription.vue";
import SemanticBadge from "@/components/generic/SemanticBadge.vue";
import PermissionsModal from "@/components/PermissionsModal.vue";
import CollectionService from "@/services/collectionService";
import {
  default as semanticAnnotationService,
  default as SemanticAnnotationService,
} from "@/services/semanticAnnotationService";
import { downloadFile } from "@/utils/download";
import { handleError, logError } from "@/utils/error-handling";
import type {
  Collection,
  Permissions,
  ResponseError,
  Roles,
  SemanticAnnotation,
} from "@dlr-shepard/shepard-client";
import { useTitle } from "@vueuse/core";
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue2-helpers/vue-router";
import CurrentRoleIcon from "../components/generic/CurrentRoleIcon.vue";

const router = useRouter();
const currentCollectionId = computed(() => {
  return Number(router.currentRoute.params.collectionId);
});
const editPermissions = computed(() => {
  return roles.value != undefined && (roles.value.owner || roles.value.writer);
});
const managePermissions = computed(() => {
  return roles.value != undefined && (roles.value.owner || roles.value.manager);
});

const currentCollection = ref<Collection | undefined>();
const attributeItems = ref<Array<{ key: string; value: string }>>([]);
function retrieveCollection() {
  CollectionService.getCollection({
    collectionId: currentCollectionId.value,
  })
    .then(response => {
      currentCollection.value = response;
      attributeItems.value = [];
      if (currentCollection.value.attributes !== undefined) {
        Object.entries(currentCollection.value.attributes).forEach(
          ([key, value]) =>
            attributeItems.value.push({ key: key, value: value }),
        );
      }
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching collection");
    });
}

function exportCollection() {
  const filename = (currentCollection.value?.name || "export")
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9 -]/g, "")
    .replace(" ", "_");
  CollectionService.exportCollection({
    collectionId: currentCollectionId.value,
  })
    .then(response => {
      downloadFile(response, filename + ".zip");
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching file");
    });
}

const permissions = ref<Permissions | undefined>();
function retrievePermissions() {
  CollectionService.getCollectionPermissions({
    collectionId: currentCollectionId.value,
  })
    .then(response => {
      permissions.value = response;
    })
    .catch(e => {
      logError(e as ResponseError, "fetching permissions");
    });
}
function updatePermissions(perms: Permissions) {
  CollectionService.editCollectionPermissions({
    collectionId: currentCollectionId.value,
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
  CollectionService.getCollectionRoles({
    collectionId: currentCollectionId.value,
  })
    .then(response => {
      roles.value = response;
    })
    .catch(e => {
      logError(e as ResponseError, "fetching roles");
    });
}

function handleDelete() {
  CollectionService.deleteCollection({
    collectionId: currentCollectionId.value,
  })
    .then(() => {
      router.push({ name: "Explore" });
    })
    .catch(e => {
      handleError(e as ResponseError, "deleting collection");
    });
}

const title = computed(() => {
  return currentCollection.value?.name || "Collection";
});
function updateTitle() {
  useTitle(title, {
    titleTemplate: "%s | shepard",
  });
}

const collectionAnnotationList = ref<SemanticAnnotation[]>();
function getAllCollectionAnnotations() {
  SemanticAnnotationService.getAllCollectionAnnotations({
    collectionId: +currentCollectionId.value,
  })
    .then(annotationList => {
      collectionAnnotationList.value = annotationList;
    })
    .catch(e => {
      handleError(
        e as ResponseError,
        "get all semantic collection annotations",
      );
    });
}

function createCollectionAnnotation(semanticAnnotation: SemanticAnnotation) {
  semanticAnnotationService
    .createCollectionAnnotation({
      collectionId: currentCollectionId.value,
      semanticAnnotation: semanticAnnotation,
    })
    .then(newAnnotation => {
      const temp = collectionAnnotationList.value || [];
      collectionAnnotationList.value = [...temp, newAnnotation];
    })
    .catch(e => {
      handleError(
        e as ResponseError,
        "creating semantic collection annotation",
      );
    });
}
function deleteCollectionAnnotation(semanticAnnotationId: number) {
  SemanticAnnotationService.deleteCollectionAnnotation({
    collectionId: +currentCollectionId.value,
    semanticAnnotationId: semanticAnnotationId,
  })
    .then(() => {
      if (!collectionAnnotationList.value) return;
      const temp = collectionAnnotationList.value.filter(a => {
        return a.id != semanticAnnotationId;
      });
      collectionAnnotationList.value = temp;
    })
    .catch(e => {
      handleError(
        e as ResponseError,
        "deleting semantic collection annotation",
      );
    });
}

onMounted(() => {
  retrieveCollection();
  retrieveRoles();
  updateTitle();
  getAllCollectionAnnotations();
});
</script>

<template>
  <div v-if="currentCollection" class="view">
    <div>
      <b-button-group class="float-right">
        <b-button
          v-if="editPermissions"
          v-b-modal.create-data-object-modal
          v-b-tooltip.hover
          title="Create"
          variant="primary"
        >
          <CreateIcon />
        </b-button>
        <b-button
          v-b-tooltip.hover
          to="graph"
          append
          title="Graph"
          variant="secondary"
        >
          <GraphIcon />
        </b-button>
        <b-button
          v-b-tooltip.hover
          append
          title="Export"
          variant="secondary"
          @click="exportCollection()"
        >
          <DownloadIcon />
        </b-button>
        <b-button
          v-if="editPermissions"
          v-b-modal.edit-collection-modal
          v-b-tooltip.hover
          title="Edit"
          variant="secondary"
        >
          <EditIcon />
        </b-button>
        <b-button
          v-if="editPermissions"
          v-b-modal.edit-semantic-modal
          v-b-tooltip.hover
          title="Edit Semantic Annotation"
          variant="secondary"
        >
          <SemanticIcon />
        </b-button>
        <b-button
          v-if="managePermissions"
          v-b-modal.permissions-modal
          v-b-tooltip.hover
          title="Edit Permissions"
          variant="secondary"
          @click="retrievePermissions()"
        >
          <PermissionsIcon />
        </b-button>
        <b-button
          v-if="editPermissions"
          v-b-modal.delete-confirmation-modal
          v-b-tooltip.hover
          title="Delete"
          variant="info"
        >
          <DeleteIcon />
        </b-button>
      </b-button-group>
      <h3 class="title">
        {{ currentCollection.name }}
        <CurrentRoleIcon :roles="roles" />
      </h3>
    </div>
    <div class="mb-3">
      Collection ID: {{ currentCollection.id }}
      <CreatedByLine
        :created-at="currentCollection.createdAt"
        :created-by="currentCollection.createdBy"
        tooltip
      />
      <CreatedByLine
        v-if="currentCollection.updatedAt && currentCollection.updatedBy"
        :created-at="currentCollection.updatedAt"
        :created-by="currentCollection.updatedBy"
        updated
        tooltip
      />
    </div>

    <SemanticBadge
      v-if="collectionAnnotationList"
      class="mb-3"
      :annotation-list="collectionAnnotationList"
    />

    <GenericDescription
      v-if="currentCollection.description"
      class="mb-3"
      :text="currentCollection.description"
    />

    <GenericCollapse
      v-if="attributeItems.length"
      class="mb-3"
      title="Attributes"
    >
      <b-table striped small :items="attributeItems"> </b-table>
    </GenericCollapse>

    <GenericCollapse
      v-if="currentCollection.dataObjectIds?.length"
      class="mb-3"
      title="Data Objects"
    >
      <DataObjectList
        v-if="currentCollection.id"
        :current-collection-id="currentCollection.id"
        :parent-id="-1"
      />
    </GenericCollapse>

    <CollectionModal
      :current-collection="currentCollection"
      modal-id="edit-collection-modal"
      modal-name="Edit Collection"
      @collection-changed="retrieveCollection()"
    />
    <DataObjectModal
      :current-collection-id="currentCollectionId"
      modal-id="create-data-object-modal"
      modal-name="Create Data Object"
    />
    <SemanticAnnotationModal
      v-if="currentCollection.id"
      modal-id="edit-semantic-modal"
      :annotation-list="collectionAnnotationList"
      @create="createCollectionAnnotation($event)"
      @delete="deleteCollectionAnnotation($event)"
    />
    <DeleteConfirmationModal
      modal-id="delete-confirmation-modal"
      modal-name="Confirm to delete collection"
      :modal-text="
        'Do you really want do delete the collection with name ' +
        currentCollection.name +
        '?'
      "
      @confirmation="handleDelete()"
    />
    <PermissionsModal
      modal-id="permissions-modal"
      modal-name="Edit Permissions"
      :entity-id="currentCollectionId"
      :old-permissions="permissions"
      @update="updatePermissions($event)"
    />
  </div>
</template>
