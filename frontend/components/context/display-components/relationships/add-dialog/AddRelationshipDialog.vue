<script setup lang="ts">
import RelationshipInput from "~/components/context/input-components/relationship/RelationshipInput.vue";

import { useCreateReferences } from "~/composables/references/useCreateReferences";
import { useUpdateDataObjectRelationship } from "~/composables/references/useUpdateDataObjectPredecessor";
import {
  REFERENCE_PREDICATE,
  fetchReferencePrefillAnnotations,
  findAnnotationByPredicate,
  parseUriRelationshipHint,
} from "~/composables/references/useReferenceTemplatePrefill";
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

// REF-EDIT-TPL-6 — template-driven URI prefill. On dialog open, fetch the
// parent DataObject's annotations and resolve any
// `urn:shepard:reference:uriRelationship` hint into a default relationship
// label + uri placeholder. The hints flow into `RelationshipInput` as props;
// the user can override before submit.
const defaultUriRelationship = ref<string | undefined>(undefined);
const uriPlaceholder = ref<string | undefined>(undefined);

async function loadUriRelationshipHint(): Promise<void> {
  const annotations = await fetchReferencePrefillAnnotations(
    props.collectionId,
    props.dataObjectId,
  );
  const annotation = findAnnotationByPredicate(
    annotations,
    REFERENCE_PREDICATE.URI_RELATIONSHIP,
  );
  const hint = parseUriRelationshipHint(annotation);
  if (!hint) return;
  if (hint.relationship) defaultUriRelationship.value = hint.relationship;
  if (hint.uriPrefix) uriPlaceholder.value = hint.uriPrefix;
}

watch(
  showDialog,
  open => {
    if (!open) return;
    defaultUriRelationship.value = undefined;
    uriPlaceholder.value = undefined;
    void loadUriRelationshipHint();
  },
  { immediate: true },
);

const { addPredecessor, addSuccessor, loading } =
  useUpdateDataObjectRelationship(
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
    addSuccessor(
      props.dataObjectId,
      relationshipModel.value.relatedDataObjectId,
    );
  }

  if (
    relationshipModel.value.type === CustomRelationshipType.COLLECTION &&
    relationshipModel.value.referenceName &&
    relationshipModel.value.referencedCollectionId
  ) {
    addCollectionReference(
      relationshipModel.value.referencedCollectionId,
      relationshipModel.value.referenceName,
      relationshipModel.value.relationshipName,
    );
  }

  if (
    relationshipModel.value.type === CustomRelationshipType.DATA_OBJECT &&
    relationshipModel.value.referencedDataObjectId &&
    relationshipModel.value.referenceName
  ) {
    addDataObjectReference(
      relationshipModel.value.referencedDataObjectId,
      relationshipModel.value.referenceName,
      relationshipModel.value.relationshipName,
    );
  }

  if (
    relationshipModel.value.type === CustomRelationshipType.URI &&
    relationshipModel.value.referenceURI &&
    relationshipModel.value.referenceName
  ) {
    addUriReference(
      relationshipModel.value.referenceURI,
      relationshipModel.value.referenceName,
      relationshipModel.value.relationshipName,
    );
  }
};
</script>

<template>
  <FormDialog
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
          :default-uri-relationship="defaultUriRelationship"
          :uri-placeholder="uriPlaceholder"
        />
      </v-form>
    </template>
  </FormDialog>
</template>
