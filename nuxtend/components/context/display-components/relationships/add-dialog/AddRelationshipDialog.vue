<script setup lang="ts">
import RelationshipInput from "~/components/context/input-components/relationship/RelationshipInput.vue";

import { useCreateReferences } from "~/composables/references/useCreateReferences";
import { useUpdateDataObjectPredecessor } from "~/composables/references/useUpdateDataObjectPredecessor";
import {
  CustomRelationshipType,
  isValidCollectionReference,
  isValidDataObjectReference,
  isValidPredecessorOrSuccessorReference,
  isValidUriReference,
  RelationshipType,
  type ReferenceData,
} from "./relationshipTypes";

const props = defineProps<{ collectionId: number; dataObjectId: number }>();
const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});

const relationshipModel = ref<ReferenceData>();
const isValid = ref<boolean>(false);

const { addPredecessor, loading } = useUpdateDataObjectPredecessor(
  props.collectionId,
  () => (showDialog.value = false),
);

const { addCollectionReference, addDataObjectReference, addUriReference } =
  useCreateReferences(
    props.collectionId,
    props.dataObjectId,
    () => (showDialog.value = false),
    loading,
  );

function validateRelationship(newRelationship: ReferenceData | undefined) {
  if (!newRelationship) isValid.value = false;
  else {
    if (
      isValidPredecessorOrSuccessorReference(newRelationship) ||
      isValidCollectionReference(newRelationship) ||
      isValidDataObjectReference(newRelationship) ||
      isValidUriReference(newRelationship)
    ) {
      isValid.value = true;
      return;
    }
  }
  isValid.value = false;
}

watchEffect(() => validateRelationship(relationshipModel.value));

const onSubmit = () => {
  if (!isValid.value) return;
  if (!relationshipModel.value) return;
  if (props.dataObjectId < 0 || props.collectionId < 0) return;

  if (relationshipModel.value.type === RelationshipType.PREDECESSOR) {
    addPredecessor(
      props.dataObjectId,
      relationshipModel.value.relatedDataObjectId,
    );
  }

  if (relationshipModel.value.type === RelationshipType.SUCCESSOR) {
    addPredecessor(
      relationshipModel.value.relatedDataObjectId,
      props.dataObjectId,
    );
  }

  // Due to the 'isValid' check, we can assert these values as non-null ('!')
  if (relationshipModel.value.type === CustomRelationshipType.COLLECTION) {
    addCollectionReference(
      relationshipModel.value.referencedCollectionId!,
      relationshipModel.value.referenceName!,
      relationshipModel.value.relationshipName,
    );
  }

  if (relationshipModel.value.type === CustomRelationshipType.DATA_OBJECT) {
    addDataObjectReference(
      relationshipModel.value.referencedDataObjectId!,
      relationshipModel.value.referenceName!,
      relationshipModel.value.relationshipName,
    );
  }

  if (relationshipModel.value.type === CustomRelationshipType.URI) {
    addUriReference(
      relationshipModel.value.referenceURI!,
      relationshipModel.value.referenceName!,
    );
  }
};
</script>

<template>
  <Dialog
    v-if="showDialog"
    v-model:show-dialog="showDialog"
    title="Add New Relationship"
    save-button-test="Save"
    :max-width="600"
    :loading="loading"
    :submit-disabled="!isValid"
    @submit="onSubmit"
  >
    <template #form>
      <v-form class="px-2 pt-6">
        <RelationshipInput
          v-model="relationshipModel"
          :collection-id="collectionId"
        />
      </v-form>
    </template>
  </Dialog>
</template>
