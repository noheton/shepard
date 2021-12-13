<template>
  <b-list-group class="mb-2">
    <b-list-group-item
      v-for="(entity, index) in entities"
      :key="index"
      :to="String(entity.id)"
      append
    >
      <b>{{ entity.name }}</b> ID: {{ entity.id }}
      <CreatedByLine
        :created-by="entity.createdBy"
        :created-at="entity.createdAt"
      />
    </b-list-group-item>
  </b-list-group>
</template>

<script lang="ts">
import CreatedByLine from "@/components/generic/CreatedByLine.vue";
import Vue from "vue";

export default Vue.extend({
  components: {
    CreatedByLine,
  },
  props: {
    entities: {
      type: Array,
      default: function () {
        return [];
      },
    },
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
