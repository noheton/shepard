<template>
  <div>
    <div v-if="entities == undefined"><Loading /></div>
    <div v-else>
      <b-list-group class="mb-2">
        <b-list-group-item
          v-for="(entity, index) in entities"
          :key="index"
          :to="String(entity.id)"
          append
        >
          <b><GenericName :name="entity.name || ''" :word-count="60" /></b>
          ID: {{ entity.id }}
          <CreatedByLine
            :created-by="entity.createdBy"
            :created-at="entity.createdAt"
          />
        </b-list-group-item>
      </b-list-group>
    </div>
  </div>
</template>

<script lang="ts">
import CreatedByLine from "@/components/generic/CreatedByLine.vue";
import GenericName from "@/components/generic/GenericName.vue";
import Loading from "@/components/generic/Loading.vue";
import type {
  Collection,
  FileContainer,
  StructuredDataContainer,
  TimeseriesContainer,
} from "@dlr-shepard/shepard-client";
import { defineComponent, type PropType } from "vue";

export default defineComponent({
  components: {
    CreatedByLine,
    GenericName,
    Loading,
  },
  props: {
    entities: {
      type: Object as PropType<
        Array<
          | Collection
          | FileContainer
          | StructuredDataContainer
          | TimeseriesContainer
        >
      >,
      default: undefined,
    },
  },
  data() {
    return {
      loaded: false,
    };
  },
});
</script>

<style scoped>
.fixed-height {
  height: 40px;
}
.list-group-item a {
  color: #495057;
  float: left;
}
.list-group-item .btn-group {
  float: right;
}
</style>
