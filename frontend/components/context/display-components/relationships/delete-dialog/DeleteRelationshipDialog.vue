<script setup lang="ts">
import { useDeleteReferences } from "~/composables/references/useDeleteReferences";
import { useUpdateDataObjectRelationship } from "~/composables/references/useUpdateDataObjectPredecessor";
import type { RelationshipTableElement } from "../relationshipTableElement";

interface DeleteRelationshipDialogProps {
  collectionId: number;
  dataObjectId: number;
  tableElement: RelationshipTableElement | undefined;
}

const props = defineProps<DeleteRelationshipDialogProps>();

const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});

const {
  deleteCollectionReference,
  deleteDataObjectReference,
  deleteReferenceV2,
  deleteUriReference,
  deleteUriReferenceV2,
} = useDeleteReferences(props.collectionId, props.dataObjectId, () => {
  showDialog.value = false;
});

const { deletePredecessor, deleteSuccessor } = useUpdateDataObjectRelationship(
  props.collectionId,
  () => {
    showDialog.value = false;
  },
);

async function deleteRelationship() {
  if (!props.tableElement) {
    return;
  }

  const referenceId = props.tableElement.information.referenceId;

  switch (props.tableElement.information.type.type) {
    case "Link": {
      // V2-SWEEP-004-3: prefer v2 delete (appId-keyed) when the table row has one.
      const uriAppId = props.tableElement.actions.uriRefAppId;
      if (uriAppId) {
        deleteUriReferenceV2(uriAppId);
      } else {
        deleteUriReference(referenceId);
      }
      break;
    }
    case "Collection Reference": {
      // APISIMP-DELETE-REFS-V2: prefer v2 delete (appId-keyed) when available.
      const colRefAppId = props.tableElement.information.referenceAppId;
      if (colRefAppId) {
        deleteReferenceV2(colRefAppId);
      } else {
        deleteCollectionReference(referenceId);
      }
      break;
    }
    case "Data Object Reference": {
      // APISIMP-DELETE-REFS-V2: prefer v2 delete (appId-keyed) when available.
      const doRefAppId = props.tableElement.information.referenceAppId;
      if (doRefAppId) {
        deleteReferenceV2(doRefAppId);
      } else {
        deleteDataObjectReference(referenceId);
      }
      break;
    }
    case "Data Object": {
      if (props.tableElement.relationship === "Successor") {
        deleteSuccessor(props.dataObjectId, referenceId);
      } else if (props.tableElement.relationship === "Predecessor") {
        deletePredecessor(props.dataObjectId, referenceId);
      } else {
        throw Error("Unknown DataObject relationship");
      }
      break;
    }
    default: {
      throw Error("Unknown Relation/ Reference Type");
    }
  }
}
</script>

<template>
  <ConfirmDeleteDialog
    v-model:show-dialog="showDialog"
    @confirmed="deleteRelationship"
  />
</template>
