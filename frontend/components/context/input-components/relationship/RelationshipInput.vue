<script setup lang="ts">
import Select from "~/components/common/Select.vue";
import {
  CustomRelationshipType,
  RelationshipType,
  type CollectionReferenceData,
  type DataObjectReferenceData,
  type ReferenceData,
  type URIReferenceData,
} from "../../display-components/relationships/add-dialog/relationshipTypes";
import { naturalSort } from "~/utils/naturalSort";

// UIRULE-DROPDOWN-SEARCH-SORT: relationship-target kinds in natural order.
const customRelationshipTypeItems = naturalSort(Object.values(CustomRelationshipType));

const props = defineProps<{
  collectionId: number;
  /** UUID v7 — when supplied, DataObject search uses GET /v2/search (SEARCH-V2-3). */
  collectionAppId?: string;
  /**
   * REF-EDIT-TPL-6 — Optional template-driven defaults for the URI sub-form.
   * `defaultUriRelationship` pre-fills the relationship label when the URI
   * mode is first selected. `uriPlaceholder` shapes the URI input's empty
   * label. Neither overrides a value the user has already typed.
   */
  defaultUriRelationship?: string;
  uriPlaceholder?: string;
}>();
const relationshipModel = defineModel<ReferenceData>();

const relationshipType = ref<RelationshipType>(RelationshipType.PREDECESSOR);

const inputLabel = computed(() => {
  if (relationshipType.value === RelationshipType.PREDECESSOR)
    return "Predecessor Name or ID...";
  else if (relationshipType.value == RelationshipType.SUCCESSOR)
    return "Successor Name or ID...";
  else return "";
});

// Create refs to be used as models in the different reference components: Collection, DataObject, Uri
const customRelationshipType = ref<CustomRelationshipType>();
const collectionReference = ref<CollectionReferenceData>({
  relationshipName: "Collection",
  type: CustomRelationshipType.COLLECTION,
});
const dataobjectReference = ref<DataObjectReferenceData>({
  relationshipName: "Data Object",
  type: CustomRelationshipType.DATA_OBJECT,
});
const uriReference = ref<URIReferenceData>({
  // REF-EDIT-TPL-6 — seed relationship from template hint if present, else
  // fall back to the legacy "URI" default. The user can override.
  relationshipName: props.defaultUriRelationship ?? "URI",
  type: CustomRelationshipType.URI,
});

// REF-EDIT-TPL-6 — when the template hint resolves after first render (async
// fetch), apply it once as long as the user has not already typed.
watch(
  () => props.defaultUriRelationship,
  hint => {
    if (!hint) return;
    const current = uriReference.value.relationshipName?.trim() ?? "";
    if (current.length === 0 || current === "URI") {
      uriReference.value.relationshipName = hint;
    }
  },
);

// watch on changes of the refs and update model accordingly
watch(collectionReference.value, async changedCollectionReference => {
  if (changedCollectionReference) {
    relationshipModel.value = {
      ...changedCollectionReference,
      type: CustomRelationshipType.COLLECTION,
    };
  }
});

watch(dataobjectReference.value, async changedDataObjectReference => {
  if (changedDataObjectReference) {
    relationshipModel.value = {
      ...changedDataObjectReference,
      type: CustomRelationshipType.DATA_OBJECT,
    };
  }
});

watch(uriReference.value, async changedUriReference => {
  if (changedUriReference) {
    relationshipModel.value = {
      ...changedUriReference,
      type: CustomRelationshipType.URI,
    };
  }
});

watch(relationshipType, newType => {
  // handle relationship type changes after user changed selection
  if (relationshipModel.value) {
    if (
      newType === RelationshipType.PREDECESSOR ||
      newType === RelationshipType.SUCCESSOR
    )
      relationshipModel.value.type = newType;
  }
});
</script>

<template>
  <v-row class="text-textbody1 text-subtitle-2 pl-1 pb-3">
    Relationship Type:
  </v-row>
  <v-row>
    <v-radio-group v-model="relationshipType">
      <v-radio label="Predecessor" :value="RelationshipType.PREDECESSOR" />
      <v-radio label="Successor" :value="RelationshipType.SUCCESSOR" />
      <v-radio label="Custom" :value="RelationshipType.CUSTOM" />
    </v-radio-group>
  </v-row>

  <v-row>
    <v-divider color="text-divider1" :thickness="2" />
  </v-row>

  <div v-if="relationshipType !== RelationshipType.CUSTOM">
    <v-row class="pt-10">
      <DataObjectAutocomplete
        :collection-id="collectionId"
        :collection-app-id="collectionAppId"
        :input-label="inputLabel"
        @search-ended="
          value => {
            if (value && relationshipType != RelationshipType.CUSTOM) {
              relationshipModel = {
                relatedDataObjectId: value.id,
                type: relationshipType,
              };
            }
          }
        "
      />
    </v-row>
  </div>
  <div v-else>
    <v-row class="pt-10 pb-3">
      <Select
        v-model:model-value="customRelationshipType"
        label="Add Relationship To...*"
        density="compact"
        variant="outlined"
        :items="customRelationshipTypeItems"
      />
    </v-row>

    <CollectionReferenceInput
      v-if="
        relationshipType === RelationshipType.CUSTOM &&
        customRelationshipType === CustomRelationshipType.COLLECTION
      "
      v-model="collectionReference"
    />

    <DataobjectReferenceInput
      v-if="
        relationshipType === RelationshipType.CUSTOM &&
        customRelationshipType === CustomRelationshipType.DATA_OBJECT
      "
      v-model="dataobjectReference"
    />

    <UriReferenceInput
      v-if="
        relationshipType === RelationshipType.CUSTOM &&
        customRelationshipType === CustomRelationshipType.URI
      "
      v-model="uriReference"
      :uri-placeholder="uriPlaceholder"
    />

    <v-row class="pt-8">
      <MandatoryFieldHint />
    </v-row>
  </div>
</template>
