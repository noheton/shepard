<script setup lang="ts">
import type { Collection } from "@dlr-shepard/backend-client";
import CollectionListItemContent from "./CollectionListItemContent.vue";

const router = useRouter();

const props = defineProps<{
  maxObjects: number;
  page: number;
  collections: Collection[];
}>();

const page = ref(props.page);
const paginationLength =
  Math.floor(props.collections.length / props.maxObjects) + 1;
</script>

<template>
  <v-data-iterator
    :items="$props.collections"
    :items-per-page="$props.maxObjects"
    :page="page"
  >
    <template #default="{ items }">
      <v-card
        v-for="(item, i) in items"
        :key="i"
        :style="{ padding: '5px' }"
        @click="router.push(collectionsPath + item.raw.id)"
      >
        <CollectionListItemContent :collection="item.raw" />
      </v-card>
      <v-pagination v-model="page" :length="paginationLength" />
    </template>
  </v-data-iterator>
</template>
