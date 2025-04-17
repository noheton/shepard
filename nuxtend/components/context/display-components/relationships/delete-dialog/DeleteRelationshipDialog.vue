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
  deleteUriReference,
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
      deleteUriReference(referenceId);
      break;
    }
    case "Collection Reference": {
      deleteCollectionReference(referenceId);
      break;
    }
    case "Data Object Reference": {
      deleteDataObjectReference(referenceId);
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
