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
          <b><GenericName :name="entity.name" :word-count="60" /></b>
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
import Vue from "vue";

export default Vue.extend({
  components: {
    CreatedByLine,
    GenericName,
    Loading,
  },
  props: {
    entities: {
      type: Array,
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
