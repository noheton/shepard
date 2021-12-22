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
        <b><GenericName :name="dataObject.name" :word-count="40" /></b> ID:
        {{ dataObject.id }}
      </div>
      <CreatedByLine
        :created-by="dataObject.createdBy"
        :created-at="dataObject.createdAt"
      />
    </div>
    <div class="icon">
      <ReferencesIcon title="References" />
      {{ dataObject.referenceIds.length }}
    </div>
    <div class="icon">
      <SuccessorIcon title="Successors" />
      {{ dataObject.successorIds.length }}
    </div>
    <div class="icon">
      <PredecessorIcon title="Predecessors" />
      {{ dataObject.predecessorIds.length }}
    </div>
    <div class="icon">
      <ChildIcon title="Children" />
      {{ dataObject.childrenIds.length }}
    </div>
    <div class="icon">
      <ParentIcon title="Parents" />
      <span v-if="dataObject.parentId"> 1 </span>
      <span v-else> 0 </span>
    </div>
  </b-list-group-item>
</template>

<script lang="ts">
import CreatedByLine from "@/components/generic/CreatedByLine.vue";
import GenericName from "@/components/generic/GenericName.vue";
import { DataObject } from "@dlr-shepard/shepard-client";
import Vue, { PropType } from "vue";

export default Vue.extend({
  components: { CreatedByLine, GenericName },
  props: {
    dataObject: {
      type: Object as PropType<DataObject>,
      required: true,
    },
  },
});
</script>

<style scoped>
.icon {
  margin-left: 10px;
  margin-right: 10px;
  margin-top: 10px;
  float: right;
}
</style>
