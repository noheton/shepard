<script setup lang="ts">
import CreatedByLine from "@/components/generic/CreatedByLine.vue";
import GenericName from "@/components/generic/GenericName.vue";
import type { DataObject } from "@dlr-shepard/shepard-client";
import type { PropType } from "vue";

defineProps({
  dataObject: {
    type: Object as PropType<DataObject>,
    required: true,
  },
});
</script>

<template>
  <b-list-group-item
    :to="{
      name: 'DataObject',
      params: {
        collectionId: dataObject.collectionId,
        dataObjectId: dataObject.id,
      },
    }"
    append
  >
    <div class="float-left">
      <div>
        <b><GenericName :name="dataObject.name || ''" :word-count="40" /></b>
        ID:
        {{ dataObject.id }}
      </div>
      <CreatedByLine
        :created-by="dataObject.createdBy"
        :created-at="dataObject.createdAt"
      />
    </div>
    <div v-if="dataObject.referenceIds" class="icon">
      <ReferencesIcon />
      {{ dataObject.referenceIds.length }}
    </div>
    <div v-if="dataObject.successorIds" class="icon">
      <SuccessorIcon />
      {{ dataObject.successorIds.length }}
    </div>
    <div v-if="dataObject.predecessorIds" class="icon">
      <PredecessorIcon />
      {{ dataObject.predecessorIds.length }}
    </div>
    <div v-if="dataObject.childrenIds" class="icon">
      <ChildIcon />
      {{ dataObject.childrenIds.length }}
    </div>
    <div class="icon">
      <ParentIcon />
      <span v-if="dataObject.parentId"> 1 </span>
      <span v-else> 0 </span>
    </div>
  </b-list-group-item>
</template>

<style scoped>
.icon {
  margin-left: 10px;
  margin-right: 10px;
  margin-top: 10px;
  float: right;
}
</style>
