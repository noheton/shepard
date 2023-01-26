<script setup lang="ts">
import CreatedByLine from "@/components/generic/CreatedByLine.vue";
import GenericName from "@/components/generic/GenericName.vue";
import Loading from "@/components/generic/Loading.vue";
import type {
  Collection,
  FileContainer,
  SemanticRepository,
  StructuredDataContainer,
  TimeseriesContainer,
} from "@dlr-shepard/shepard-client";
import type { PropType } from "vue";

const props = defineProps({
  entities: {
    type: Array as PropType<
      Array<
        | Collection
        | FileContainer
        | StructuredDataContainer
        | TimeseriesContainer
        | SemanticRepository
      >
    >,
    default: undefined,
  },
});
</script>

<template>
  <div>
    <div v-if="props.entities == undefined"><Loading /></div>
    <div v-else>
      <b-list-group class="mb-2">
        <b-list-group-item
          v-for="(entity, index) in props.entities"
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
