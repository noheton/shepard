<template>
  <div v-if="collectionId">
    <b-breadcrumb class="breadcrumb" :items="items"></b-breadcrumb>
    <hr />
  </div>
</template>

<script lang="ts">
import { DataObjectVue } from "@/utils/api-mixin";
import Vue, { VueConstructor } from "vue";
import { Location } from "vue-router";

interface Breadcrumb {
  text: string;
  to?: Location;
  active: boolean;
}

function getCollectionsBreadcrumb(active: boolean): Breadcrumb {
  return {
    text: "Collections",
    active: active,
    to: {
      name: "Explore",
    },
  };
}

function getCollectionBreadcrumb(
  active: boolean,
  collectionId: string,
): Breadcrumb {
  return {
    text: "Collection",
    active: active,
    to: {
      name: "Collection",
      params: {
        collectionId: collectionId,
      },
    },
  };
}

function getDataObjectBreadcrumb(
  active: boolean,
  collectionId: string,
  dataObjectId: string,
  isParent: boolean,
): Breadcrumb {
  return {
    text: isParent ? "Parent" : "DataObject",
    active: active,
    to: {
      name: "DataObject",
      params: {
        collectionId: collectionId,
        dataObjectId: dataObjectId,
      },
    },
  };
}

interface BreadcrumbData {
  parentId?: number;
  items: Array<Breadcrumb>;
}

export default (
  Vue as VueConstructor<Vue & InstanceType<typeof DataObjectVue>>
).extend({
  mixins: [DataObjectVue],
  data() {
    return {
      parentId: undefined,
      items: [],
    } as BreadcrumbData;
  },
  computed: {
    collectionId(): string {
      return this.$route.params.collectionId;
    },
    dataObjectId(): string {
      return this.$route.params.dataObjectId;
    },
    routeName(): string {
      return this.$route.name || "";
    },
  },
  watch: {
    dataObjectId() {
      if (this.dataObjectId) this.retrieveDataObject();
      else this.parentId = undefined;
    },
    routeName() {
      this.chooseBreadcrumb();
    },
    parentId() {
      this.chooseBreadcrumb();
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
    chooseBreadcrumb() {
      this.items = [];
      if (this.routeName === "Explore") {
        this.items.push(getCollectionsBreadcrumb(true));
      } else if (this.routeName === "Collection") {
        this.items.push(getCollectionsBreadcrumb(false));
        this.items.push(getCollectionBreadcrumb(true, this.collectionId));
      } else if (this.routeName === "DataObject") {
        this.items.push(getCollectionsBreadcrumb(false));
        this.items.push(getCollectionBreadcrumb(false, this.collectionId));
        if (this.parentId) {
          this.items.push(
            getDataObjectBreadcrumb(
              false,
              this.collectionId,
              String(this.parentId),
              true,
            ),
          );
        }
        this.items.push(
          getDataObjectBreadcrumb(
            true,
            this.collectionId,
            this.dataObjectId,
            false,
          ),
        );
      }
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
