<script setup lang="ts">
import type { BasicEntity } from "@dlr-shepard/backend-client";
import { collectionsPath } from "../../utils/constants";
import CollectionListItemContent from "./CollectionListItemContent.vue";

const router = useRouter();

const props = defineProps<{
  maxObjects: number;
  page: number;
  collections: BasicEntity[];
}>();

const page = ref(props.page);
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
    </template>
  </v-data-iterator>
</template>
