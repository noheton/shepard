<template>
  <div v-if="collectionId">
    <b-breadcrumb class="breadcrumb">
      <b-breadcrumb-item to="/explore"> Collections </b-breadcrumb-item>

      <b-breadcrumb-item
        v-if="dataObjectId"
        :to="{
          name: 'Collection',
          params: {
            collectionId: collectionId,
          },
        }"
      >
        Collection
      </b-breadcrumb-item>
      <b-breadcrumb-item v-else active> Collection </b-breadcrumb-item>

      <b-breadcrumb-item
        v-if="parentId"
        :to="{
          name: 'DataObject',
          params: {
            collectionId: collectionId,
            dataObjectId: parentId,
          },
        }"
      >
        Parent
      </b-breadcrumb-item>

      <b-breadcrumb-item v-if="dataObjectId" active>
        Data Object
      </b-breadcrumb-item>
    </b-breadcrumb>
    <hr />
  </div>
</template>

<script lang="ts">
import { DataObjectVue } from "@/utils/api-mixin";
import Vue, { VueConstructor } from "vue";

interface BreadcrumbData {
  parentId?: number;
}

export default (
  Vue as VueConstructor<Vue & InstanceType<typeof DataObjectVue>>
).extend({
  mixins: [DataObjectVue],
  data() {
    return {
      parentId: undefined,
    } as BreadcrumbData;
  },
  computed: {
    collectionId(): string {
      return this.$route.params.collectionId;
    },
    dataObjectId(): string {
      return this.$route.params.dataObjectId;
    },
  },
  watch: {
    dataObjectId() {
      if (this.dataObjectId) this.retrieveDataObject();
      else this.parentId = undefined;
    },
  },
  methods: {
    retrieveDataObject() {
      this.dataObjectApi
        ?.getDataObject({
          collectionId: +this.collectionId,
          dataObjectId: +this.dataObjectId,
        })
        .then(response => {
          if (response.parentId) this.parentId = response.parentId;
          else this.parentId = undefined;
        })
        .catch(e => {
          this.parentId = undefined;
          const error = "Error while fetching data objects: " + e.statusText;
          console.log(error);
        });
    },
  },
});
</script>

<style scoped>
.breadcrumb {
  font-style: italic;
  margin-top: 30px;
  margin-bottom: 0px;
  padding: 0px;
  background-color: #fff;
}

hr {
  margin-top: 4px;
  border-top: 1px solid #343a40;
}
</style>
